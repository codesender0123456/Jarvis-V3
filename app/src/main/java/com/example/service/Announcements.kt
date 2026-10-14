package com.example.service

import android.Manifest
import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.net.Uri
import android.provider.ContactsContract
import android.provider.Telephony
import android.speech.tts.TextToSpeech
import android.speech.tts.UtteranceProgressListener
import android.telephony.TelephonyManager
import android.util.Log
import androidx.core.content.ContextCompat
import com.example.JarvisApp
import com.example.core.config.AppPreferences
import com.example.core.gemini.LiveSessionState
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import java.util.Locale

/** The sentences JARVIS speaks. Pure, so they can be tested. */
object AnnouncementText {
    fun sms(sender: String, body: String) =
        "New text message from $sender: ${body.trim().take(200)}"

    fun call(caller: String?) =
        if (caller.isNullOrBlank()) "Incoming call." else "Incoming call from $caller."
}

/**
 * Speaks an announcement. During a conversation the assistant says it in its own voice; otherwise
 * Android's text-to-speech reads it. Callers must already have checked the user's setting.
 */
object Announcer {
    private const val TAG = "Announcer"

    fun announce(context: Context, text: String) {
        val app = context.applicationContext as? JarvisApp
        val session = app?.liveSession
        if (session != null && session.isWanted && session.sessionState.value in setOf(
                LiveSessionState.LISTENING, LiveSessionState.SPEAKING, LiveSessionState.THINKING
            )
        ) {
            session.sendSourceNote("[Tell the user this right away, briefly, in your own words: $text]")
            return
        }
        speak(context.applicationContext, text)
    }

    private fun speak(context: Context, text: String) {
        var tts: TextToSpeech? = null
        tts = TextToSpeech(context) { status ->
            val engine = tts
            if (status == TextToSpeech.SUCCESS && engine != null) {
                engine.language = Locale.getDefault()
                engine.setOnUtteranceProgressListener(object : UtteranceProgressListener() {
                    override fun onStart(utteranceId: String?) {}
                    override fun onDone(utteranceId: String?) { engine.shutdown() }
                    @Deprecated("Deprecated in Java")
                    override fun onError(utteranceId: String?) { engine.shutdown() }
                })
                engine.speak(text, TextToSpeech.QUEUE_ADD, null, "jarvis_announce")
            } else {
                Log.w(TAG, "TTS unavailable")
            }
        }
    }

    /** Contact name for a number, or the number itself when contacts are not readable. */
    fun displayName(context: Context, number: String?): String? {
        if (number.isNullOrBlank()) return null
        if (ContextCompat.checkSelfPermission(context, Manifest.permission.READ_CONTACTS) != PackageManager.PERMISSION_GRANTED) return number
        return try {
            val uri = Uri.withAppendedPath(ContactsContract.PhoneLookup.CONTENT_FILTER_URI, Uri.encode(number))
            context.contentResolver.query(uri, arrayOf(ContactsContract.PhoneLookup.DISPLAY_NAME), null, null, null)
                ?.use { if (it.moveToFirst()) it.getString(0) else null } ?: number
        } catch (e: Exception) { number }
    }
}

class SmsAnnounceReceiver : BroadcastReceiver() {
    override fun onReceive(context: Context, intent: Intent) {
        if (intent.action != Telephony.Sms.Intents.SMS_RECEIVED_ACTION) return
        val parts = Telephony.Sms.Intents.getMessagesFromIntent(intent) ?: return
        if (parts.isEmpty()) return
        val from = parts[0].originatingAddress
        val body = parts.joinToString("") { it.messageBody.orEmpty() }
        val pending = goAsync()
        CoroutineScope(Dispatchers.Default).launch {
            try {
                if (AppPreferences(context.applicationContext).settingsFlowOnce().announceSms) {
                    val who = Announcer.displayName(context, from) ?: "an unknown number"
                    Announcer.announce(context, AnnouncementText.sms(who, body))
                }
            } finally { pending.finish() }
        }
    }
}

class CallAnnounceReceiver : BroadcastReceiver() {
    override fun onReceive(context: Context, intent: Intent) {
        if (intent.action != TelephonyManager.ACTION_PHONE_STATE_CHANGED) return
        if (intent.getStringExtra(TelephonyManager.EXTRA_STATE) != TelephonyManager.EXTRA_STATE_RINGING) return
        // The system sends this broadcast twice per call (without, then with the number): announce once.
        val now = System.currentTimeMillis()
        if (now - lastAnnouncedAt < 6_000L) return
        val number = intent.getStringExtra(TelephonyManager.EXTRA_INCOMING_NUMBER)
        val pending = goAsync()
        CoroutineScope(Dispatchers.Default).launch {
            try {
                if (AppPreferences(context.applicationContext).settingsFlowOnce().announceCalls) {
                    lastAnnouncedAt = now
                    Announcer.announce(context, AnnouncementText.call(Announcer.displayName(context, number)))
                }
            } finally { pending.finish() }
        }
    }

    private companion object { @Volatile var lastAnnouncedAt = 0L }
}
