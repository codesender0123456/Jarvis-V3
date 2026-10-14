package com.example.actions.impl

import com.example.actions.Action
import com.example.actions.ActionResult
import com.example.actions.ParamDefinition
import com.example.core.gemini.GeminiClient
import com.example.memory.MemoryRepository
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

/**
 * The spoken morning briefing: date and time, a recap of the last conversation (said once, then
 * consumed), and today's top news from Google Search. Weather comes from get_weather, so the
 * assistant is told to call it once it knows the user's city.
 */
class DailyBriefingToolAction(
    private val geminiClient: GeminiClient,
    private val memory: MemoryRepository
) : Action {

    override val name: String = "daily_briefing"
    override val description: String =
        "Gives the user's briefing: today's date and time, a recap of the last conversation, and top news. " +
        "Then call get_weather for the weather (ask for the city if you don't know it). " +
        "Summarise it in a few spoken sentences, not a list."
    override val parameters: Map<String, ParamDefinition> = mapOf(
        "topic" to ParamDefinition("string", "Optional news topic, for example 'technology' or 'cricket'", required = false)
    )

    override suspend fun execute(args: Map<String, Any?>): ActionResult {
        val now = SimpleDateFormat("EEEE d MMMM, HH:mm", Locale.getDefault()).format(Date())
        val topic = (args["topic"] as? String)?.trim().orEmpty()
        val recap = runCatching { memory.consumeMorningRecapIfAvailable() }.getOrNull()
        val region = Locale.getDefault().displayCountry.ifBlank { "the world" }
        val news = geminiClient.callOneShot(
            "Use Google Search. Give the 3 most important news headlines today" +
                (if (topic.isNotEmpty()) " about $topic" else " for $region") +
                ", one short sentence each, with the outlet name. Plain text, no markdown.",
            grounded = true
        )
        val sb = StringBuilder("It is $now.")
        if (!recap.isNullOrBlank()) sb.append("\nLast time: $recap")
        sb.append("\nNews: ").append(if (news.isError || news.text.isBlank()) "news is unavailable right now." else news.text)
        sb.append("\nNext: call get_weather for the weather.")
        return ActionResult(sb.toString(), mapOf("title" to "Briefing", "snippet" to sb.toString().take(400)))
    }
}

/**
 * Live interpreter mode. The assistant itself does the translating; this tool switches the mode on
 * and off and tells it exactly how to behave. Interruptions already work: when the user speaks
 * over the assistant, playback is cut and the assistant listens.
 */
class LiveInterpreterAction : Action {

    override val name: String = "live_interpreter"
    override val description: String =
        "Turns live interpreter mode on or off. While on, translate everything the user says into the other " +
        "language and speak ONLY the translation."
    override val parameters: Map<String, ParamDefinition> = mapOf(
        "mode" to ParamDefinition("string", "'on' or 'off'", required = true, enumValues = listOf("on", "off")),
        "language_a" to ParamDefinition("string", "The user's language, for example 'Hindi'", required = false),
        "language_b" to ParamDefinition("string", "The other language, for example 'English'", required = false)
    )

    override suspend fun execute(args: Map<String, Any?>): ActionResult {
        if ((args["mode"] as? String)?.lowercase() == "off") {
            return ActionResult("Interpreter mode is off. Go back to normal conversation.")
        }
        val a = (args["language_a"] as? String)?.trim().orEmpty().ifEmpty { "the user's language" }
        val b = (args["language_b"] as? String)?.trim().orEmpty().ifEmpty { "English" }
        return ActionResult(
            "Interpreter mode is ON between $a and $b. From now on, whenever you hear $a, say it in $b, and " +
                "whenever you hear $b, say it in $a. Speak only the translation: no greetings, no comments, no " +
                "answers to what is said. If the user speaks over you, stop and listen. Leave the mode only when " +
                "asked. Say 'Interpreter on' once now."
        )
    }
}
