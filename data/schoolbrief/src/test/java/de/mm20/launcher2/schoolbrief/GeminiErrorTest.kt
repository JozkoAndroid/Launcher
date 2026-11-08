package de.mm20.launcher2.schoolbrief

import org.junit.Assert.assertTrue
import org.junit.Test

/** Round 16: a failed Gemini call says what is wrong (device test showed only "Gemini 401"). */
class GeminiErrorTest {
    private fun body(msg: String, status: String) =
        """{"error":{"code":400,"message":"$msg","status":"$status","details":[{"reason":"API_KEY_INVALID"}]}}"""

    @Test
    fun invalidKeyAsksForANewOne() {
        val text = GeminiSchoolChat.explain(400, body("API key not valid. Please pass a valid API key.", "INVALID_ARGUMENT"))
        assertTrue(text, text.contains("neplatný") && text.contains("Nastavenia") && text.contains("API key not valid"))
    }

    @Test
    fun rejectedKey() {
        val text = GeminiSchoolChat.explain(403, """{"error":{"code":403,"message":"Your API key was reported as leaked.","status":"PERMISSION_DENIED"}}""")
        assertTrue(text, text.contains("odmieta") && text.contains("leaked"))
    }

    @Test
    fun rateLimitAndOverload() {
        assertTrue(GeminiSchoolChat.explain(429, "").contains("limit"))
        assertTrue(GeminiSchoolChat.explain(503, "not json").contains("preťažený"))
    }
}
