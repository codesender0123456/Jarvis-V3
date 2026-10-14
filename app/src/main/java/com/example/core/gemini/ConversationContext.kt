package com.example.core.gemini

/**
 * Builds the "what we were talking about" block that is added to the system prompt when the
 * session has to start fresh (for example after a voice change: the server keeps the ORIGINAL
 * voice when a session is resumed, so a new voice needs a new session).
 */
object ConversationContext {
    fun build(lines: List<Pair<String, String>>, maxLines: Int = 14, maxCharsPerLine: Int = 280): String? {
        val recent = lines.filter { (who, text) -> (who == "user" || who == "assistant") && text.isNotBlank() }
            .takeLast(maxLines)
        if (recent.isEmpty()) return null
        val body = recent.joinToString("\n") { (who, text) ->
            "${if (who == "user") "User" else "You"}: ${text.trim().take(maxCharsPerLine)}"
        }
        return "\n\n# Conversation so far (the session was restarted; carry on naturally, do not recap it)\n$body"
    }
}
