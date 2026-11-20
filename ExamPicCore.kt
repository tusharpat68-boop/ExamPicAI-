package com.exampic.ai.core

import android.graphics.Bitmap
import android.graphics.RectF
import com.google.mlkit.vision.common.InputImage
import com.google.mlkit.vision.text.TextRecognition
import com.google.mlkit.vision.text.devanagari.DevanagariTextRecognizerOptions
import com.google.mlkit.vision.text.latin.TextRecognizerOptions
import kotlinx.coroutines.tasks.await

// ---------- Document model (all positions normalized 0..1 so they survive any page size) ----------

enum class Align { LEFT, CENTER, RIGHT }
enum class Role { SCHOOL, EXAM, META, SECTION, QUESTION, OPTION, INSTRUCTION, BODY, MATH, TABLE, IMAGE }

data class TextElement(
    val id: String,
    val text: String,
    val box: RectF,                 // normalized page coordinates
    val fontSizeSp: Float,
    val bold: Boolean = false,
    val italic: Boolean = false,
    val underline: Boolean = false,
    val align: Align = Align.LEFT,
    val role: Role = Role.BODY,
    val confidence: Float = 1f,     // < LOW_CONFIDENCE => highlight for manual review
    val latex: String? = null       // set for MATH elements
) {
    val needsReview get() = confidence < LOW_CONFIDENCE
    companion object { const val LOW_CONFIDENCE = 0.80f }
}

data class Page(val index: Int, val sourceImage: String, val elements: List<TextElement>)
data class Paper(val name: String, val pages: List<Page>)

// ---------- OCR -> layout-preserving elements ----------

class LayoutOcr {
    private val latin = TextRecognition.getClient(TextRecognizerOptions.DEFAULT_OPTIONS)
    private val devanagari = TextRecognition.getClient(DevanagariTextRecognizerOptions.Builder().build())
    // NOTE: ML Kit on-device does not cover Bangla/Arabic/Urdu. Plug a second engine
    // (e.g. Tesseract ben/ara/urd traineddata, or a cloud OCR) behind the same interface
    // and merge results per line by highest confidence.

    suspend fun recognize(bitmap: Bitmap, pageIndex: Int, src: String): Page {
        val img = InputImage.fromBitmap(bitmap, 0)
        val w = bitmap.width.toFloat()
        val h = bitmap.height.toFloat()
        val result = latin.process(img).await()

        val elements = mutableListOf<TextElement>()
        var n = 0
        for (block in result.textBlocks) for (line in block.lines) {
            val r = line.boundingBox ?: continue
            val box = RectF(r.left / w, r.top / h, r.right / w, r.bottom / h)
            val conf = line.confidence.takeIf { it > 0f } ?: 1f
            val text = line.text
            elements += TextElement(
                id = "p${pageIndex}_${n++}",
                text = text,
                box = box,
                fontSizeSp = (r.height() / h) * 800f,        // calibrate against page height
                align = inferAlign(box),
                role = inferRole(text, box),
                confidence = conf
            )
        }
        return Page(pageIndex, src, elements.sortedWith(compareBy({ it.box.top }, { it.box.left })))
    }

    private fun inferAlign(b: RectF): Align {
        val cx = (b.left + b.right) / 2f
        return when {
            kotlin.math.abs(cx - 0.5f) < 0.06f && b.left > 0.15f -> Align.CENTER
            b.left > 0.55f -> Align.RIGHT
            else -> Align.LEFT
        }
    }

    // Question Paper Mode heuristics: Latin + Bengali digits, "ক)" style options, marks, etc.
    private val qNum = Regex("""^\s*([0-9০-৯]+|[IVX]+)\s*[.)।]""")
    private val option = Regex("""^\s*(\(?[a-dA-D]\)|[ক-ঘ]\)|\(?[ক-ঘ]\))""")
    private val meta = Regex("""(?i)(full\s*marks|time|date|class|subject|পূর্ণমান|সময়|শ্রেণি|বিষয়)""")

    private fun inferRole(t: String, b: RectF) = when {
        option.containsMatchIn(t) -> Role.OPTION
        qNum.containsMatchIn(t) -> Role.QUESTION
        meta.containsMatchIn(t) -> Role.META
        b.top < 0.08f && inferAlign(b) == Align.CENTER -> Role.SCHOOL
        else -> Role.BODY
    }
}

// ---------- Editing with Layout Lock + Undo/Redo ----------

class PageEditor(initial: Page) {
    var layoutLock = true
    private val undo = ArrayDeque<Page>()
    private val redo = ArrayDeque<Page>()
    var page: Page = initial
        private set

    private fun commit(new: Page) { undo.addLast(page); redo.clear(); page = new }

    /** Text edit: ONLY text changes. Box, size, alignment are never touched. */
    fun editText(id: String, newText: String) = commit(page.copy(elements = page.elements.map {
        if (it.id == id) it.copy(text = newText, confidence = 1f) else it
    }))

    /** Move/resize is rejected while Layout Lock is ON. */
    fun moveOrResize(id: String, newBox: RectF): Boolean {
        if (layoutLock) return false
        commit(page.copy(elements = page.elements.map { if (it.id == id) it.copy(box = newBox) else it }))
        return true
    }

    fun setStyle(id: String, f: (TextElement) -> TextElement) =
        commit(page.copy(elements = page.elements.map { if (it.id == id) f(it) else it }))

    fun addText(e: TextElement) = commit(page.copy(elements = page.elements + e))
    fun delete(id: String) = commit(page.copy(elements = page.elements.filterNot { it.id == id }))

    fun undo() { if (undo.isNotEmpty()) { redo.addLast(page); page = undo.removeLast() } }
    fun redo() { if (redo.isNotEmpty()) { undo.addLast(page); page = redo.removeLast() } }
}
