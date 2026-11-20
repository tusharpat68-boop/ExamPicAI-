package com.exampic.ai

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.compose.foundation.layout.*
import androidx.compose.material3.*
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp

class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContent { MaterialTheme { Home() } }
    }
}

// TODO: wire each button (CameraX, Photo Picker, blank paper, Room list) and the editor screen.
@Composable
fun Home() {
    Column(
        Modifier.fillMaxSize().padding(24.dp),
        verticalArrangement = Arrangement.spacedBy(16.dp, Alignment.CenterVertically),
        horizontalAlignment = Alignment.CenterHorizontally
    ) {
        Text("ExamPic AI", style = MaterialTheme.typography.headlineLarge)
        listOf("📷 Scan with Camera", "🖼️ Select from Gallery",
               "📄 Create New Question Paper", "📁 Saved Papers").forEach {
            Button(onClick = { }, modifier = Modifier.fillMaxWidth()) { Text(it) }
        }
    }
}
