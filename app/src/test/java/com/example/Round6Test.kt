package com.example

import com.example.actions.Action
import com.example.actions.ActionRegistry
import com.example.actions.ActionResult
import com.example.actions.ApprovalMode
import com.example.actions.ApprovalPolicy
import com.example.actions.ConfirmationSpec
import com.example.actions.ParamDefinition
import com.example.actions.SafetyGuard
import com.example.actions.UndoStack
import com.example.core.gemini.GreetingPrompt
import com.example.core.support.SupportRules
import com.example.service.AnnouncementText
import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertTrue
import org.junit.Test

class Round6Test {

    // ---- permission levels ----

    private fun policy(store: MutableSet<String> = mutableSetOf()) =
        ApprovalPolicy({ store.toSet() }, { store.clear(); store.addAll(it) })

    @Test fun defaultIsAskEachTime() {
        assertEquals(ApprovalMode.ASK, policy().modeFor("make_phone_call"))
        assertFalse(policy().isPreApproved("make_phone_call"))
    }

    @Test fun sessionGrantEndsWithTheSession() {
        val p = policy()
        p.setMode("send_sms", ApprovalMode.SESSION)
        assertTrue(p.isPreApproved("send_sms"))
        p.endSession()
        assertFalse(p.isPreApproved("send_sms"))
    }

    @Test fun alwaysSurvivesRestartAndSessionEnd() {
        val saved = mutableSetOf<String>()
        val p = policy(saved)
        p.setMode("send_sms", ApprovalMode.ALWAYS)
        p.endSession()
        assertTrue(p.isPreApproved("send_sms"))
        assertTrue("persisted", policy(saved).isPreApproved("send_sms"))
    }

    @Test fun changingModeAndResettingWork() {
        val p = policy()
        p.setMode("a", ApprovalMode.ALWAYS)
        p.setMode("a", ApprovalMode.ASK)
        assertEquals(ApprovalMode.ASK, p.modeFor("a"))
        p.setMode("b", ApprovalMode.SESSION); p.setMode("c", ApprovalMode.ALWAYS)
        p.resetAll()
        assertTrue(p.states.value.isEmpty())
    }

    private class Risky(var runs: Int = 0) : Action {
        override val name = "risky"
        override val description = "x"
        override val parameters: Map<String, ParamDefinition> = emptyMap()
        override val riskyLabel = "Risky thing"
        override fun confirmationFor(args: Map<String, Any?>) = ConfirmationSpec("t", "d")
        override suspend fun execute(args: Map<String, Any?>): ActionResult { runs++; return ActionResult("done") }
    }

    @Test fun registrySkipsTheDialogOnlyWhenPreApproved() = runBlocking {
        val p = policy()
        val guard = SafetyGuard()
        val reg = ActionRegistry(UndoStack(), guard, p)
        val a = Risky(); reg.register(a)
        assertEquals(listOf("risky" to "Risky thing"), reg.getRiskyActions())

        reg.executeAction("risky", emptyMap())
        assertEquals("asks first by default", 0, a.runs)
        assertNotNull(guard.pendingRequest.value)
        guard.reject()

        p.setMode("risky", ApprovalMode.SESSION)
        assertEquals("done", reg.executeAction("risky", emptyMap()).spokenResult)
        assertEquals(1, a.runs)

        p.endSession()
        reg.executeAction("risky", emptyMap())
        assertEquals("asks again after the session", 1, a.runs)
    }

    // ---- greeting, announcements, support ----

    @Test fun greetingUsesTheNameAndOffersTheBriefingOncePerDay() {
        val first = GreetingPrompt.build("Aarav", true)
        assertTrue(first.contains("Aarav") && first.contains("briefing"))
        assertFalse(GreetingPrompt.build("Aarav", false).contains("briefing"))
        assertTrue(GreetingPrompt.build("Sir", false).contains("the user"))
    }

    @Test fun announcementsAreShortAndNamed() {
        assertEquals("Incoming call from Mom.", AnnouncementText.call("Mom"))
        assertEquals("Incoming call.", AnnouncementText.call(null))
        assertTrue(AnnouncementText.sms("Mom", "x".repeat(500)).length < 260)
    }

    @Test fun emailAndProblemValidation() {
        assertTrue(SupportRules.isValidEmail("a.b+c@mail.example.com"))
        assertFalse(SupportRules.isValidEmail("no-at-sign"))
        assertFalse(SupportRules.isValidEmail("a@b"))
        assertFalse(SupportRules.isValidProblem("short"))
        assertTrue(SupportRules.isValidProblem("The app keeps disconnecting"))
    }

    @Test fun inboxShowsMessagesForEveryoneAndForThisUserOnly() {
        val json = """{"messages":[
            {"id":"1","title":"Update","body":"b1","date":"2026-10-01","to":"all"},
            {"id":"2","title":"For you","body":"b2","date":"2026-10-03","to":"Me@Mail.com"},
            {"id":"3","title":"Someone else","body":"b3","date":"2026-10-02","to":"other@mail.com"},
            {"title":"no id","to":"all"}]}"""
        val got = SupportRules.parseInbox(json, "me@mail.com")
        assertEquals(listOf("2", "1"), got.map { it.id })
        assertTrue(SupportRules.parseInbox("garbage", "x@y.com").isEmpty())
    }
}
