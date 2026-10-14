package com.example.service

import android.accessibilityservice.AccessibilityService
import android.accessibilityservice.GestureDescription
import android.graphics.Path
import android.graphics.Rect
import android.os.Build
import android.os.Bundle
import android.view.accessibility.AccessibilityEvent
import android.view.accessibility.AccessibilityNodeInfo

/** One visible element on screen, as handed to the model. */
data class UiElement(
    val index: Int,
    val label: String,
    val clickable: Boolean,
    val editable: Boolean,
    val enabled: Boolean
)

enum class ClickResult { CLICKED, NOT_FOUND, DISABLED }

/**
 * Lets JARVIS operate other apps: read what is on screen, click by visible text or index, type
 * into fields, press the keyboard's action key, scroll, long-press, and use Back/Home/Recents.
 * It only acts when a tool call asks it to; events are not processed.
 */
class JarvisAccessibilityService : AccessibilityService() {

    private var lastSnapshot: List<Pair<UiElement, AccessibilityNodeInfo>> = emptyList()

    override fun onServiceConnected() {
        super.onServiceConnected()
        instance = this
    }

    override fun onAccessibilityEvent(event: AccessibilityEvent?) { /* acts only on demand */ }
    override fun onInterrupt() {}

    override fun onDestroy() {
        super.onDestroy()
        if (instance === this) instance = null
    }

    // ---- Reading ------------------------------------------------------------------------

    fun currentPackage(): String? = rootInActiveWindow?.packageName?.toString()

    /** Visible, labelled or interactive elements of the foreground window (max [maxItems]). */
    fun snapshot(maxItems: Int = 80): List<UiElement> {
        val root = rootInActiveWindow ?: return emptyList()
        val out = ArrayList<Pair<UiElement, AccessibilityNodeInfo>>()
        collect(root, out, maxItems, 0)
        lastSnapshot = out
        return out.map { it.first }
    }

    private fun labelOf(n: AccessibilityNodeInfo): String {
        val t = n.text?.toString()?.trim().orEmpty()
        if (t.isNotEmpty()) return t
        val d = n.contentDescription?.toString()?.trim().orEmpty()
        if (d.isNotEmpty()) return d
        return n.hintText?.toString()?.trim().orEmpty()
    }

    private fun collect(n: AccessibilityNodeInfo, out: MutableList<Pair<UiElement, AccessibilityNodeInfo>>, max: Int, depth: Int) {
        if (out.size >= max || depth > 40 || !n.isVisibleToUser) return
        val label = labelOf(n)
        if (label.isNotEmpty() || n.isEditable) {
            out += UiElement(out.size, label.take(120), n.isClickable, n.isEditable, n.isEnabled) to n
        }
        for (i in 0 until n.childCount) {
            val c = n.getChild(i) ?: continue
            collect(c, out, max, depth + 1)
        }
    }

    /** Compact numbered listing for the model, for example `[3] Search (field)`. */
    fun describeScreen(maxChars: Int = 2500): String {
        val pkg = currentPackage() ?: return ""
        val sb = StringBuilder("App: $pkg\n")
        for (e in snapshot()) {
            val kind = when {
                e.editable -> "field"
                e.clickable -> "button"
                else -> "text"
            }
            val line = "[${e.index}] ${e.label.ifEmpty { "(unlabelled)" }} ($kind${if (!e.enabled) ", disabled" else ""})\n"
            if (sb.length + line.length > maxChars) { sb.append("..."); break }
            sb.append(line)
        }
        return sb.toString().trim()
    }

    // ---- Acting -------------------------------------------------------------------------

    fun clickByText(query: String): ClickResult {
        val q = query.trim().lowercase()
        if (q.isEmpty()) return ClickResult.NOT_FOUND
        snapshot(200)
        val pick = lastSnapshot.firstOrNull { it.first.label.lowercase() == q }
            ?: lastSnapshot.firstOrNull { it.first.label.lowercase().startsWith(q) }
            ?: lastSnapshot.firstOrNull { it.first.label.lowercase().contains(q) }
            ?: return ClickResult.NOT_FOUND
        return clickNode(pick.second)
    }

    fun clickIndex(index: Int): ClickResult {
        val pick = lastSnapshot.getOrNull(index) ?: return ClickResult.NOT_FOUND
        return clickNode(pick.second)
    }

    /** Clicks the node, or its nearest clickable ancestor, or taps its centre as a last resort. */
    private fun clickNode(node: AccessibilityNodeInfo): ClickResult {
        if (!node.isEnabled) return ClickResult.DISABLED
        var cur: AccessibilityNodeInfo? = node
        var hops = 0
        while (cur != null && hops < 7) {
            if (cur.isClickable && cur.isEnabled && cur.performAction(AccessibilityNodeInfo.ACTION_CLICK)) return ClickResult.CLICKED
            cur = cur.parent; hops++
        }
        val r = Rect(); node.getBoundsInScreen(r)
        if (r.isEmpty) return ClickResult.NOT_FOUND
        tap(r.exactCenterX(), r.exactCenterY())
        return ClickResult.CLICKED
    }

    /** Types into the field with [index], else the focused field, else the first editable one. */
    fun typeText(text: String, index: Int? = null): Boolean {
        if (index == null) snapshot(120)
        val target: AccessibilityNodeInfo? = index?.let { lastSnapshot.getOrNull(it)?.second }
            ?: rootInActiveWindow?.findFocus(AccessibilityNodeInfo.FOCUS_INPUT)?.takeIf { it.isEditable }
            ?: lastSnapshot.firstOrNull { it.first.editable }?.second
        target ?: return false
        target.performAction(AccessibilityNodeInfo.ACTION_FOCUS)
        val args = Bundle().apply { putCharSequence(AccessibilityNodeInfo.ACTION_ARGUMENT_SET_TEXT_CHARSEQUENCE, text) }
        return target.performAction(AccessibilityNodeInfo.ACTION_SET_TEXT, args)
    }

    /** The keyboard's Search/Enter key on the focused field (Android 11+). */
    fun pressEnter(): Boolean {
        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.R) return false
        val f = rootInActiveWindow?.findFocus(AccessibilityNodeInfo.FOCUS_INPUT) ?: return false
        return f.performAction(AccessibilityNodeInfo.AccessibilityAction.ACTION_IME_ENTER.id)
    }

    fun tap(x: Float, y: Float) = gesture(x, y, x, y, 60)
    fun longPress(x: Float, y: Float) = gesture(x, y, x, y, 900)

    fun scroll(down: Boolean) {
        val m = resources.displayMetrics
        val x = m.widthPixels / 2f
        gesture(x, m.heightPixels * if (down) 0.75f else 0.25f, x, m.heightPixels * if (down) 0.25f else 0.75f, 300)
    }

    fun swipeHorizontal(left: Boolean) {
        val m = resources.displayMetrics
        val y = m.heightPixels / 2f
        gesture(m.widthPixels * if (left) 0.8f else 0.2f, y, m.widthPixels * if (left) 0.2f else 0.8f, y, 300)
    }

    private fun gesture(x1: Float, y1: Float, x2: Float, y2: Float, durationMs: Long) {
        val path = Path().apply { moveTo(x1, y1); if (x1 != x2 || y1 != y2) lineTo(x2, y2) }
        dispatchGesture(
            GestureDescription.Builder().addStroke(GestureDescription.StrokeDescription(path, 0, durationMs)).build(),
            null, null
        )
    }

    companion object {
        @Volatile var instance: JarvisAccessibilityService? = null
            private set
    }
}
