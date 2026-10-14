package com.example.ui.components

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalClipboardManager
import androidx.compose.ui.text.AnnotatedString
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.core.gemini.ErrorGuidance
import com.example.core.gemini.GuidanceAction

/** Explains what went wrong and how to fix it, with a button that does the first fix step. */
@Composable
fun ErrorGuidanceDialog(
    guidance: ErrorGuidance,
    onAction: (GuidanceAction) -> Unit,
    onEnterKey: () -> Unit,
    onRetry: () -> Unit,
    onDismiss: () -> Unit
) {
    val clipboard = LocalClipboardManager.current

    AlertDialog(
        onDismissRequest = onDismiss,
        title = {
            Text(
                text = guidance.title,
                fontSize = 16.sp,
                fontWeight = FontWeight.Bold,
                color = MaterialTheme.colorScheme.error
            )
        },
        text = {
            Column(modifier = Modifier.heightIn(max = 440.dp).verticalScroll(rememberScrollState())) {
                Text(guidance.explanation, fontSize = 13.sp, color = MaterialTheme.colorScheme.onSurface)
                Spacer(modifier = Modifier.height(10.dp))
                Text("How to fix it", fontSize = 12.sp, fontWeight = FontWeight.Bold, color = MaterialTheme.colorScheme.primary)
                guidance.steps.forEachIndexed { i, step ->
                    Row(modifier = Modifier.padding(top = 4.dp)) {
                        Text("${i + 1}.", fontSize = 12.sp, fontFamily = FontFamily.Monospace,
                            color = MaterialTheme.colorScheme.primary, modifier = Modifier.padding(end = 6.dp))
                        Text(step, fontSize = 12.sp, color = MaterialTheme.colorScheme.onSurface)
                    }
                }
                if (guidance.technical.isNotBlank()) {
                    Spacer(modifier = Modifier.height(12.dp))
                    Column(
                        modifier = Modifier.fillMaxWidth().clip(RoundedCornerShape(8.dp))
                            .background(Color(0xFF0F1728)).padding(10.dp)
                    ) {
                        Text("Technical details", fontSize = 10.sp, color = MaterialTheme.colorScheme.onSurfaceVariant)
                        Text(guidance.technical, fontSize = 11.sp, fontFamily = FontFamily.Monospace,
                            color = MaterialTheme.colorScheme.onSurface)
                        TextButton(onClick = { clipboard.setText(AnnotatedString(guidance.technical)) }) {
                            Text("COPY DETAILS", fontSize = 11.sp, fontFamily = FontFamily.Monospace)
                        }
                    }
                }
            }
        },
        confirmButton = {
            Row(horizontalArrangement = Arrangement.End) {
                if (guidance.action != GuidanceAction.NONE && guidance.actionLabel != null) {
                    TextButton(onClick = { onAction(guidance.action) }) {
                        Text(guidance.actionLabel, fontSize = 12.sp, fontFamily = FontFamily.Monospace)
                    }
                }
                if (guidance.action == GuidanceAction.OPEN_AI_STUDIO) {
                    TextButton(onClick = onEnterKey) { Text("ENTER KEY", fontSize = 12.sp, fontFamily = FontFamily.Monospace) }
                }
                if (guidance.canRetry) {
                    TextButton(onClick = onRetry) { Text("TRY AGAIN", fontSize = 12.sp, fontFamily = FontFamily.Monospace) }
                }
            }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) { Text("DISMISS", fontSize = 12.sp, fontFamily = FontFamily.Monospace) }
        }
    )
}
