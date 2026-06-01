package de.seuhd.ktcodingagent.tools

import de.seuhd.ktcodingagent.io.Workspace
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive
import java.nio.file.Files

/**
 * Sub-exercise (a): implement [execute].
 *
 * - Read required "path" and "content" args.
 * - Resolve via workspace.resolveSandboxed(...). If the target is an existing directory, return an error.
 * - Create parent directories as needed; write the content as UTF-8.
 * - Return ToolResult("wrote <relpath> (<n> chars)").
 *
 * See ToolsTest for the contract.
 */
class WriteFileTool(private val workspace: Workspace) : Tool {
    override val name: String = "write_file"
    override val description: String = "Write a text file."
    override val schema: Map<String, String> = mapOf(
        "path" to "str",
        "content" to "str"
    )
    override val risky: Boolean = true

    override fun execute(args: JsonObject): ToolResult {
        val pathPrim = args["path"] as? JsonPrimitive
        val pathStr = if (pathPrim?.isString == true) pathPrim.content else null
        if (pathStr == null) {
            return ToolResult.error("missing or non-string required argument: path")
        }

        val contentPrim = args["content"] as? JsonPrimitive
        val content = if (contentPrim?.isString == true) contentPrim.content else null
        if (content == null) {
            return ToolResult.error("missing or non-string required argument: content")
        }

        val path = workspace.resolveSandboxed(pathStr)

        if (Files.exists(path) && Files.isDirectory(path)) {
            return ToolResult.error("target is an existing directory: $pathStr")
        }

        try {
            Files.createDirectories(path.parent)
            Files.writeString(path, content)
        } catch (e: SecurityException) {
            throw e
        } catch (e: Exception) {
            return ToolResult.error("error writing file: ${e.message}")
        }

        return ToolResult("wrote $pathStr (${content.length} chars)")
    }
}
