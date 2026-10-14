package com.example.ui.components

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalClipboardManager
import androidx.compose.ui.platform.LocalUriHandler
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp

private const val API_KEY_PAGE = "https://aistudio.google.com/apikey"

private val KEY_STEPS = listOf(
    "Tap \"GET A FREE API KEY\" below. Google AI Studio opens in your browser; sign in with your Google account.",
    "Tap \"Create API key\". Pick an existing project, or choose \"Create API key in new project\".",
    "Tap the copy icon next to the key (it starts with \"AIza\").",
    "Come back to JARVIS. Tap PASTE (or paste into the field) and tap SAVE & PROCEED.",
    "Allow the permissions JARVIS asks for (microphone first), then tap Proceed. You are ready to talk."
)

/**
 * Blocking prompt for the Gemini API key, with a link to create a free key and the steps to do it.
 *
 * Shown on first launch once permissions are granted, and again on any later launch where no
 * key is stored, because every AI feature in the app is dead without one.
 */
@Composable
fun ApiKeyDialog(
    isFirstRun: Boolean,
    isSaving: Boolean,
    onSubmit: (String) -> Unit,
    onSkip: () -> Unit
) {
    var key by rememberSaveable { mutableStateOf("") }
    val trimmed = key.trim()
    val canSubmit = trimmed.isNotEmpty() && !isSaving
    val uriHandler = LocalUriHandler.current
    val clipboard = LocalClipboardManager.current

    AlertDialog(
        onDismissRequest = { /* Deliberately modal: the app cannot work without a key. */ },
        title = {
            Text(
                text = if (isFirstRun) "AUTHORIZE NEURAL CORE" else "API KEY REQUIRED",
                fontSize = 16.sp,
                fontFamily = FontFamily.Monospace
            )
        },
        text = {
            Column(
                modifier = Modifier
                    .heightIn(max = 460.dp)
                    .verticalScroll(rememberScrollState())
            ) {
                Text(
                    text = "JARVIS needs your own Gemini API key. It is free to create, stored only on this " +
                        "device (encrypted) and sent only to Google.",
                    fontSize = 13.sp,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )

                Spacer(modifier = Modifier.height(12.dp))

                OutlinedTextField(
                    value = key,
                    onValueChange = { key = it },
                    modifier = Modifier.fillMaxWidth(),
                    singleLine = true,
                    enabled = !isSaving,
                    label = { Text("Gemini API Key", fontSize = 12.sp) },
                    visualTransformation = PasswordVisualTransformation(),
                    keyboardOptions = KeyboardOptions(imeAction = ImeAction.Done),
                    textStyle = TextStyle(fontFamily = FontFamily.Monospace)
                )

                Row(
                    modifier = Modifier.fillMaxWidth().padding(top = 4.dp),
                    horizontalArrangement = Arrangement.End
                ) {
                    TextButton(
                        onClick = { clipboard.getText()?.text?.let { key = it.trim() } },
                        enabled = !isSaving
                    ) { Text("PASTE", fontSize = 12.sp, fontFamily = FontFamily.Monospace) }
                }

                Spacer(modifier = Modifier.height(8.dp))

                // No key yet? Link + step-by-step.
                Column(
                    modifier = Modifier
                        .fillMaxWidth()
                        .clip(RoundedCornerShape(10.dp))
                        .background(Color(0xFF0F1728))
                        .padding(12.dp)
                ) {
                    Text(
                        text = "Don't have a key?",
                        fontSize = 13.sp,
                        fontWeight = FontWeight.Bold,
                        fontFamily = FontFamily.Monospace,
                        color = MaterialTheme.colorScheme.primary
                    )
                    Spacer(modifier = Modifier.height(6.dp))
                    KEY_STEPS.forEachIndexed { i, step ->
                        Row(modifier = Modifier.padding(vertical = 2.dp)) {
                            Text(
                                text = "${i + 1}.",
                                fontSize = 12.sp,
                                fontFamily = FontFamily.Monospace,
                                color = MaterialTheme.colorScheme.primary,
                                modifier = Modifier.padding(end = 6.dp)
                            )
                            Text(
                                text = step,
                                fontSize = 12.sp,
                                color = MaterialTheme.colorScheme.onSurface
                            )
                        }
                    }
                    Spacer(modifier = Modifier.height(10.dp))
                    OutlinedButton(
                        onClick = { runCatching { uriHandler.openUri(API_KEY_PAGE) } },
                        modifier = Modifier.fillMaxWidth(),
                        shape = RoundedCornerShape(8.dp)
                    ) {
                        Text("GET A FREE API KEY", fontSize = 13.sp, fontFamily = FontFamily.Monospace)
                    }
                    Text(
                        text = "aistudio.google.com/apikey",
                        fontSize = 10.sp,
                        fontFamily = FontFamily.Monospace,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        modifier = Modifier.padding(top = 4.dp)
                    )
                }

                if (isSaving) {
                    Text(
                        text = "Securing key...",
                        fontSize = 11.sp,
                        fontFamily = FontFamily.Monospace,
                        color = MaterialTheme.colorScheme.primary,
                        modifier = Modifier.padding(top = 8.dp)
                    )
                }
            }
        },
        confirmButton = {
            Button(
                onClick = { onSubmit(trimmed) },
                enabled = canSubmit,
                shape = RoundedCornerShape(8.dp),
                colors = ButtonDefaults.buttonColors(
                    containerColor = MaterialTheme.colorScheme.primary,
                    contentColor = Color(0xFF001F26)
                )
            ) {
                Text("SAVE & PROCEED", fontSize = 13.sp, fontFamily = FontFamily.Monospace)
            }
        },
        dismissButton = {
            if (!isFirstRun) {
                TextButton(onClick = onSkip) {
                    Text("Later", fontSize = 13.sp)
                }
            }
        }
    )
}
