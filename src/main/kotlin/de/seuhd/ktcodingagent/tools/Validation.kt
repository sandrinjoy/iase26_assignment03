package de.seuhd.ktcodingagent.tools

import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.intOrNull

/** Thrown by [Validation.validate] when a tool's arguments are missing or ill-typed. */
class ToolValidationException(message: String) : RuntimeException(message)

/**
 * Argument validation for the built-in tools.
 *
 * Called by [ToolRegistry.dispatch] before the tool is executed, so a tool's `execute` can
 * assume well-formed input. Every reader uses safe casts (`as? JsonPrimitive`) and never
 * touches `?.jsonPrimitive`, so a model that emits `{"path": {...}}` produces a clean
 * [ToolValidationException] instead of an internal cast error.
 */
object Validation {
    fun validate(name: String, args: JsonObject) {
        when (name) {
            "list_files" -> Unit // path is optional and defaults to "."
            "read_file" -> {
                val path = stringArg(args, "path")
                    ?: throw ToolValidationException("invalid arguments for read_file: missing or non-string required argument: path")
                if (path.isBlank()) throw ToolValidationException("invalid arguments for read_file: path must not be empty")
                val start = intArg(args, "start") ?: 1
                val end = intArg(args, "end") ?: 200
                if (start !in 1..end) throw ToolValidationException("invalid arguments for read_file: invalid line range")
            }
            "write_file" -> {
                val path = stringArg(args, "path")
                    ?: throw ToolValidationException("invalid arguments for write_file: missing or non-string required argument: path")
                if (path.isBlank()) throw ToolValidationException("invalid arguments for write_file: path must not be empty")
                if (!args.containsKey("content")) {
                    throw ToolValidationException("invalid arguments for write_file: missing required argument: content")
                }
                if (stringArg(args, "content") == null) {
                    throw ToolValidationException("invalid arguments for write_file: content must be a string")
                }
            }
        }
    }

    /**
     * Canonical example tool call for [name], used as a hint in validation error messages
     * so the model can self-correct on the next turn.
     */
    fun toolCallExample(name: String): String = when (name) {
        "list_files" -> """<tool>{"name":"list_files","args":{"path":"."}}</tool>"""
        "read_file" -> """<tool>{"name":"read_file","args":{"path":"README.md","start":1,"end":80}}</tool>"""
        "write_file" -> """<tool>{"name":"write_file","args":{"path":"hello.txt","content":"hi\n"}}</tool>"""
        else -> ""
    }

    private fun stringArg(args: JsonObject, key: String): String? {
        val primitive = args[key] as? JsonPrimitive ?: return null
        return if (primitive.isString) primitive.content else null
    }

    private fun intArg(args: JsonObject, key: String): Int? =
        (args[key] as? JsonPrimitive)?.intOrNull
}
