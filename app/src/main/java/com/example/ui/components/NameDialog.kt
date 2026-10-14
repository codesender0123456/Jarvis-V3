package com.example.ui.components

import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp

/** Asked once, after permissions and the API key: what should JARVIS call you? */
@Composable
fun NameDialog(onSubmit: (String) -> Unit) {
    var name by rememberSaveable { mutableStateOf("") }
    AlertDialog(
        onDismissRequest = { /* needed once; JARVIS greets you by name */ },
        title = { Text("WHAT SHOULD I CALL YOU?", fontSize = 16.sp, fontFamily = FontFamily.Monospace) },
        text = {
            Column {
                Text(
                    "JARVIS will greet you by this name every time you connect. You can change it later in Settings.",
                    fontSize = 13.sp,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
                Spacer(modifier = Modifier.height(12.dp))
                OutlinedTextField(
                    value = name,
                    onValueChange = { name = it.take(30) },
                    modifier = Modifier.fillMaxWidth(),
                    singleLine = true,
                    label = { Text("Your name", fontSize = 12.sp) },
                    keyboardOptions = KeyboardOptions(imeAction = ImeAction.Done)
                )
            }
        },
        confirmButton = {
            Button(onClick = { onSubmit(name.trim()) }, enabled = name.isNotBlank()) {
                Text("CONTINUE", fontSize = 13.sp, fontFamily = FontFamily.Monospace)
            }
        }
    )
}
