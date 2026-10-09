package com.orbit.app.integrations.gemini

import java.io.IOException
import java.net.HttpURLConnection
import java.net.SocketTimeoutException
import java.net.URL
import java.net.URLEncoder
import java.net.UnknownHostException
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import org.json.JSONException
import org.json.JSONObject

interface GeminiApiClient {
    suspend fun generateJson(
        apiKey: String,
        modelId: String,
        prompt: String,
        maxOutputTokens: Int = 256,
    ): GeminiApiResult

    suspend fun testConnection(apiKey: String, modelId: String): GeminiApiResult
}

sealed interface GeminiApiResult {
    data class Success(
        val text: String,
        val modelId: String,
    ) : GeminiApiResult

    data class Failure(
        val error: GeminiApiError,
    ) : GeminiApiResult
}

data class GeminiApiError(
    val kind: GeminiApiErrorKind,
    val userMessage: String,
)

enum class GeminiApiErrorKind {
    MissingKey,
    BadKey,
    RateLimited,
    Timeout,
    NoInternet,
    InvalidResponse,
    SafetyBlocked,
    Server,
    ModelNotFound,
    Unknown,
}

class HttpGeminiApiClient : GeminiApiClient {
    override suspend fun testConnection(apiKey: String, modelId: String): GeminiApiResult =
        generateJson(
            apiKey = apiKey,
            modelId = modelId,
            prompt = """Return exactly this JSON: {"ok": true}""",
            // Reasoning-capable models may spend initial tokens on hidden thought parts
            // before returning the compact JSON acknowledgement.
            maxOutputTokens = 128,
        ).let { result ->
            when (result) {
                is GeminiApiResult.Success -> {
                    if (GeminiJsonValidator.isConnectionJson(result.text)) result
                    else GeminiApiResult.Failure(geminiError(GeminiApiErrorKind.InvalidResponse))
                }

                is GeminiApiResult.Failure -> result
            }
        }

    override suspend fun generateJson(
        apiKey: String,
        modelId: String,
        prompt: String,
        maxOutputTokens: Int,
    ): GeminiApiResult = withContext(Dispatchers.IO) {
        val cleanKey = apiKey.trim()
        if (cleanKey.isBlank()) {
            return@withContext GeminiApiResult.Failure(geminiError(GeminiApiErrorKind.MissingKey))
        }

        runCatching {
            val connection = URL(endpointFor(modelId)).openConnection() as HttpURLConnection
            connection.requestMethod = "POST"
            connection.connectTimeout = TimeoutMillis
            connection.readTimeout = TimeoutMillis
            connection.doOutput = true
            connection.setRequestProperty("Content-Type", "application/json")
            // The key travels in the header instead of the URL so it cannot leak
            // into request logs or proxies.
            connection.setRequestProperty("x-goog-api-key", cleanKey)
            connection.outputStream.use { output ->
                output.write(requestBody(prompt, maxOutputTokens).toByteArray(Charsets.UTF_8))
            }

            val statusCode = connection.responseCode
            val responseText = if (statusCode in 200..299) {
                connection.inputStream.bufferedReader().use { it.readText() }
            } else {
                connection.errorStream?.bufferedReader()?.use { it.readText() }.orEmpty()
            }
            connection.disconnect()

            if (statusCode !in 200..299) {
                return@withContext GeminiApiResult.Failure(geminiError(geminiErrorKindFor(statusCode, responseText)))
            }

            parseResponse(responseText, modelId)
        }.getOrElse { exception ->
            GeminiApiResult.Failure(errorForException(exception))
        }
    }

    private fun endpointFor(modelId: String): String {
        val encodedModel = URLEncoder.encode(modelId.trim(), "UTF-8")
        return "$BaseUrl/$encodedModel:generateContent"
    }

    private fun requestBody(prompt: String, maxOutputTokens: Int): String = JSONObject()
        .put(
            "contents",
            org.json.JSONArray()
                .put(
                    JSONObject()
                        .put(
                            "parts",
                            org.json.JSONArray()
                                .put(JSONObject().put("text", prompt)),
                        ),
                ),
        )
        .put(
            "generationConfig",
            JSONObject()
                .put("temperature", 0)
                .put("maxOutputTokens", maxOutputTokens.coerceIn(1, 2048))
                .put("responseMimeType", "application/json"),
        )
        .toString()

    private fun parseResponse(responseText: String, modelId: String): GeminiApiResult {
        val json = runCatching { JSONObject(responseText) }.getOrNull()
            ?: return GeminiApiResult.Failure(geminiError(GeminiApiErrorKind.InvalidResponse))
        val promptFeedback = json.optJSONObject("promptFeedback")
        if (!promptFeedback?.optString("blockReason").isNullOrBlank()) {
            return GeminiApiResult.Failure(geminiError(GeminiApiErrorKind.SafetyBlocked))
        }

        val candidate = json.optJSONArray("candidates")?.optJSONObject(0)
            ?: return GeminiApiResult.Failure(geminiError(GeminiApiErrorKind.InvalidResponse))
        if (candidate.optString("finishReason") == "SAFETY") {
            return GeminiApiResult.Failure(geminiError(GeminiApiErrorKind.SafetyBlocked))
        }

        val text = extractGeminiResponseText(candidate).orEmpty()

        return if (text.isNotBlank()) {
            GeminiApiResult.Success(text = text, modelId = modelId)
        } else {
            GeminiApiResult.Failure(geminiError(GeminiApiErrorKind.InvalidResponse))
        }
    }

    private fun errorForException(exception: Throwable): GeminiApiError = when (exception) {
        is SocketTimeoutException -> geminiError(GeminiApiErrorKind.Timeout)
        is UnknownHostException -> geminiError(GeminiApiErrorKind.NoInternet)
        is IOException -> geminiError(GeminiApiErrorKind.NoInternet)
        is JSONException -> geminiError(GeminiApiErrorKind.InvalidResponse)
        else -> geminiError(GeminiApiErrorKind.Unknown)
    }

    private companion object {
        const val BaseUrl = "https://generativelanguage.googleapis.com/v1beta/models"
        const val TimeoutMillis = 15_000
    }
}

/**
 * Maps an HTTP failure to a user-meaningful kind. Gemini reports an invalid key as
 * HTTP 400 with reason API_KEY_INVALID, and an unknown model as HTTP 404.
 */
internal fun geminiErrorKindFor(statusCode: Int, errorBody: String): GeminiApiErrorKind {
    val error = GeminiJson.parseObject(errorBody)?.optJSONObject("error")
    val status = error?.optString("status").orEmpty()
    val message = error?.optString("message").orEmpty().lowercase()
    val reasons = buildList {
        val details = error?.optJSONArray("details")
        for (index in 0 until (details?.length() ?: 0)) {
            details?.optJSONObject(index)?.optString("reason")?.takeIf { it.isNotBlank() }?.let(::add)
        }
    }
    return when {
        "API_KEY_INVALID" in reasons || "API_KEY_INVALID" in errorBody ||
            (statusCode == 400 && "api key" in message) -> GeminiApiErrorKind.BadKey
        statusCode == 401 || statusCode == 403 || status == "PERMISSION_DENIED" ||
            status == "UNAUTHENTICATED" -> GeminiApiErrorKind.BadKey
        statusCode == 404 || status == "NOT_FOUND" -> GeminiApiErrorKind.ModelNotFound
        statusCode == 429 || status == "RESOURCE_EXHAUSTED" -> GeminiApiErrorKind.RateLimited
        statusCode in 500..599 -> GeminiApiErrorKind.Server
        else -> GeminiApiErrorKind.Unknown
    }
}

internal fun extractGeminiResponseText(candidate: JSONObject): String? {
    val parts = candidate.optJSONObject("content")?.optJSONArray("parts") ?: return null
    for (index in parts.length() - 1 downTo 0) {
        val part = parts.optJSONObject(index) ?: continue
        if (part.optBoolean("thought", false)) continue
        val text = part.optString("text").trim()
        if (text.isNotBlank()) return text
    }
    return null
}

fun geminiError(kind: GeminiApiErrorKind): GeminiApiError {
    val message = when (kind) {
        GeminiApiErrorKind.MissingKey -> "Add a Gemini API key first. Local mode still works."
        GeminiApiErrorKind.BadKey -> "Gemini could not use this key. Local mode still works."
        GeminiApiErrorKind.RateLimited -> "Rate limit reached. LUMA will use local mode."
        GeminiApiErrorKind.Timeout -> "Gemini took too long. Local mode still works."
        GeminiApiErrorKind.NoInternet -> "No internet. Local mode still works."
        GeminiApiErrorKind.InvalidResponse -> "Gemini replied in a format LUMA could not use."
        GeminiApiErrorKind.SafetyBlocked -> "Gemini blocked that test. Local mode still works."
        GeminiApiErrorKind.Server -> "Gemini is unavailable right now. Local mode still works."
        GeminiApiErrorKind.ModelNotFound -> "Gemini does not know this model name. Local mode still works."
        GeminiApiErrorKind.Unknown -> "Gemini connection did not finish. Local mode still works."
    }
    return GeminiApiError(kind = kind, userMessage = message)
}
