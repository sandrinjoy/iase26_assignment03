package de.seuhd.ktcodingagent.model

import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable
import kotlinx.serialization.encodeToString
import kotlinx.serialization.json.Json
import kotlinx.serialization.decodeFromString
import java.net.http.HttpClient
import java.net.http.HttpRequest
import java.net.http.HttpResponse
import java.time.Duration
import java.nio.charset.StandardCharsets

// ...existing code...

/**
 * Sub-exercise (b): implement [complete] and [checkAvailability].
 *
 * [complete]:
 *   - POST {host}/api/generate with body:
 *       {"model": modelName, "prompt": prompt, "stream": false, "raw": false, "think": false,
 *        "options": {"num_predict": maxNewTokens, "temperature": temperature, "top_p": topP}}
 *     (Use kotlinx.serialization. Note the snake_case keys.)
 *   - Send via httpClient (injected for testability).
 *   - On IOException, throw OllamaUnreachableException(host, modelName, cause).
 *   - On HTTP != 2xx, throw RuntimeException with status and body.
 *   - On success, parse {"response": "..."} and return that field (empty string if null).
 *
 * [checkAvailability]:
 *   - GET {host}/api/tags.
 *   - On IOException, return AvailabilityCheck.OllamaUnreachable("Ollama is not running. Start it with `ollama serve`.").
 *   - On HTTP != 2xx, return OllamaUnreachable with a message containing the status and body.
 *   - On success, parse {"models": [{"name": "..."}, ...]}; if modelName (or modelName:latest) is present,
 *     return AvailabilityCheck.Ready, else AvailabilityCheck.ModelMissing("Model 'X' is not pulled. Run `ollama pull X`.").
 *
 * See OllamaModelClientUnitTest for the contract.
 */
class OllamaModelClient(
    private val modelName: String,
    private val host: String = DEFAULT_HOST,
    private val temperature: Double = 0.2,
    private val topP: Double = 0.9,
    private val timeout: Duration = Duration.ofSeconds(300),
    private val httpClient: HttpClient = HttpClient.newBuilder().connectTimeout(Duration.ofSeconds(10)).build()
) : ModelClient {

    override fun complete(prompt: String, maxNewTokens: Int): String {
        val requestBody = GenerateRequest(
            model = modelName,
            prompt = prompt,
            stream = false,
            raw = false,
            think = false,
            options = GenerateOptions(
                numPredict = maxNewTokens,
                temperature = temperature,
                topP = topP
            )
        )

        val jsonBody = Json.encodeToString(requestBody)
        val request = HttpRequest.newBuilder()
            .uri(java.net.URI("$host/api/generate"))
            .timeout(timeout)
            .header("Content-Type", "application/json")
            .POST(HttpRequest.BodyPublishers.ofString(jsonBody))
            .build()

        return try {
            val response = httpClient.send(request, HttpResponse.BodyHandlers.ofString())
            if (response.statusCode() !in 200..299) {
                throw RuntimeException("HTTP ${response.statusCode()}: ${response.body()}")
            }
            val parsed = Json.decodeFromString<GenerateResponse>(response.body())
            parsed.response ?: ""
        } catch (e: java.io.IOException) {
            throw OllamaUnreachableException(host, modelName, e)
        }
    }

    fun checkAvailability(): AvailabilityCheck {
        val request = HttpRequest.newBuilder()
            .uri(java.net.URI("$host/api/tags"))
            .timeout(timeout)
            .GET()
            .build()

        return try {
            val response = httpClient.send(request, HttpResponse.BodyHandlers.ofString())
            if (response.statusCode() !in 200..299) {
                return AvailabilityCheck.OllamaUnreachable(
                    "HTTP ${response.statusCode()}: ${response.body()}"
                )
            }
            val parsed = Json.decodeFromString<TagsResponse>(response.body())
            val modelNames = parsed.models.map { it.name }
            if (modelName in modelNames || "$modelName:latest" in modelNames) {
                AvailabilityCheck.Ready
            } else {
                AvailabilityCheck.ModelMissing(
                    "Model '$modelName' is not pulled. Run `ollama pull $modelName`."
                )
            }
        } catch (e: java.io.IOException) {
            AvailabilityCheck.OllamaUnreachable(
                "Ollama is not running. Start it with `ollama serve`."
            )
        }
    }

    companion object {
        const val DEFAULT_HOST: String = "http://127.0.0.1:11434"
        val JSON: Json = Json {
            ignoreUnknownKeys = true
            isLenient = true
            encodeDefaults = true
        }
    }
}

sealed class AvailabilityCheck {
    object Ready : AvailabilityCheck()
    data class OllamaUnreachable(val message: String) : AvailabilityCheck()
    data class ModelMissing(val message: String) : AvailabilityCheck()
}

class OllamaUnreachableException(host: String, model: String, cause: Throwable?) :
    RuntimeException(
        "Could not reach Ollama. Make sure `ollama serve` is running and the model is available.\nHost: $host\nModel: $model",
        cause
    )

@Serializable
internal data class GenerateRequest(
    val model: String,
    val prompt: String,
    val stream: Boolean,
    val raw: Boolean,
    val think: Boolean,
    val options: GenerateOptions
)

@Serializable
internal data class GenerateOptions(
    @SerialName("num_predict") val numPredict: Int,
    val temperature: Double,
    @SerialName("top_p") val topP: Double
)

@Serializable
internal data class GenerateResponse(
    val response: String? = null,
    val error: String? = null
)

@Serializable
internal data class TagsResponse(val models: List<OllamaTag> = emptyList())

@Serializable
internal data class OllamaTag(val name: String)
