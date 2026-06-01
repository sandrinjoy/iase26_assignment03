package de.seuhd.ktcodingagent.parse

import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonObject

/**
 * Sub-exercise (c): the response parser.
 *
 * Implement [parse] to return one of:
 *   - Parsed.Tool(name, args)   when the raw text contains <tool>{json}</tool>
 *   - Parsed.Final(text)        when the raw text contains <final>...</final>,
 *                               or contains neither tag but is non-empty
 *   - Parsed.Retry(notice)      on empty input, empty <final>, malformed JSON
 *                               inside <tool>, missing "name", or non-object "args"
 *
 * Retry notices follow the form:
 *   "Runtime notice: <problem>. Reply with a valid <tool> call or a non-empty <final> answer."
 *
 * See ResponseParserTest for the contract.
 */
object ResponseParser {
    fun parse(raw: String): Parsed {
        val trimmed = raw.trim()

        if (trimmed.isBlank()) {
            return Parsed.Retry("Runtime notice: empty response. Reply with a valid <tool> call or a non-empty <final> answer.")
        }

        // Try to extract <tool>...</tool>
        val toolMatch = Regex("<tool>(.*?)</tool>", RegexOption.DOT_MATCHES_ALL).find(trimmed)
        if (toolMatch != null) {
            val jsonStr = toolMatch.groupValues[1].trim()
            return try {
                val parsed = Json.decodeFromString<JsonObject>(jsonStr)
                val nameElement = parsed["name"]
                val name = (nameElement as? kotlinx.serialization.json.JsonPrimitive)
                    ?.takeIf { it.isString }?.content
                if (name == null) {
                    return Parsed.Retry("Runtime notice: missing or non-string tool name. Reply with a valid <tool> call or a non-empty <final> answer.")
                }
                val args = parsed["args"] as? JsonObject
                if (args == null) {
                    return Parsed.Retry("Runtime notice: 'args' must be a JSON object. Reply with a valid <tool> call or a non-empty <final> answer.")
                }
                Parsed.Tool(name, args)
            } catch (e: Exception) {
                Parsed.Retry("Runtime notice: malformed tool JSON in <tool> tag: ${e.message}. Reply with a valid <tool> call or a non-empty <final> answer.")
            }
        }

        // Try to extract <final>...</final>
        val finalMatch = Regex("<final>(.*?)</final>", RegexOption.DOT_MATCHES_ALL).find(trimmed)
        if (finalMatch != null) {
            val text = finalMatch.groupValues[1].trim()
            if (text.isBlank()) {
                return Parsed.Retry("Runtime notice: empty <final> answer. Reply with a valid <tool> call or a non-empty <final> answer.")
            }
            return Parsed.Final(text)
        }

        // No tags, but non-empty response is treated as a final answer
        return Parsed.Final(trimmed)
    }
}
