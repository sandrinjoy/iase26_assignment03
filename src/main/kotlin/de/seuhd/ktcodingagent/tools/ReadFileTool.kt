package de.seuhd.ktcodingagent.tools

import de.seuhd.ktcodingagent.io.Workspace
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.intOrNull
import java.nio.file.Files

/**
 * Sub-exercise (a): implement [execute].
 *
 * - Read required "path" arg and optional "start" (default 1) and "end" (default 200).
 * - Resolve via workspace.resolveSandboxed(...). If not a regular file, return an error.
 * - Validate the range; return an error on invalid range.
 * - Read the file, slice lines [start..end], and format each line as
 *   "%4d: %s" prefixed by a "# <relpath>" header.
 *
 * See ToolsTest for the contract.
 */
class ReadFileTool(private val workspace: Workspace) : Tool {
    override val name: String = "read_file"
    override val description: String = "Read a UTF-8 file by line range."
    override val schema: Map<String, String> = mapOf(
        "path" to "str",
        "start" to "int=1",
        "end" to "int=200"
    )
    override val risky: Boolean = false

    override fun execute(args: JsonObject): ToolResult {
        val pathPrim = args["path"] as? JsonPrimitive
        val pathStr = if (pathPrim?.isString == true) pathPrim.content else null
        if (pathStr == null) {
            return ToolResult.error("missing or non-string required argument: path")
        }

        val start = (args["start"] as? JsonPrimitive)?.intOrNull ?: 1
        val end = (args["end"] as? JsonPrimitive)?.intOrNull ?: 200

        val path = workspace.resolveSandboxed(pathStr)

        if (!Files.isRegularFile(path)) {
            return ToolResult.error("not a regular file: $pathStr")
        }

        val lines = try {
            Files.readAllLines(path)
        } catch (e: SecurityException) {
            throw e
        } catch (e: Exception) {
            return ToolResult.error("error reading file: ${e.message}")
        }

        if (start < 1 || start > end) {
            return ToolResult.error("invalid line range: start=$start, end=$end")
        }

        val sliced = lines.drop(start - 1).take(end - start + 1)
        val result = buildString {
            append("# $pathStr")
            for ((idx, line) in sliced.withIndex()) {
                append("\n${(start + idx).toString().padStart(4)}: $line")
            }
        }

        return ToolResult(result)
    }
}
