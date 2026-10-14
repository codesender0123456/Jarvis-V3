package com.example.core.gemini

/** The one-line instruction that makes the assistant greet the user when a session opens. */
object GreetingPrompt {
    fun build(userName: String, firstConversationToday: Boolean): String {
        val who = if (userName.isBlank() || userName.equals("Sir", ignoreCase = true)) "the user" else userName
        val offer = if (firstConversationToday)
            " This is the first conversation today, so after greeting, offer a quick morning briefing (news and weather)."
        else ""
        return "[Session started. Greet $who in ONE short, warm sentence: say hello using their name, ask how they are " +
            "and what you can do for them today.$offer Do not mention this note.]"
    }
}
