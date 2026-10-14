package com.example.ui.screens

import android.content.Intent
import android.net.Uri
import android.os.Build
import android.os.SystemClock
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Button
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.core.support.InboxMessage
import com.example.core.support.SendResult
import com.example.core.support.SupportClient
import com.example.core.support.SupportConfig
import com.example.core.support.SupportRules
import kotlinx.coroutines.launch

/** Contact the developer, and read messages the developer has sent to users. */
@Composable
fun ContactScreen(client: SupportClient, onBack: () -> Unit) {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()

    var email by remember { mutableStateOf(client.savedEmail) }
    var problem by remember { mutableStateOf("") }
    var status by remember { mutableStateOf("") }
    var sending by remember { mutableStateOf(false) }
    var inbox by remember { mutableStateOf<List<InboxMessage>?>(emptyList()) }
    var loadingInbox by remember { mutableStateOf(false) }

    fun refreshInbox() {
        if (!SupportRules.isValidEmail(email)) return
        scope.launch {
            loadingInbox = true
            inbox = client.fetchInbox(email)
            inbox?.let { client.markRead(it) }
            loadingInbox = false
        }
    }
    LaunchedEffect(Unit) { refreshInbox() }

    Column(
        modifier = Modifier.fillMaxSize().padding(16.dp).verticalScroll(rememberScrollState())
    ) {
        Row(verticalAlignment = androidx.compose.ui.Alignment.CenterVertically) {
            TextButton(onClick = onBack) { Text("< BACK", fontFamily = FontFamily.Monospace) }
            Text("CONTACT US", fontSize = 18.sp, fontWeight = FontWeight.Bold, color = MaterialTheme.colorScheme.primary)
        }
        Spacer(modifier = Modifier.height(12.dp))

        OutlinedTextField(
            value = email, onValueChange = { email = it.take(120) }, modifier = Modifier.fillMaxWidth(),
            singleLine = true, label = { Text("Your email address") }
        )
        Spacer(modifier = Modifier.height(8.dp))
        OutlinedTextField(
            value = problem, onValueChange = { problem = it.take(2000) },
            modifier = Modifier.fillMaxWidth().height(150.dp),
            label = { Text("Describe your problem or question") }
        )
        Spacer(modifier = Modifier.height(8.dp))

        val canSend = SupportRules.isValidEmail(email) && SupportRules.isValidProblem(problem) && !sending
        Button(
            enabled = canSend,
            onClick = {
                val wait = SupportRules.SEND_COOLDOWN_MS - (System.currentTimeMillis() - client.lastSentAt)
                if (wait > 0) { status = "Please wait ${wait / 1000 + 1} seconds before sending another message."; return@Button }
                client.savedEmail = email
                val info = "App 1.0, Android ${Build.VERSION.RELEASE}, ${Build.MANUFACTURER} ${Build.MODEL}"
                scope.launch {
                    sending = true; status = "Sending..."
                    when (val r = client.send(email.trim(), problem.trim(), info)) {
                        SendResult.Sent -> { client.lastSentAt = System.currentTimeMillis(); problem = ""; status = "Sent. Thank you, I will reply by email." }
                        SendResult.NotConfigured -> {
                            if (SupportConfig.SUPPORT_EMAIL.isNotBlank()) {
                                val mail = Intent(Intent.ACTION_SENDTO, Uri.parse("mailto:")).apply {
                                    putExtra(Intent.EXTRA_EMAIL, arrayOf(SupportConfig.SUPPORT_EMAIL))
                                    putExtra(Intent.EXTRA_SUBJECT, "JARVIS Mobile support")
                                    putExtra(Intent.EXTRA_TEXT, "${problem.trim()}\n\nReply to: ${email.trim()}\n$info")
                                }
                                status = if (runCatching { context.startActivity(mail) }.isSuccess) "Your email app opened: press send there."
                                else "No email app found on this phone."
                            } else {
                                status = "Contact is not set up yet: the developer has not added a support email."
                            }
                        }
                        is SendResult.Failed -> status = r.reason + " Please try again."
                    }
                    sending = false
                }
            },
            modifier = Modifier.fillMaxWidth()
        ) { Text(if (sending) "SENDING..." else "SEND", fontFamily = FontFamily.Monospace) }

        if (status.isNotBlank()) {
            Text(status, fontSize = 12.sp, color = MaterialTheme.colorScheme.primary, modifier = Modifier.padding(top = 8.dp))
        }

        Spacer(modifier = Modifier.height(20.dp))
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = androidx.compose.ui.Alignment.CenterVertically
        ) {
            Text("MESSAGES FROM THE DEVELOPER", fontSize = 12.sp, fontWeight = FontWeight.Bold, fontFamily = FontFamily.Monospace)
            TextButton(onClick = { refreshInbox() }, enabled = !loadingInbox) {
                Text(if (loadingInbox) "..." else "REFRESH", fontSize = 11.sp, fontFamily = FontFamily.Monospace)
            }
        }
        val list = inbox
        when {
            !SupportRules.isValidEmail(email) ->
                Text("Enter your email above to see messages sent to you.", fontSize = 12.sp, color = MaterialTheme.colorScheme.onSurfaceVariant)
            list == null ->
                Text("The inbox is not set up yet.", fontSize = 12.sp, color = MaterialTheme.colorScheme.onSurfaceVariant)
            list.isEmpty() ->
                Text("No messages yet.", fontSize = 12.sp, color = MaterialTheme.colorScheme.onSurfaceVariant)
            else -> list.forEach { m ->
                Column(
                    modifier = Modifier.fillMaxWidth().padding(vertical = 4.dp).clip(RoundedCornerShape(10.dp))
                        .background(Color(0xFF0F1728)).padding(12.dp)
                ) {
                    Text(m.title, fontWeight = FontWeight.Bold, fontSize = 14.sp)
                    Text(m.date, fontSize = 10.sp, color = MaterialTheme.colorScheme.onSurfaceVariant, fontFamily = FontFamily.Monospace)
                    Text(m.body, fontSize = 13.sp, modifier = Modifier.padding(top = 4.dp))
                }
            }
        }
    }
}
