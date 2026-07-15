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
    Unknown,
}

class HttpGeminiApiClient : GeminiApiClient {
    override suspend fun testConnection(apiKey: String, modelId: String): GeminiApiResult =
        generateJson(
            apiKey = apiKey,
            modelId = modelId,
            prompt = """Return exactly this JSON: {"ok": true}""",
            maxOutputTokens = 32,
        ).let { result ->
            when (result) {
                is GeminiApiResult.Success -> {
                    if (GeminiJsonValidator.isConnectionOk(result.text)) result
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
            val connection = URL(endpointFor(modelId, cleanKey)).openConnection() as HttpURLConnection
            connection.requestMethod = "POST"
            connection.connectTimeout = TimeoutMillis
            connection.readTimeout = TimeoutMillis
            connection.doOutput = true
            connection.setRequestProperty("Content-Type", "application/json")
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
                return@withContext GeminiApiResult.Failure(errorForStatus(statusCode))
            }

            parseResponse(responseText, modelId)
        }.getOrElse { exception ->
            GeminiApiResult.Failure(errorForException(exception))
        }
    }

    private fun endpointFor(modelId: String, apiKey: String): String {
        val encodedModel = URLEncoder.encode(modelId.trim(), "UTF-8")
        val encodedKey = URLEncoder.encode(apiKey, "UTF-8")
        return "$BaseUrl/$encodedModel:generateContent?key=$encodedKey"
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

        val text = candidate
            .optJSONObject("content")
            ?.optJSONArray("parts")
            ?.optJSONObject(0)
            ?.optString("text")
            ?.trim()
            .orEmpty()

        return if (text.isNotBlank()) {
            GeminiApiResult.Success(text = text, modelId = modelId)
        } else {
            GeminiApiResult.Failure(geminiError(GeminiApiErrorKind.InvalidResponse))
        }
    }

    private fun errorForStatus(statusCode: Int): GeminiApiError = when (statusCode) {
        401, 403 -> geminiError(GeminiApiErrorKind.BadKey)
        429 -> geminiError(GeminiApiErrorKind.RateLimited)
        in 500..599 -> geminiError(GeminiApiErrorKind.Server)
        else -> geminiError(GeminiApiErrorKind.Unknown)
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
        GeminiApiErrorKind.Unknown -> "Gemini connection did not finish. Local mode still works."
    }
    return GeminiApiError(kind = kind, userMessage = message)
}
