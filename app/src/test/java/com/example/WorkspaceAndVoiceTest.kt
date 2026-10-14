package com.example

import com.example.core.files.WorkspacePaths
import com.example.core.gemini.ConversationContext
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class WorkspaceAndVoiceTest {

    @Test fun pathsAreSplitAndNormalised() {
        assertEquals(listOf("site", "index.html"), WorkspacePaths.segments("site/index.html"))
        assertEquals(listOf("a", "b"), WorkspacePaths.segments("/a//b/./"))
        assertEquals(listOf("a", "b"), WorkspacePaths.segments("a\\b"))
        assertEquals(emptyList<String>(), WorkspacePaths.segments(""))
    }

    @Test fun pathsCannotEscapeTheGrantedFolder() {
        assertNull(WorkspacePaths.segments("../secrets"))
        assertNull(WorkspacePaths.segments("a/../../b"))
        assertNull(WorkspacePaths.segments("a/..\\b"))
    }

    @Test fun voiceChangeCarriesTheLastLinesOfConversation() {
        val log = (1..30).map { (if (it % 2 == 0) "assistant" else "user") to "line $it" } +
            ("system" to "Neural core connected") + ("tool" to "Executing x")
        val ctx = ConversationContext.build(log, maxLines = 6)!!
        assertTrue(ctx.contains("line 30") && ctx.contains("line 25"))
        assertTrue("older lines are dropped", !ctx.contains("line 10"))
        assertTrue("system and tool lines are not part of the conversation", !ctx.contains("Neural core"))
        assertTrue(ctx.contains("You: line 30") && ctx.contains("User: line 29"))
    }

    @Test fun nothingToCarryOverMeansNoBlock() {
        assertNull(ConversationContext.build(listOf("system" to "x")))
        assertNotNull(ConversationContext.build(listOf("user" to "hi")))
    }
}
