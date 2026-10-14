package com.example.actions

import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import java.util.concurrent.ConcurrentHashMap

/** How the user wants a risky action handled. */
enum class ApprovalMode { ASK, SESSION, ALWAYS }

/** Decides whether a risky action may skip the confirmation dialog. */
interface ApprovalChecker {
    fun isPreApproved(actionName: String): Boolean
}

/**
 * Per-action permission for risky actions (calls, messages, deleting files...):
 *  ASK     the user confirms every time (default)
 *  SESSION allowed until the current conversation ends
 *  ALWAYS  allowed until the user changes it
 * Changeable at any time from Settings or from the confirmation dialog itself.
 * [loadAlways]/[saveAlways] persist only the ALWAYS list; SESSION grants live in memory and
 * disappear with the session.
 */
class ApprovalPolicy(
    private val loadAlways: () -> Set<String>,
    private val saveAlways: (Set<String>) -> Unit
) : ApprovalChecker {

    private val always = ConcurrentHashMap.newKeySet<String>().apply { addAll(loadAlways()) }
    private val session = ConcurrentHashMap.newKeySet<String>()

    private val _states = MutableStateFlow<Map<String, ApprovalMode>>(emptyMap())
    /** Every action with a non-default mode (for the settings screen and the HUD banner). */
    val states: StateFlow<Map<String, ApprovalMode>> = _states.asStateFlow()

    init { publish() }

    fun modeFor(actionName: String): ApprovalMode = when {
        actionName in always -> ApprovalMode.ALWAYS
        actionName in session -> ApprovalMode.SESSION
        else -> ApprovalMode.ASK
    }

    override fun isPreApproved(actionName: String): Boolean = modeFor(actionName) != ApprovalMode.ASK

    fun setMode(actionName: String, mode: ApprovalMode) {
        always.remove(actionName); session.remove(actionName)
        when (mode) {
            ApprovalMode.ALWAYS -> always.add(actionName)
            ApprovalMode.SESSION -> session.add(actionName)
            ApprovalMode.ASK -> Unit
        }
        saveAlways(always.toSet())
        publish()
    }

    /** Called when the conversation ends: session grants end with it. */
    fun endSession() {
        if (session.isNotEmpty()) { session.clear(); publish() }
    }

    /** Back to "ask every time" for everything (the STOP button). */
    fun resetAll() {
        always.clear(); session.clear(); saveAlways(emptySet()); publish()
    }

    private fun publish() {
        val m = HashMap<String, ApprovalMode>()
        always.forEach { m[it] = ApprovalMode.ALWAYS }
        session.forEach { m[it] = ApprovalMode.SESSION }
        _states.value = m
    }
}
