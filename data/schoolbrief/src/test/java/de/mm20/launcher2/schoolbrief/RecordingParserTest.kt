package de.mm20.launcher2.schoolbrief

import de.mm20.launcher2.schoolbrief.live.RecordingParser
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.jsonObject
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test
import java.time.LocalDateTime

/** lessons.status shapes seen live (Nov 2026), trimmed to the fields the parser reads; no personal data. */
class RecordingParserTest {
    private val now = LocalDateTime.parse("2026-11-08T12:45:00")
    private val subjects = """"subjects": {"ETV": {"folder": "Ethik", "lang": "de"}, "Ph": {"folder": "Physik", "lang": "de"}}"""

    private fun parse(json: String) = RecordingParser.parse(Json.parseToJsonElement(json).jsonObject, now)

    @Test
    fun runningLessonPaused() {
        val r = parse("""{"lesson": {"id": "Ethik/2026-11-08-1241", "subject": "ETV", "folder": "Ethik", "lang": "de",
            "audio_seconds": 0.672, "paused": true, "block_end": null}, "alive": ["browser"], $subjects}""")!!
        assertEquals("Ethik", r.subject)
        assertEquals(true, r.paused)
    }

    @Test
    fun servicesInAliveAreNotARecording() {
        assertNull(parse("""{"lesson": null, "alive": ["browser"], $subjects}"""))
    }

    @Test
    fun lessonOnAnotherDeviceFromAlive() {
        val r = parse("""{"lesson": null, "alive": ["browser", "Physik/2026-11-10-0800"], $subjects}""")!!
        assertEquals("Physik", r.subject)
    }

    @Test
    fun lessonNestedUnderAnotherDevice() {
        val r = parse("""{"lesson": null, "alive": ["browser"], "devices": [{"device": "mac", "lesson": {"id": "Physik/2026-11-09-0800",
            "folder": "Physik", "audio_seconds": 1834.0, "paused": false}}], $subjects}""")!!
        assertEquals("Physik", r.subject)
        assertEquals(1834L, r.seconds)
    }
}
