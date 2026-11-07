package de.mm20.launcher2.schoolbrief

import de.mm20.launcher2.schoolbrief.live.GeminiWriter
import de.mm20.launcher2.schoolbrief.live.NewsSource
import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertTrue
import org.junit.Assume.assumeTrue
import org.junit.Test
import java.time.LocalDate
import java.time.OffsetDateTime

/**
 * Real network: news feeds and Gemini. Runs only with GEMINI_API_KEY set, e.g.
 * GEMINI_API_KEY=... ./gradlew :data:schoolbrief:testDebugUnitTest --tests '*LiveNetworkTest*'
 * Uses invented tasks; never real Schoolhelp data.
 */
class LiveNetworkTest {

    @Test
    fun newsFeedsAndGeminiBriefEndToEnd() = runBlocking {
        val key = System.getenv("GEMINI_API_KEY")
        assumeTrue("GEMINI_API_KEY not set", !key.isNullOrBlank())
        val now = OffsetDateTime.now()
        val candidates = NewsSource.fetch(now)
        println("candidates: " + candidates.mapValues { it.value.size })
        assertTrue(candidates.values.all { it.isNotEmpty() })

        val today = LocalDate.now()
        val tasks = SampleFeed.create(today).tasks
        val writer = GeminiWriter(key!!)
        val result = writer.write(today, tomorrow = false, tasks = tasks, daysOff = emptyList(), candidates = candidates)
        assertNotNull("Gemini failed: ${writer.lastProblem}", result)
        println("BRIEF: " + result!!.brief)
        result.news.forEach { println("NEWS ${it.topic}: ${it.title}") }
    }

    @Test
    fun schoolChatAnswersFromSchoolData() = runBlocking {
        val key = System.getenv("GEMINI_API_KEY")
        assumeTrue("GEMINI_API_KEY not set", !key.isNullOrBlank())
        val now = java.time.LocalDateTime.parse("2026-11-10T07:30")
        val feed = SampleFeed.create(now.toLocalDate())
        val body = GeminiSchoolChat.request(listOf(ChatMessage(true, "Čo mám zajtra a kedy je najbližší test?")), feed, now)
        val res = de.mm20.launcher2.schoolbrief.live.Http.request(
            "https://generativelanguage.googleapis.com/v1beta/models/${GeminiWriter.DefaultModel}:generateContent", "POST",
            body = kotlinx.serialization.json.Json.encodeToString(kotlinx.serialization.json.JsonObject.serializer(), body),
            headers = mapOf("Content-Type" to "application/json", "x-goog-api-key" to key!!), timeoutMs = 30_000,
        )
        println("CHAT ${res.code}: " + res.body.substringAfter("\"text\": \"").substringBefore("\"").take(400))
        assertTrue(res.ok)
    }
}
