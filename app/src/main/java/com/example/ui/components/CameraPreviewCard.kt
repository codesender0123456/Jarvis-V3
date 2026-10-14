package com.example.ui.components

import androidx.camera.view.PreviewView
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.viewinterop.AndroidView
import com.example.core.vision.VisionController

/**
 * The camera, inside the app (no camera app is opened): live preview, a flip button, and one-tap
 * "read text" (OCR) and "translate" that ask the assistant about what the camera currently sees.
 */
@Composable
fun CameraPreviewCard(
    vision: VisionController,
    translateTarget: String,
    onAsk: (String) -> Unit,
    modifier: Modifier = Modifier
) {
    val front by vision.cameraFront.collectAsState()

    DisposableEffect(Unit) {
        onDispose { vision.detachPreview() }
    }

    Column(
        modifier = modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(12.dp))
            .background(Color(0xFF0F1728))
    ) {
        AndroidView(
            factory = { ctx ->
                PreviewView(ctx).apply {
                    implementationMode = PreviewView.ImplementationMode.COMPATIBLE
                    scaleType = PreviewView.ScaleType.FILL_CENTER
                }
            },
            update = { view -> vision.attachPreview(view.surfaceProvider) },
            modifier = Modifier.fillMaxWidth().height(190.dp)
        )
        Row(
            modifier = Modifier.fillMaxWidth().padding(horizontal = 4.dp),
            horizontalArrangement = Arrangement.SpaceBetween
        ) {
            TextButton(onClick = { vision.flipCamera() }) {
                Text(if (front) "FLIP > REAR" else "FLIP > FRONT", fontSize = 11.sp, fontFamily = FontFamily.Monospace)
            }
            TextButton(onClick = { onAsk("Read all the text you can see through the camera, exactly as written.") }) {
                Text("READ TEXT", fontSize = 11.sp, fontFamily = FontFamily.Monospace)
            }
            TextButton(onClick = { onAsk("Translate the text you can see through the camera into $translateTarget and tell me what it says.") }) {
                Text("TRANSLATE", fontSize = 11.sp, fontFamily = FontFamily.Monospace)
            }
            TextButton(onClick = { vision.stopAll() }) {
                Text("CLOSE", fontSize = 11.sp, fontFamily = FontFamily.Monospace, color = MaterialTheme.colorScheme.error)
            }
        }
    }
}
