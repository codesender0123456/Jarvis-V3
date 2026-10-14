package com.example.actions.impl

import android.accessibilityservice.AccessibilityService
import android.app.SearchManager
import android.content.Context
import android.content.Intent
import android.net.Uri
import android.provider.Settings
import com.example.actions.Action
import com.example.actions.ActionResult
import com.example.actions.ParamDefinition
import com.example.service.ClickResult
import com.example.service.JarvisAccessibilityService
import kotlinx.coroutines.delay

/**
 * Operates other apps through the accessibility service. The model works in a loop: look
 * ('read_screen'), act ('click_text', 'click_index', 'type_text', 'press_enter', 'scroll_*'),
 * look again to check. 'close_app' force-stops an app; 'play_youtube' searches YouTube and
 * taps the first result (best effort: YouTube's layout changes).
 */
class AccessibilityControlAction(private val context: Context) : Action {

    override val name: String = "accessibility_control"
    override val description: String =
        "Operates the phone's screen and other apps. First call read_screen to see numbered elements, then " +
        "click_text / click_index / type_text / press_enter / scroll_* / long_press / back / home / recents, " +
        "and read_screen again to confirm. close_app force-stops an app by name. play_youtube searches YouTube " +
        "and plays the first result. Do not read the whole screen listing aloud; summarise it."
    override val parameters: Map<String, ParamDefinition> = mapOf(
        "action" to ParamDefinition(
            "string", "What to do", required = true,
            enumValues = listOf(
                "read_screen", "click_text", "click_index", "type_text", "press_enter", "tap", "long_press",
                "scroll_down", "scroll_up", "scroll_left", "scroll_right", "back", "home", "recents",
                "close_app", "play_youtube"
            )
        ),
        "text_to_find" to ParamDefinition("string", "Visible text or description of the element to click", required = false),
        "index" to ParamDefinition("integer", "Element number from the last read_screen", required = false),
        "text" to ParamDefinition("string", "Text to type (type_text)", required = false),
        "submit" to ParamDefinition("boolean", "Press the keyboard's search/enter key after typing", required = false),
        "app_name" to ParamDefinition("string", "App to close (close_app)", required = false),
        "query" to ParamDefinition("string", "What to search for (play_youtube)", required = false),
        "x" to ParamDefinition("number", "X coordinate (tap, long_press)", required = false),
        "y" to ParamDefinition("number", "Y coordinate (tap, long_press)", required = false)
    )

    override suspend fun execute(args: Map<String, Any?>): ActionResult {
        val action = (args["action"] as? String)?.lowercase() ?: "read_screen"

        if (action == "play_youtube") return playYoutube(args["query"] as? String)

        val service = JarvisAccessibilityService.instance ?: return needsService()
        return when (action) {
            "read_screen" -> {
                val screen = service.describeScreen()
                if (screen.isEmpty()) ActionResult("The screen has nothing readable (it may be protected, or still loading).", isError = true)
                else ActionResult(screen, mapOf("screen_text" to screen))
            }
            "click_text" -> {
                val t = (args["text_to_find"] as? String).orEmpty()
                clickResult(service.clickByText(t), t)
            }
            "click_index" -> {
                val i = (args["index"] as? Number)?.toInt() ?: return ActionResult("Which element number?", isError = true)
                clickResult(service.clickIndex(i), "element $i")
            }
            "type_text" -> {
                val text = args["text"] as? String ?: return ActionResult("What should I type?", isError = true)
                val idx = (args["index"] as? Number)?.toInt()
                if (!service.typeText(text, idx)) {
                    ActionResult("I couldn't find a text field to type into. Tap the field first, or read the screen.", isError = true)
                } else {
                    delay(250)
                    val submitted = if (args["submit"] as? Boolean == true) service.pressEnter() else false
                    ActionResult(
                        if (args["submit"] as? Boolean == true && !submitted)
                            "Typed it. I could not press enter on this Android version; tap the search button."
                        else "Typed it${if (submitted) " and pressed enter" else ""}."
                    )
                }
            }
            "press_enter" -> if (service.pressEnter()) ActionResult("Pressed enter.")
                else ActionResult("I couldn't press enter here.", isError = true)
            "tap" -> {
                val x = (args["x"] as? Number)?.toFloat(); val y = (args["y"] as? Number)?.toFloat()
                if (x == null || y == null) ActionResult("I need x and y to tap.", isError = true)
                else { service.tap(x, y); ActionResult("Tapped.") }
            }
            "long_press" -> {
                val x = (args["x"] as? Number)?.toFloat(); val y = (args["y"] as? Number)?.toFloat()
                if (x == null || y == null) ActionResult("I need x and y to long-press.", isError = true)
                else { service.longPress(x, y); ActionResult("Long-pressed.") }
            }
            "scroll_down" -> { service.scroll(true); ActionResult("Scrolled down.") }
            "scroll_up" -> { service.scroll(false); ActionResult("Scrolled up.") }
            "scroll_left" -> { service.swipeHorizontal(true); ActionResult("Swiped left.") }
            "scroll_right" -> { service.swipeHorizontal(false); ActionResult("Swiped right.") }
            "back" -> { service.performGlobalAction(AccessibilityService.GLOBAL_ACTION_BACK); ActionResult("Back.") }
            "home" -> { service.performGlobalAction(AccessibilityService.GLOBAL_ACTION_HOME); ActionResult("Home.") }
            "recents" -> { service.performGlobalAction(AccessibilityService.GLOBAL_ACTION_RECENTS); ActionResult("Recent apps.") }
            "close_app" -> closeApp(service, args["app_name"] as? String)
            else -> ActionResult("Unknown accessibility action: $action", isError = true)
        }
    }

    private fun needsService() = ActionResult(
        "The accessibility service is off. Turn on JARVIS under Settings > Accessibility, then ask again.",
        isError = true
    ).also { openAccessibilitySettings() }

    private fun openAccessibilitySettings() {
        runCatching {
            context.startActivity(Intent(Settings.ACTION_ACCESSIBILITY_SETTINGS).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK))
        }
    }

    private fun clickResult(r: ClickResult, what: String) = when (r) {
        ClickResult.CLICKED -> ActionResult("Clicked $what.")
        ClickResult.DISABLED -> ActionResult("$what is disabled right now.", isError = true)
        ClickResult.NOT_FOUND -> ActionResult("I couldn't find '$what' on screen. Read the screen to see what is there.", isError = true)
    }

    // ---- close_app ----------------------------------------------------------------------

    private suspend fun closeApp(service: JarvisAccessibilityService, appName: String?): ActionResult {
        val query = appName?.trim().orEmpty()
        if (query.isEmpty()) return ActionResult("Which app should I close?", isError = true)
        val pm = context.packageManager
        val apps = pm.queryIntentActivities(
            Intent(Intent.ACTION_MAIN).addCategory(Intent.CATEGORY_LAUNCHER), 0
        ).map { it.loadLabel(pm).toString() to it.activityInfo.packageName }
        val best = apps.maxByOrNull { score(query, it.first) }
        if (best == null || score(query, best.first) < 0.6) return ActionResult("I couldn't find an app called $query.", isError = true)
        if (best.second == context.packageName) return ActionResult("I won't close myself.", isError = true)

        context.startActivity(
            Intent(Settings.ACTION_APPLICATION_DETAILS_SETTINGS, Uri.parse("package:${best.second}"))
                .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
        )
        val force = clickAny(service, FORCE_STOP, 6000)
        when (force) {
            ClickResult.NOT_FOUND -> return ActionResult("I opened ${best.first}'s info but couldn't find the Force stop button.", isError = true)
            ClickResult.DISABLED -> {
                service.performGlobalAction(AccessibilityService.GLOBAL_ACTION_BACK)
                return ActionResult("${best.first} is not running, so there was nothing to close.")
            }
            ClickResult.CLICKED -> Unit
        }
        clickAny(service, CONFIRM, 4000)
        delay(600)
        service.performGlobalAction(AccessibilityService.GLOBAL_ACTION_HOME)
        return ActionResult("Closed ${best.first}.")
    }

    private suspend fun clickAny(service: JarvisAccessibilityService, labels: List<String>, timeoutMs: Long): ClickResult {
        var waited = 0L
        var last = ClickResult.NOT_FOUND
        while (waited <= timeoutMs) {
            for (l in labels) {
                val r = service.clickByText(l)
                if (r != ClickResult.NOT_FOUND) return r
            }
            delay(400); waited += 400
            last = ClickResult.NOT_FOUND
        }
        return last
    }

    private fun score(q: String, c: String): Double {
        fun n(s: String) = s.lowercase().filter { it.isLetterOrDigit() }
        val a = n(q); val b = n(c)
        if (a.isEmpty() || b.isEmpty()) return 0.0
        if (a == b) return 1.0
        if (b.contains(a) || a.contains(b)) return 0.9
        return 0.0
    }

    // ---- play_youtube -------------------------------------------------------------------

    private suspend fun playYoutube(query: String?): ActionResult {
        val q = query?.trim().orEmpty()
        if (q.isEmpty()) return ActionResult("What should I search for on YouTube?", isError = true)
        val search = Intent(Intent.ACTION_SEARCH).setPackage(YOUTUBE).putExtra(SearchManager.QUERY, q)
            .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
        val opened = runCatching { context.startActivity(search) }.isSuccess ||
            runCatching {
                context.startActivity(
                    Intent(Intent.ACTION_VIEW, Uri.parse("https://www.youtube.com/results?search_query=${Uri.encode(q)}"))
                        .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
                )
            }.isSuccess
        if (!opened) return ActionResult("I couldn't open YouTube.", isError = true)

        val service = JarvisAccessibilityService.instance
            ?: return ActionResult("YouTube search for '$q' is open. Turn on JARVIS accessibility to let me pick and play a result.").also { openAccessibilitySettings() }

        // Wait for the results, then click the first video.
        var waited = 0L
        while (waited < 10_000L) {
            delay(700); waited += 700
            if (service.currentPackage() != YOUTUBE) continue
            val r = service.clickByText("play video")
            if (r == ClickResult.CLICKED) return ActionResult("Playing the first YouTube result for '$q'.")
        }
        val screen = service.describeScreen(1500)
        return ActionResult(
            "YouTube is showing results for '$q', but I couldn't pick a video by myself. " +
                "Use click_index on a result from this list:\n$screen"
        )
    }

    private companion object {
        const val YOUTUBE = "com.google.android.youtube"
        val FORCE_STOP = listOf(
            "force stop", "force close", "forzar detención", "forcer l'arrêt", "beenden erzwingen",
            "जबरन रोकें", "बलपूर्वक रोकें", "जबरदस्ती रोकें"
        )
        val CONFIRM = listOf("ok", "force stop", "yes", "ठीक है", "हाँ", "aceptar", "ok ")
    }
}
