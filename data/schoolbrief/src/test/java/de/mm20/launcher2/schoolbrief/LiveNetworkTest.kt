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
}
