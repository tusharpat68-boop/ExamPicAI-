package com.exampic.ai.export

import android.content.ContentValues
import android.content.Context
import android.graphics.Bitmap
import android.graphics.pdf.PdfDocument
import android.net.Uri
import android.os.Environment
import android.provider.MediaStore

/** Saves into Downloads/ExamPic AI/ via MediaStore (no storage permission needed on API 29+). */
object Exporter {
    private fun open(ctx: Context, name: String, mime: String): Pair<Uri, java.io.OutputStream> {
        val v = ContentValues().apply {
            put(MediaStore.Downloads.DISPLAY_NAME, name)
            put(MediaStore.Downloads.MIME_TYPE, mime)
            put(MediaStore.Downloads.RELATIVE_PATH, Environment.DIRECTORY_DOWNLOADS + "/ExamPic AI")
        }
        val uri = ctx.contentResolver.insert(MediaStore.Downloads.EXTERNAL_CONTENT_URI, v)!!
        return uri to ctx.contentResolver.openOutputStream(uri)!!
    }

    fun savePng(ctx: Context, page: Bitmap, name: String) = open(ctx, "$name.png", "image/png").let { (u, s) ->
        s.use { page.compress(Bitmap.CompressFormat.PNG, 100, it) }; u }

    fun saveJpg(ctx: Context, page: Bitmap, name: String) = open(ctx, "$name.jpg", "image/jpeg").let { (u, s) ->
        s.use { page.compress(Bitmap.CompressFormat.JPEG, 95, it) }; u }

    /** pages = rendered page bitmaps in order. Vector text PDF: draw elements on page.canvas instead. */
    fun savePdf(ctx: Context, pages: List<Bitmap>, name: String): Uri {
        val doc = PdfDocument()
        pages.forEachIndexed { i, bmp ->
            val p = doc.startPage(PdfDocument.PageInfo.Builder(bmp.width, bmp.height, i + 1).create())
            p.canvas.drawBitmap(bmp, 0f, 0f, null); doc.finishPage(p)
        }
        val (u, s) = open(ctx, "$name.pdf", "application/pdf")
        s.use { doc.writeTo(it) }; doc.close(); return u
    }
    // DOCX: add Apache POI (or write the OOXML zip by hand) with absolutely positioned text frames.
}
