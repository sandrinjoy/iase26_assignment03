package de.seuhd.ktcodingagent

import de.seuhd.ktcodingagent.session.HistoryEntry
import org.junit.jupiter.api.io.TempDir
import java.nio.file.Files
import java.nio.file.Path
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue
import kotlin.test.assertFalse

/**
 * Sub-exercise (d): add at least three scripted scenarios in this class that exercise
 * [Agent] via [StubModelClient], beyond the twelve cases provided in [AgentTest].
 *
 * Suggested scenarios:
 *   1. A sequence where the agent receives a tool-error response and surfaces it.
 *   2. A sequence where the model attempts a path-safety violation that the
 *      sandbox rejects inside the loop.
 *   3. One scenario of your own design.
 *
 * Use [buildAgentForTest] from [AgentTestSupport] to wire up an Agent with your
 * scripted StubModelClient outputs, then assert on `agent.session.history` and the
 * returned final answer.
 *
 * This class ships empty so it does not contribute to the failing-test count. JUnit
 * picks up no tests until you add @Test methods.
 */
class ScriptedAgentTest {

    @Test
    fun toolErrorResponseIsHandled(@TempDir root: Path) {
        // Scenario 1: Tool error - agent calls read_file on non-existent file, gets error, then final
        Files.writeString(root.resolve("hello.txt"), "world")

        val scripted = listOf(
            """<tool>{"name":"read_file","args":{"path":"nonexistent.txt"}}</tool>""",
            """<final>The file does not exist, but I found hello.txt is available.</final>"""
        )

        val (agent, _) = buildAgentForTest(root, scripted)
        val answer = agent.ask("Try to read a file that doesn't exist")

        assertEquals(
            "The file does not exist, but I found hello.txt is available.",
            answer
        )

        // Verify tool entry was recorded with isError=true
        val toolEntry = agent.session.history
            .filterIsInstance<HistoryEntry.ToolEntry>()
            .firstOrNull { it.name == "read_file" }
        assertTrue(toolEntry != null && toolEntry.isError)
    }

    @Test
    fun pathSafetyRejectionInsideLoop(@TempDir root: Path) {
        // Scenario 2: Agent attempts path traversal (../), which gets blocked by sandbox
        Files.writeString(root.resolve("secret.txt"), "confidential")

        val scripted = listOf(
            """<tool>{"name":"read_file","args":{"path":"../secret.txt"}}</tool>""",
            """<final>I attempted to escape but was blocked.</final>"""
        )

        val (agent, _) = buildAgentForTest(root, scripted)
        val answer = agent.ask("Try to escape the sandbox")

        // Agent should have received security error and recovered
        val toolEntry = agent.session.history
            .filterIsInstance<HistoryEntry.ToolEntry>()
            .firstOrNull { it.name == "read_file" }
        assertTrue(toolEntry != null && toolEntry.isError)
        assertTrue("path escapes workspace" in toolEntry.content)
    }

    @Test
    fun writeThenReadSequence(@TempDir root: Path) {
        // Scenario 3: Custom scenario - agent writes a file then reads it back
        val scripted = listOf(
            """<tool>{"name":"write_file","args":{"path":"created.txt","content":"Hello World"}}</tool>""",
            """<tool>{"name":"read_file","args":{"path":"created.txt","start":1,"end":1}}</tool>""",
            """<final>Successfully wrote and read the file: Hello World</final>"""
        )

        val (agent, _) = buildAgentForTest(root, scripted)
        val answer = agent.ask("Create and read a file")

        assertEquals("Successfully wrote and read the file: Hello World", answer)

        // Verify both operations were recorded successfully
        val toolEntries = agent.session.history.filterIsInstance<HistoryEntry.ToolEntry>()
        assertEquals(2, toolEntries.size)
        assertFalse(toolEntries[0].isError) // write_file should succeed
        assertFalse(toolEntries[1].isError) // read_file should succeed

        // Verify the file actually exists
        assertTrue(Files.exists(root.resolve("created.txt")))
        assertEquals("Hello World", Files.readString(root.resolve("created.txt")))
    }
}
