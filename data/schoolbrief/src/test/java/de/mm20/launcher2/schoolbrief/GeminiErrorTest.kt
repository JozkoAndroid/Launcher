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

    // Round 31: what happens after each response.
    @Test
    fun busyOrLimitedMovesOnKeyProblemsStop() {
        assertTrue(GeminiSchoolChat.next(429, """{"error":{"code":429,"message":"Resource exhausted"}}""") is GeminiSchoolChat.Step.NextModel)
        assertTrue(GeminiSchoolChat.next(503, """{"error":{"code":503,"message":"The model is overloaded"}}""") is GeminiSchoolChat.Step.Retry)
        assertTrue(GeminiSchoolChat.next(403, """{"error":{"code":403,"message":"leaked"}}""") is GeminiSchoolChat.Step.Stop)
        assertTrue(GeminiSchoolChat.next(400, body("API key not valid.", "INVALID_ARGUMENT")) is GeminiSchoolChat.Step.Stop)
    }

    @Test
    fun blockedOrEmptyAnswerTriesTheNextModel() {
        val blocked = GeminiSchoolChat.next(200, """{"promptFeedback":{"blockReason":"SAFETY"}}""")
        assertTrue(blocked is GeminiSchoolChat.Step.NextModel && (blocked as GeminiSchoolChat.Step.NextModel).message.contains("SAFETY"))
        val ok = GeminiSchoolChat.next(200, """{"candidates":[{"content":{"parts":[{"text":"Zajtra Bio."}]}}]}""")
        assertTrue(ok == GeminiSchoolChat.Step.Answer("Zajtra Bio."))
    }

    @Test
    fun failedAnswersAreNotContextAndRolesAlternate() {
        val turns = GeminiSchoolChat.turns(
            listOf(
                ChatMessage(false, "stará"),
                ChatMessage(true, "Čo mám zajtra?"),
                ChatMessage(false, "Gemini je práve preťažený.", failed = true),
                ChatMessage(true, "Čo mám zajtra?"),
            )
        )
        assertTrue(turns.size == 1 && turns[0].first && turns[0].second == "Čo mám zajtra?\n\nČo mám zajtra?")
    }
}
