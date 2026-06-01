package de.seuhd.ktcodingagent.tools

import de.seuhd.ktcodingagent.io.Workspace
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive
import java.nio.file.Files

/**
 * Sub-exercise (a): implement [execute].
 *
 * - Read the optional "path" arg (default ".") and resolve it via workspace.resolveSandboxed(...).
 * - If not a directory, return an error ToolResult.
 * - List entries (directories first, then files; alphabetic). Hide IGNORED_PATH_NAMES.
 * - Format each entry as "[D] relpath" or "[F] relpath". Return "(empty)" if none.
 *
 * See ToolsTest for the contract.
 */
private val IGNORED_PATH_NAMES = setOf(".git", ".kt-coding-agent", "build", ".gradle", ".idea")

class ListFilesTool(private val workspace: Workspace) : Tool {
    override val name: String = "list_files"
    override val description: String = "List files in the workspace."
    override val schema: Map<String, String> = mapOf("path" to "str='.'")
    override val risky: Boolean = false

    override fun execute(args: JsonObject): ToolResult {
        val pathStr = (args["path"] as? JsonPrimitive)?.takeIf { it.isString }?.content ?: "."
        val path = workspace.resolveSandboxed(pathStr)

        if (!Files.isDirectory(path)) {
            return ToolResult.error("not a directory: $pathStr")
        }

        val entries = mutableListOf<String>()
        try {
            Files.list(path).use { stream ->
                stream
                    .map { it.fileName.toString() }
                    .filter { it !in IGNORED_PATH_NAMES }
                    .forEach { entries.add(it) }
            }
        } catch (e: SecurityException) {
            throw e
        } catch (e: Exception) {
            return ToolResult.error("error listing directory: ${e.message}")
        }

        if (entries.isEmpty()) {
            return ToolResult("(empty)")
        }

        entries.sort()
        val dirs = entries.filter { Files.isDirectory(path.resolve(it)) }
        val files = entries.filter { !Files.isDirectory(path.resolve(it)) }

        val result = (dirs.sorted().map { "[D] $it" } + files.sorted().map { "[F] $it" })
            .joinToString("\n")
        return ToolResult(result)
    }
}
