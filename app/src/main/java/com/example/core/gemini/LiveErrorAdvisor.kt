package com.example.core.gemini

/** What the "fix it" button on an error does. */
enum class GuidanceAction { NONE, ENTER_KEY, OPEN_AI_STUDIO, OPEN_NETWORK_SETTINGS, OPEN_APP_SETTINGS }

/** A plain-language explanation of a failure, with the steps that fix it. */
data class ErrorGuidance(
    val title: String,
    val explanation: String,
    val steps: List<String>,
    val action: GuidanceAction = GuidanceAction.NONE,
    val actionLabel: String? = null,
    val canRetry: Boolean = true,
    /** Raw code and reason, for copying into a bug report. */
    val technical: String = ""
)

/**
 * Turns a close code and reason from the Live connection into something a user can act on.
 * Pure logic (no Android), covered by unit tests.
 */
object LiveErrorAdvisor {

    private const val AI_STUDIO = "aistudio.google.com/apikey"

    fun missingKey() = ErrorGuidance(
        title = "No Gemini API key",
        explanation = "JARVIS needs your own free Gemini API key before it can talk.",
        steps = listOf(
            "Tap GET A KEY and create a key at $AI_STUDIO (sign in with Google, tap Create API key).",
            "Copy the key, come back, and tap ENTER KEY.",
            "Paste it, tap SAVE & PROCEED, then press the power button."
        ),
        action = GuidanceAction.OPEN_AI_STUDIO,
        actionLabel = "GET A KEY",
        canRetry = false
    )

    fun microphone(detail: String) = ErrorGuidance(
        title = "Microphone not available",
        explanation = "JARVIS is connected, but it cannot hear you. $detail",
        steps = listOf(
            "Tap OPEN SETTINGS, then Permissions, then Microphone, and choose Allow.",
            "Come back and press the power button off and on again.",
            "If another app is recording (a call or a recorder), close it first."
        ),
        action = GuidanceAction.OPEN_APP_SETTINGS,
        actionLabel = "OPEN SETTINGS",
        canRetry = true,
        technical = detail
    )

    fun fromClose(code: Int, reason: String, kind: CloseKind, allModelsRefused: Boolean = false, attempts: Int = 0): ErrorGuidance {
        val tech = "code $code: ${reason.ifBlank { "no reason given" }}"
        val r = reason.lowercase()

        return when {
            kind == CloseKind.FATAL_KEY -> ErrorGuidance(
                title = "Google rejected your API key",
                explanation = "The key in the app is invalid, was deleted, or was copied incompletely.",
                steps = listOf(
                    "Open $AI_STUDIO and copy the key again (it starts with AIza and has no spaces).",
                    "Tap ENTER KEY, paste it, and save.",
                    "If it still fails, create a new key and use that one."
                ),
                action = GuidanceAction.ENTER_KEY, actionLabel = "ENTER KEY", canRetry = false, technical = tech
            )

            kind == CloseKind.MODEL_UNAVAILABLE && (code == 1008 || "denied" in r) -> ErrorGuidance(
                title = "Google denied your project",
                explanation = "Google is refusing the Google Cloud project behind your key. This is not an internet problem, " +
                    "and retrying will not help. It usually means the project was restricted or is not allowed to use Gemini Live.",
                steps = listOf(
                    "Tap GET A KEY and create a key in a NEW project (choose \"Create API key in new project\").",
                    "Tap ENTER NEW KEY, paste the new key, and save.",
                    "Press the power button to connect.",
                    "If the new project is also denied, check the project's status at $AI_STUDIO or contact Google support: only Google can lift that."
                ),
                action = GuidanceAction.OPEN_AI_STUDIO, actionLabel = "GET A KEY", canRetry = false, technical = tech
            )

            kind == CloseKind.MODEL_UNAVAILABLE -> ErrorGuidance(
                title = "Your key can't use the voice models",
                explanation = if (allModelsRefused) "Gemini refused both voice models for this key." else "Gemini refused this voice model for your key.",
                steps = listOf(
                    "Create a fresh key in a new project at $AI_STUDIO.",
                    "Enter it in the app and press the power button.",
                    "Some regions or older projects do not have Live access yet; a new project usually fixes that."
                ),
                action = GuidanceAction.OPEN_AI_STUDIO, actionLabel = "GET A KEY", canRetry = false, technical = tech
            )

            kind == CloseKind.QUOTA -> ErrorGuidance(
                title = "Free usage limit reached",
                explanation = "Your Gemini key has used up its current quota (too many requests or too much audio).",
                steps = listOf(
                    "Wait a few minutes and try again; per-minute limits reset quickly.",
                    "If it keeps happening all day, the daily free limit is used up: try tomorrow, or enable billing for the project in Google AI Studio.",
                    "A second key from a different project also has its own quota."
                ),
                action = GuidanceAction.NONE, canRetry = true, technical = tech
            )

            looksLikeNetwork(r) -> ErrorGuidance(
                title = "Can't reach Google",
                explanation = "The phone could not open a connection to Gemini. " +
                    if (attempts > 0) "It tried $attempts times." else "",
                steps = listOf(
                    "Check that Wi-Fi or mobile data is on and has internet (open a web page to test).",
                    "Turn off any VPN or ad-blocking DNS, which can block Google's voice servers.",
                    "Some networks (office, school) block WebSocket connections: try mobile data.",
                    "Then tap TRY AGAIN."
                ),
                action = GuidanceAction.OPEN_NETWORK_SETTINGS, actionLabel = "NETWORK SETTINGS", canRetry = true, technical = tech
            )

            code == 1011 || code in 500..599 || "internal" in r || "unavailable" in r || "overloaded" in r -> ErrorGuidance(
                title = "Gemini is having trouble",
                explanation = "The error is on Google's side, not yours.",
                steps = listOf(
                    "Wait a minute and tap TRY AGAIN.",
                    "If it persists, check Google's status page, then try later."
                ),
                action = GuidanceAction.NONE, canRetry = true, technical = tech
            )

            else -> ErrorGuidance(
                title = "Couldn't connect",
                explanation = "The connection to Gemini failed for a reason I don't recognise." +
                    if (attempts > 0) " It tried $attempts times." else "",
                steps = listOf(
                    "Tap TRY AGAIN.",
                    "Check your internet connection and that the API key is correct.",
                    "If it keeps failing, copy the details below and send them to whoever maintains the app."
                ),
                action = GuidanceAction.NONE, canRetry = true, technical = tech
            )
        }
    }

    private fun looksLikeNetwork(r: String): Boolean = listOf(
        "unknownhost", "unable to resolve", "timeout", "timed out", "connectexception", "failed to connect",
        "network is unreachable", "no route to host", "connection reset", "sslhandshake", "software caused connection abort",
        "connection refused", "eof"
    ).any { it in r }
}
