package com.example.core.support

import android.content.Context
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.RequestBody.Companion.toRequestBody
import org.json.JSONArray
import org.json.JSONObject

data class InboxMessage(val id: String, val title: String, val body: String, val date: String)

sealed interface SendResult {
    data object Sent : SendResult
    /** No Web3Forms key configured; the caller can fall back to the email app. */
    data object NotConfigured : SendResult
    data class Failed(val reason: String) : SendResult
}

/** Pure helpers (unit-tested). */
object SupportRules {
    private val EMAIL = Regex("^[A-Za-z0-9._%+-]+@[A-Za-z0-9.-]+\\.[A-Za-z]{2,}$")
    fun isValidEmail(email: String) = EMAIL.matches(email.trim())

    const val MIN_PROBLEM_CHARS = 10
    fun isValidProblem(text: String) = text.trim().length >= MIN_PROBLEM_CHARS

    /** One message per minute is plenty and keeps the form from being used to spam the developer. */
    const val SEND_COOLDOWN_MS = 60_000L

    /** Messages meant for everyone or for this user, newest first. */
    fun parseInbox(json: String, userEmail: String): List<InboxMessage> {
        val arr = try { JSONObject(json).optJSONArray("messages") } catch (e: Exception) { null } ?: JSONArray()
        val out = ArrayList<InboxMessage>()
        for (i in 0 until arr.length()) {
            val o = arr.optJSONObject(i) ?: continue
            val to = o.optString("to", "all").trim()
            if (!to.equals("all", ignoreCase = true) && !to.equals(userEmail.trim(), ignoreCase = true)) continue
            val id = o.optString("id", "")
            if (id.isBlank()) continue
            out += InboxMessage(id, o.optString("title"), o.optString("body"), o.optString("date"))
        }
        return out.sortedByDescending { it.date }
    }
}

/** Sends contact messages and reads developer messages. No server of our own is involved. */
class SupportClient(private val context: Context, private val http: OkHttpClient) {
    private val prefs = context.getSharedPreferences("jarvis_support", Context.MODE_PRIVATE)

    var savedEmail: String
        get() = prefs.getString("email", "").orEmpty()
        set(v) { prefs.edit().putString("email", v.trim()).apply() }

    var lastSentAt: Long
        get() = prefs.getLong("last_sent", 0L)
        set(v) { prefs.edit().putLong("last_sent", v).apply() }

    private fun readIds(): Set<String> = prefs.getStringSet("read_ids", emptySet()) ?: emptySet()
    fun unreadCount(messages: List<InboxMessage>) = messages.count { it.id !in readIds() }
    fun markRead(messages: List<InboxMessage>) {
        prefs.edit().putStringSet("read_ids", readIds() + messages.map { it.id }).apply()
    }

    suspend fun send(userEmail: String, problem: String, appInfo: String): SendResult = withContext(Dispatchers.IO) {
        if (SupportConfig.WEB3FORMS_ACCESS_KEY.isBlank()) return@withContext SendResult.NotConfigured
        val body = JSONObject()
            .put("access_key", SupportConfig.WEB3FORMS_ACCESS_KEY)
            .put("subject", "JARVIS Mobile support message")
            .put("from_name", "JARVIS Mobile user")
            .put("email", userEmail)
            .put("message", "$problem\n\n--\n$appInfo")
            .toString()
        try {
            val req = Request.Builder().url("https://api.web3forms.com/submit")
                .header("Accept", "application/json")
                .post(body.toRequestBody("application/json".toMediaType())).build()
            http.newCall(req).execute().use { resp ->
                val ok = try { JSONObject(resp.body?.string().orEmpty()).optBoolean("success", false) } catch (e: Exception) { false }
                if (resp.isSuccessful && ok) SendResult.Sent else SendResult.Failed("The mail service answered ${resp.code}.")
            }
        } catch (e: Exception) {
            SendResult.Failed("Could not reach the mail service (${e.javaClass.simpleName}).")
        }
    }

    /** Null when the developer has not set a feed; an empty list when there is nothing for this user. */
    suspend fun fetchInbox(userEmail: String): List<InboxMessage>? = withContext(Dispatchers.IO) {
        if (SupportConfig.INBOX_FEED_URL.isBlank()) return@withContext null
        try {
            http.newCall(Request.Builder().url(SupportConfig.INBOX_FEED_URL).build()).execute().use { resp ->
                if (!resp.isSuccessful) return@use emptyList<InboxMessage>()
                SupportRules.parseInbox(resp.body?.string().orEmpty(), userEmail)
            }
        } catch (e: Exception) { emptyList() }
    }
}
