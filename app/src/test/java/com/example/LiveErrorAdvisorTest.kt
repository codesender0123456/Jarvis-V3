package com.example

import com.example.core.gemini.CloseKind
import com.example.core.gemini.GuidanceAction
import com.example.core.gemini.LiveErrorAdvisor
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class LiveErrorAdvisorTest {

    @Test fun projectDeniedExplainsItIsNotTheInternetAndOffersAKeyFix() {
        val g = LiveErrorAdvisor.fromClose(1008, "Your project has been denied access. Please contact support.", CloseKind.MODEL_UNAVAILABLE, allModelsRefused = true)
        assertTrue(g.explanation.contains("not an internet problem"))
        assertEquals(GuidanceAction.OPEN_AI_STUDIO, g.action)
        assertFalse("retrying cannot fix a denied project", g.canRetry)
        assertTrue(g.technical.contains("1008"))
        assertTrue(g.steps.any { it.contains("NEW project") })
    }

    @Test fun badKeyOffersToEnterANewOne() {
        val g = LiveErrorAdvisor.fromClose(1007, "API key not valid", CloseKind.FATAL_KEY)
        assertEquals(GuidanceAction.ENTER_KEY, g.action)
        assertFalse(g.canRetry)
    }

    @Test fun quotaCanBeRetriedLater() {
        val g = LiveErrorAdvisor.fromClose(429, "RESOURCE_EXHAUSTED", CloseKind.QUOTA)
        assertTrue(g.canRetry)
        assertTrue(g.title.contains("limit"))
    }

    @Test fun dnsAndTimeoutFailuresPointAtTheNetwork() {
        for (reason in listOf("UnknownHostException: Unable to resolve host", "SocketTimeoutException: timeout", "ConnectException: Failed to connect")) {
            val g = LiveErrorAdvisor.fromClose(-1, reason, CloseKind.TRANSIENT, attempts = 6)
            assertEquals(reason, GuidanceAction.OPEN_NETWORK_SETTINGS, g.action)
            assertTrue(g.canRetry)
            assertTrue(g.explanation.contains("6 times"))
        }
    }

    @Test fun serverTroubleSaysItIsOnGooglesSide() {
        assertTrue(LiveErrorAdvisor.fromClose(1011, "internal error", CloseKind.TRANSIENT).explanation.contains("Google's side"))
    }

    @Test fun unknownErrorsStillShowTheRawDetails() {
        val g = LiveErrorAdvisor.fromClose(4242, "weird", CloseKind.TRANSIENT)
        assertTrue(g.technical.contains("4242") && g.technical.contains("weird"))
        assertTrue(g.canRetry)
    }

    @Test fun missingKeyAndMicrophoneHaveActions() {
        assertEquals(GuidanceAction.OPEN_AI_STUDIO, LiveErrorAdvisor.missingKey().action)
        assertEquals(GuidanceAction.OPEN_APP_SETTINGS, LiveErrorAdvisor.microphone("Microphone permission is not granted.").action)
    }
}
