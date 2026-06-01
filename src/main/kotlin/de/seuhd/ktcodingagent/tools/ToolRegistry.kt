package de.seuhd.ktcodingagent.tools

import de.seuhd.ktcodingagent.session.HistoryEntry
import kotlinx.serialization.json.JsonObject

const val MAX_TOOL_OUTPUT = 4_000

/**
 * Sub-exercise (a): implement [dispatch].
 *
 * Pipeline order:
 *   1. Look up the tool by name. Unknown name -> error result.
 *   2. Validate args via Validation.validate(name, args). On failure, return an error
 *      that includes the example from Validation.toolCallExample(name).
 *   3. Reject the call as "repeated identical tool call" if a prior successful ToolEntry
 *      has the same name and identical args, *unless* a successful `write_file` has run
 *      between that prior call and the current one. A `write_file` invalidates the cache
 *      because the workspace state has changed. Identical `write_file` calls (same path,
 *      same content) stay blocked — there is nothing between them.
 *   4. If the tool is risky and the approval gate denies the call, return an error.
 *   5. Execute the tool. Catch SecurityException (path escape) and other exceptions as errors.
 *   6. Clip the resulting content to MAX_TOOL_OUTPUT chars before returning.
 *
 * See ToolRegistryTest for the contract.
 */
class ToolRegistry(
    val tools: List<Tool>,
    private val approvalGate: ApprovalGate = AutoApprove
) {
    private val byName: Map<String, Tool> = tools.associateBy { it.name }

    fun dispatch(name: String, args: JsonObject, history: List<HistoryEntry>): ToolResult {
        // 1. Look up tool by name
        val tool = byName[name] ?: return ToolResult.error("unknown tool '$name'")

        // 2. Validate arguments
        try {
            Validation.validate(name, args)
        } catch (e: ToolValidationException) {
            val example = Validation.toolCallExample(name)
            return ToolResult.error("${e.message}\nExample: $example")
        }

        // 3. Reject repeated identical calls (unless write_file ran between)
        val lastEntry = history
            .filterIsInstance<HistoryEntry.ToolEntry>()
            .lastOrNull { it.name == name && it.args == args }

        if (lastEntry != null) {
            // Check if the last entry was successful (not an error), and if so, check for writes after
            if (!lastEntry.isError) {
                // Check if a successful write_file happened after this entry
                val writeFileAfter = history
                    .filterIsInstance<HistoryEntry.ToolEntry>()
                    .any { it.createdAt > lastEntry.createdAt && it.name == "write_file" && !it.isError }

                if (!writeFileAfter) {
                    return ToolResult.error("repeated identical tool call (already executed with same args)")
                }
            } else {
                // If the last entry was an error (e.g., denied by approval gate), also block it
                return ToolResult.error("repeated identical tool call (already executed with same args)")
            }
        }

        // 4. Check approval gate for risky tools
        if (tool.risky && !approvalGate.approve(name, args)) {
            return ToolResult.error("tool call denied by approval gate")
        }

        // 5. Execute the tool
        val result = try {
            tool.execute(args)
        } catch (e: SecurityException) {
            ToolResult.error(e.message ?: "Security error: ${e::class.simpleName}")
        } catch (e: Exception) {
            ToolResult.error("tool execution failed: ${e.message}")
        }

        // 6. Clip output to MAX_TOOL_OUTPUT
        if (result.content.length > MAX_TOOL_OUTPUT) {
            val clipped = result.content.take(MAX_TOOL_OUTPUT)
            return ToolResult("$clipped\n...[truncated ${result.content.length - MAX_TOOL_OUTPUT} chars]", result.isError)
        }

        return result
    }
}
