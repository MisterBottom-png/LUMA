package com.orbit.app.integrations.gemini

import org.junit.Assert.assertEquals
import org.junit.Test

class GeminiErrorMappingTest {
    @Test
    fun invalidKeyReportedAsBadRequestIsShownAsABadKey() {
        val body = """
            {"error":{"code":400,"message":"API key not valid. Please pass a valid API key.",
            "status":"INVALID_ARGUMENT","details":[{"@type":"type.googleapis.com/google.rpc.ErrorInfo",
            "reason":"API_KEY_INVALID","domain":"googleapis.com"}]}}
        """.trimIndent()
        assertEquals(GeminiApiErrorKind.BadKey, geminiErrorKindFor(400, body))
    }

    @Test
    fun unknownModelIsReportedAsSuch() {
        val body = """{"error":{"code":404,"message":"models/gemini-9 is not found","status":"NOT_FOUND"}}"""
        assertEquals(GeminiApiErrorKind.ModelNotFound, geminiErrorKindFor(404, body))
    }

    @Test
    fun otherStatusesKeepTheirMeaning() {
        assertEquals(GeminiApiErrorKind.BadKey, geminiErrorKindFor(403, ""))
        assertEquals(GeminiApiErrorKind.RateLimited, geminiErrorKindFor(429, ""))
        assertEquals(GeminiApiErrorKind.Server, geminiErrorKindFor(503, "not json"))
        assertEquals(GeminiApiErrorKind.Unknown, geminiErrorKindFor(400, """{"error":{"status":"INVALID_ARGUMENT"}}"""))
    }
}
