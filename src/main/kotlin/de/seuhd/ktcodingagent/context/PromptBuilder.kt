package de.seuhd.ktcodingagent.context

import de.seuhd.ktcodingagent.session.Session
import de.seuhd.ktcodingagent.tools.Tool
import de.seuhd.ktcodingagent.session.HistoryEntry

const val MAX_HISTORY = 12_000

// ...existing code...

/**
 * Sub-exercise (b): implement [build].
 *
 * The full prompt is:
 *   prefix + "\n\n" + memoryText + "\n\nTranscript:\n" + historyText + "\n\nCurrent user request:\n" + userMessage
 *
 * The stable prefix (built once at construction; byte-identical across calls):
 *   <promptPreamble>
 *
 *   Tools:
 *   - <name>(<schema fields joined by ", ">) [safe|approval required] <description>
 *   ... (one line per tool)
 *
 *   Valid response examples:
 *   <tool>{"name":"list_files","args":{"path":"."}}</tool>
 *   <tool>{"name":"read_file","args":{"path":"README.md","start":1,"end":80}}</tool>
 *   <tool>{"name":"write_file","args":{"path":"hello.txt","content":"hi\n"}}</tool>
 *   <final>Done.</final>
 *
 *   <workspace.render()>
 *
 * The memory text:
 *   Memory:
 *   - task: <task or "->">
 *   - files: <comma-separated paths or "->">
 *   - notes:
 *   - <note>
 *   ...
 *
 * The transcript text:
 *   - "- empty" when history is empty
 *   - otherwise, one or two lines per entry:
 *     ToolEntry      -> "[tool:<name>] <args as compact JSON>" then clipped content
 *     UserEntry      -> "[user] <clipped content>"
 *     AssistantEntry -> "[assistant] <clipped content>"
 *   - recent window: entries with index >= max(0, history.size - 6) use limit 900;
 *     older entries use 180 (tool) or 220 (user/assistant)
 *   - final transcript clipped to MAX_HISTORY = 12000 chars
 *
 * See PromptBuilderTest for the contract.
 */
class PromptBuilder(
    private val promptPreamble: String,
    private val tools: List<Tool>,
    private val workspace: WorkspaceContext
) {
    private val cachedPrefix: String = buildPrefix()

    fun build(session: Session, userMessage: String): String {
        val memoryText = buildMemoryText(session)
        val historyText = buildHistoryText(session)
        return "$cachedPrefix\n\n$memoryText\n\nTranscript:\n$historyText\n\nCurrent user request:\n$userMessage"
    }

    fun prefix(): String = cachedPrefix

    private fun buildPrefix(): String {
        val toolsSection = tools.joinToString("\n") { tool ->
            val riskLevel = if (tool.risky) "approval required" else "safe"
            val schemaStr = tool.schema.entries.joinToString(", ") { (name, type) -> "$name:$type" }
            "- ${tool.name}($schemaStr) [$riskLevel] ${tool.description}"
        }

        return buildString {
            appendLine(promptPreamble)
            appendLine()
            appendLine("Tools:")
            appendLine(toolsSection)
            appendLine()
            appendLine("Valid response examples:")
            appendLine("""<tool>{"name":"list_files","args":{"path":"."}}</tool>""")
            appendLine("""<tool>{"name":"read_file","args":{"path":"README.md","start":1,"end":80}}</tool>""")
            appendLine("""<tool>{"name":"write_file","args":{"path":"hello.txt","content":"hi\n"}}</tool>""")
            appendLine("""<final>Done.</final>""")
            appendLine()
            append(workspace.render())
        }
    }

    private fun buildMemoryText(session: Session): String {
        val memory = session.memory
        val task = if (memory.task.isEmpty()) "->" else memory.task
        val files = if (memory.files.isEmpty()) "->" else memory.files.joinToString(", ")

        return buildString {
            appendLine("Memory:")
            appendLine("- task: $task")
            appendLine("- files: $files")
            appendLine("- notes:")
            for (note in memory.notes) {
                appendLine("- $note")
            }
        }
    }

    private fun buildHistoryText(session: Session): String {
        if (session.history.isEmpty()) {
            return "- empty"
        }

        val result = StringBuilder()
        val recentStartIdx = maxOf(0, session.history.size - 6)

        for ((idx, entry) in session.history.withIndex()) {
            val isRecent = idx >= recentStartIdx
            val clipLimit = if (isRecent) 900 else when (entry) {
                is HistoryEntry.ToolEntry -> 180
                else -> 220
            }

            when (entry) {
                is HistoryEntry.ToolEntry -> {
                    val argsJson = entry.args.toString()
                    result.append("- [tool:${entry.name}] $argsJson\n")
                    result.append(clipText(entry.content, clipLimit)).append("\n")
                }
                is HistoryEntry.UserEntry -> {
                    result.append("- [user] ").append(clipText(entry.content, clipLimit)).append("\n")
                }
                is HistoryEntry.AssistantEntry -> {
                    result.append("- [assistant] ").append(clipText(entry.content, clipLimit)).append("\n")
                }
            }
        }

        val fullHistory = result.toString()
        if (fullHistory.length <= MAX_HISTORY) {
            return fullHistory.trimEnd()
        }

        return fullHistory.take(MAX_HISTORY) + "\n[history truncated]"
    }

    private fun clipText(text: String, limit: Int): String {
        return if (text.length <= limit) {
            text.replace('\n', ' ')
        } else {
            text.take(limit).replace('\n', ' ') + "...[${text.length - limit} chars]"
        }
    }
}
