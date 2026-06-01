package de.seuhd.ktcodingagent

import de.seuhd.ktcodingagent.context.PromptBuilder
import de.seuhd.ktcodingagent.model.ModelClient
import de.seuhd.ktcodingagent.parse.Parsed
import de.seuhd.ktcodingagent.parse.ResponseParser
import de.seuhd.ktcodingagent.session.HistoryEntry
import de.seuhd.ktcodingagent.session.Session
import de.seuhd.ktcodingagent.session.SessionStore
import de.seuhd.ktcodingagent.tools.ToolRegistry
import kotlinx.serialization.json.JsonObject
import java.time.LocalDateTime
import java.time.format.DateTimeFormatter

// ...existing code...

/**
 * Sub-exercise (c): the agent loop.
 *
 * Implement [ask] per the loop in the assignment sheet:
 *   - record the user message and persist the session
 *   - on each iteration, build the prompt, call modelClient.complete,
 *     parse the response, and act on the three cases (Tool, Final, Retry)
 *   - bound by maxSteps (tool calls) and maxAttempts = 3 * maxSteps
 *   - on tool calls, call session.memory.recordToolCall(...)
 *   - distinguish the two stop conditions in the final message
 *
 * See AgentTest for the contract.
 */
class Agent(
    private val modelClient: ModelClient,
    private val registry: ToolRegistry,
    private val promptBuilder: PromptBuilder,
    val session: Session,
    private val sessionStore: SessionStore,
    private val maxSteps: Int = 16,
    private val maxNewTokens: Int = 1024,
    private val onToolCall: (name: String, args: JsonObject, content: String, isError: Boolean) -> Unit = { _, _, _, _ -> }
) {
    fun ask(userMessage: String): String {
        val maxAttempts = 3 * maxSteps
        var steps = 0
        var attempts = 0

        // Record the user message
        session.memory.setInitialTask(userMessage)
        session.record(HistoryEntry.UserEntry(userMessage, now()))
        sessionStore.save(session)

        while (attempts < maxAttempts && steps < maxSteps) {
            // Build the prompt
            val prompt = promptBuilder.build(session, userMessage)

            // Call model
            attempts++
            val rawResponse = try {
                modelClient.complete(prompt, maxNewTokens)
            } catch (e: Exception) {
                return "Error calling model: ${e.message}"
            }

            // Parse response
            val parsed = ResponseParser.parse(rawResponse)

            when (parsed) {
                is Parsed.Tool -> {
                    steps++
                    // Dispatch the tool
                    val result = registry.dispatch(parsed.name, parsed.args, session.history)
                    onToolCall(parsed.name, parsed.args, result.content, result.isError)

                    // Record the tool call
                    session.record(
                        HistoryEntry.ToolEntry(
                            parsed.name,
                            parsed.args,
                            result.content,
                            result.isError,
                            now()
                        )
                    )

                    // Record in memory
                    session.memory.recordToolCall(parsed.name, parsed.args, result.content)

                    // Persist
                    sessionStore.save(session)

                    // Check if this is a dedup block (repeated identical tool call)
                    if (result.isError && result.content.contains("repeated identical tool call")) {
                        // Find the most recent successful identical call before this one
                        val priorSuccess = session.history
                            .filterIsInstance<HistoryEntry.ToolEntry>()
                            .lastOrNull {
                                it.name == parsed.name &&
                                it.args == parsed.args &&
                                !it.isError &&
                                it != session.history.last() // exclude the current entry
                            }
                        if (priorSuccess != null) {
                            // Synthesize a final answer from the prior result
                            session.memory.recordFinal(priorSuccess.content)
                            session.record(HistoryEntry.AssistantEntry(priorSuccess.content, now()))
                            sessionStore.save(session)
                            return priorSuccess.content
                        }
                    }
                }
                is Parsed.Final -> {
                    // Record the final answer
                    session.memory.recordFinal(parsed.text)
                    session.record(HistoryEntry.AssistantEntry(parsed.text, now()))
                    sessionStore.save(session)
                    return parsed.text
                }
                is Parsed.Retry -> {
                    // Record the retry notice
                    session.record(HistoryEntry.AssistantEntry(parsed.notice, now()))
                    sessionStore.save(session)
                }
            }
        }

        // Loop terminated without a final answer
        val message = when {
            steps >= maxSteps -> "Stopped after reaching the step limit without a final answer."
            else -> "Stopped after too many malformed model responses without a valid tool call or final answer."
        }

        session.record(HistoryEntry.AssistantEntry(message, now()))
        sessionStore.save(session)
        return message
    }

    private fun now(): String = LocalDateTime.now().format(DateTimeFormatter.ISO_DATE_TIME)

    fun reset() {
        session.history.clear()
        session.memory.clear()
        sessionStore.save(session)
    }
}
