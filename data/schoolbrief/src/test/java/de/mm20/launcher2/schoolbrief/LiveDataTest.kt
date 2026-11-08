package de.mm20.launcher2.schoolbrief

import de.mm20.launcher2.schoolbrief.live.GeminiWriter
import de.mm20.launcher2.schoolbrief.live.LiveBriefRepository
import de.mm20.launcher2.schoolbrief.live.McpClient
import de.mm20.launcher2.schoolbrief.live.NewsSource
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.jsonArray
import kotlinx.serialization.json.jsonObject
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test
import java.time.LocalDate
import java.time.LocalDateTime
import java.time.LocalTime

/** Invented data in the shapes Schoolhelp, Gemini and the news feeds return. */
class LiveDataTest {

    private val today = LocalDate.parse("2026-11-07") // Saturday

    private val events = Json.parseToJsonElement(
        """{"items":[
          {"id":"f1","title":"Ferien","start":"2026-10-24","end":"2026-11-01","kind":"termin","folder":"","done":false},
          {"id":"h1","title":"Wirtschaft: Werbeplakat entwerfen","start":"2026-11-10","end":"","kind":"hausaufgabe","folder":"Wirtschaft","note":"„…“ · Datum geschätzt","done":false},
          {"id":"h2","title":"Chemie: Protokoll","start":"2026-11-03","end":"","kind":"hausaufgabe","folder":"Chemie","done":true},
          {"id":"h3","title":"Deutsch: Gedicht lernen","start":"2026-11-05","end":"","kind":"hausaufgabe","folder":"Deutsch","done":false},
          {"id":"t1","title":"Chemie Leistungsnachweis","start":"2026-11-20","end":"","kind":"test","folder":"Chemie","done":true},
          {"id":"t2","title":"Englisch Test","start":"2026-11-04","end":"","kind":"test","folder":"English","done":false},
          {"id":"a1","title":"Laternenumzug","start":"2026-11-11T16:30","end":"2026-11-11T17:55","kind":"termin","folder":"Unsorted","done":false},
          {"id":"x1","title":"Notiz","start":"2026-11-08","kind":"notiz"},
          {"id":"w1","title":"Weihnachtsferien","start":"2026-12-21","end":"2027-01-06","kind":"termin","folder":"","done":false}
        ]}"""
    ).jsonObject["items"]!!.jsonArray.map { it.jsonObject }

    @Test
    fun eventsBecomeTasksAndHolidays() {
        val p = FeedBuilder.tasks(events, today)
        // h2 and t1 are ticked done: hidden.
        assertEquals(listOf("h1", "h3", "a1"), p.tasks.map { it.id })
        assertTrue(p.tasks.first { it.id == "h1" }.estimated)
        assertEquals("16:30", p.tasks.first { it.id == "a1" }.time)
        assertEquals("", p.tasks.first { it.id == "a1" }.subject) // "Unsorted" is not a subject
        // Past holiday dropped; future one kept even beyond 14 days.
        assertEquals(listOf("Weihnachtsferien"), p.daysOff.map { it.title })
    }

    @Test
    fun timetableMergesDoubleLessonsAndSkipsGaps() {
        val tt = Json.parseToJsonElement(
            """{"periods":{"1":["08:00","08:45"],"2":["08:50","09:35"],"3":["09:55","10:40"],"8":["14:05","14:50"]},
                "week":{"Mon":{"1":"Ph","2":"Ph","3":"Ma","8":"Ma"},"Sat":{"1":"Ph"}},
                "subjects":{"Ph":{"folder":"Physik"},"Ma":{"folder":"Mathe"}}}"""
        ).jsonObject
        assertEquals(
            mapOf("mon" to listOf(Lesson("08:00", "09:35", "Physik"), Lesson("09:55", "10:40", "Mathe"), Lesson("14:05", "14:50", "Mathe"))),
            FeedBuilder.timetable(tt),
        )
    }

    private val tasks = listOf(
        SchoolTask("1", SchoolTask.Homework, "Mathe: Arbeitsblatt Vektoren Seite 2", "Mathe", "2026-11-10"),
        SchoolTask("2", SchoolTask.Test, "Ethik Leistungsnachweis", "Ethik", "2026-11-11"),
    )
    private val news = listOf(
        NewsItem("sk", "S", "https://dennikn.sk/s"),
        NewsItem("eu", "C", "https://www.euractiv.com/c"),
        NewsItem("tech", "B", "https://www.theverge.com/b"),
        NewsItem("aviation", "A", "https://simpleflying.com/a"),
    )
    private val urls = news.map { it.url }.toSet()

    @Test
    fun briefCheckCatchesEveryRule() {
        assertNull(BriefRules.check("V utorok odovzdáš „Mathe: Arbeitsblatt Vektoren Seite 2“.", news, tasks, urls))
        assertTrue(BriefRules.check("A. B. C. D.", news, tasks, urls)!!.contains("max 3"))
        assertTrue(BriefRules.check("Zajtra „Etika test“.", news, tasks, urls)!!.contains("not an exact task title"))
        assertTrue(BriefRules.check("Ok.", news.reversed(), tasks, urls)!!.contains("topics"))
        assertTrue(BriefRules.check("Ok.", news, tasks, setOf("https://simpleflying.com/a"))!!.contains("not from the fetched feeds"))
        assertTrue(BriefRules.check("Dnes nič.", news, tasks, urls, listOf("Ethik Leistungsnachweis"))!!.contains("overdue"))
        assertNull(BriefRules.check("Po termíne je „Ethik Leistungsnachweis“.", news, tasks, urls, listOf("Ethik Leistungsnachweis")))
    }

    @Test
    fun templateQuotesTitlesExactlyAndPassesTheCheck() {
        val t = BriefRules.template(tasks, emptyList(), today, LocalTime.of(13, 30))
        assertEquals("Dnes máš voľno. Najbližšie: „Mathe: Arbeitsblatt Vektoren Seite 2“ (utorok) a „Ethik Leistungsnachweis“ (streda).", t)
        assertNull(BriefRules.check(t, news, tasks, urls))
    }

    @Test
    fun eveningTemplateTalksAboutTomorrowAndOverdueFirst() {
        val friday = LocalDate.parse("2026-11-06")
        val late = tasks + SchoolTask("3", SchoolTask.Homework, "Deutsch: Gedicht lernen", "Deutsch", "2026-11-05")
        val t = BriefRules.template(late, emptyList(), friday, LocalTime.of(19, 45))
        assertTrue(t, t.startsWith("Zajtra máš voľno. Po termíne: „Deutsch: Gedicht lernen“."))
        assertTrue(BriefRules.template(tasks, emptyList(), LocalDate.parse("2026-11-09"), LocalTime.of(7, 0)).startsWith("Dnes máš školu."))
    }

    @Test
    fun mcpParsesPlainJsonAndSse() {
        val plain = McpClient.parseResponse("""{"jsonrpc":"2.0","id":1,"result":{"ok":true}}""", "application/json")
        assertEquals("true", plain["result"]!!.jsonObject["ok"].toString())
        val sse = "event: message\ndata: {\"jsonrpc\":\"2.0\",\"method\":\"notifications/progress\"}\n\n" +
            "event: message\ndata: {\"jsonrpc\":\"2.0\",\"id\":2,\"result\":{\"content\":[{\"type\":\"text\",\"text\":\"{\\\"today\\\":\\\"Sat\\\"}\"}]}}\n\n"
        val res = McpClient.parseResponse(sse, "text/event-stream")
        assertEquals("\"Sat\"", McpClient.toolJson(res["result"]!!.jsonObject)["today"].toString())
    }

    @Test
    fun geminiAnswerIsParsed() {
        val body = """{"candidates":[{"content":{"parts":[{"text":"{\"brief\":\"Dnes máš voľno.\",\"news\":[{\"topic\":\"aviation\",\"title\":\"A\",\"url\":\"https://simpleflying.com/a\"}]}"}]}}],"modelVersion":"x"}"""
        val a = GeminiWriter.parseAnswer(body)
        assertEquals("Dnes máš voľno.", a.brief)
        assertEquals("aviation", a.news.single().topic)
        // The request carries the schema and both prompts.
        val req = GeminiWriter.request(today, false, tasks, emptyList(), emptyMap(), "brief has 4 sentences, max 3").toString()
        assertTrue(req.contains("responseSchema") && req.contains("previous_answer_rejected_because") && req.contains("sobota"))
    }

    @Test
    fun rssAndAtomAreParsedAndHttpOnlyLinksDropped() {
        val rss = """<?xml version="1.0"?><rss><channel>
            <item><title>  Boeing   news </title><link>https://simpleflying.com/x</link><pubDate>Sat, 07 Nov 2026 13:28:00 +0000</pubDate></item>
            <item><title>Old http</title><link>http://insecure.example/y</link><pubDate>Sat, 07 Nov 2026 13:00:00 +0000</pubDate></item>
            </channel></rss>"""
        val r = NewsSource.parse("aviation", rss)
        assertEquals(listOf("Boeing news"), r.map { it.title })
        val atom = """<?xml version="1.0"?><feed xmlns="http://www.w3.org/2005/Atom">
            <entry><title>Verge story</title><link rel="alternate" href="https://www.theverge.com/z"/><published>2026-11-07T08:49:00-05:00</published></entry>
            </feed>"""
        assertEquals("https://www.theverge.com/z", NewsSource.parse("tech", atom).single().url)
    }

    @Test
    fun latestSlotBeforeMorningIsYesterdayEvening() {
        assertEquals(LocalDateTime.parse("2026-11-06T19:30"), LiveBriefRepository.latestSlot(LocalDateTime.parse("2026-11-07T06:00")))
        assertEquals(LocalDateTime.parse("2026-11-07T13:30"), LiveBriefRepository.latestSlot(LocalDateTime.parse("2026-11-07T13:30")))
    }

    private val subjects = """"subjects":{"Ph":{"folder":"Physik","lang":"de"},"ANJ":{"folder":"English","lang":"en"}}"""

    @Test
    fun recordingNoneWhenIdle() {
        val s = Json.parseToJsonElement("""{"lesson":null,"alive":[],$subjects}""").jsonObject
        assertNull(de.mm20.launcher2.schoolbrief.live.RecordingParser.parse(s, LocalDateTime.parse("2026-11-10T09:10")))
    }

    @Test
    fun recordingFromLessonObject() {
        val s = Json.parseToJsonElement("""{"lesson":{"folder":"Physik","paused":true,"audio_seconds":1834.5},"alive":[],$subjects}""").jsonObject
        assertEquals(Recording("Physik", true, 1834), de.mm20.launcher2.schoolbrief.live.RecordingParser.parse(s, LocalDateTime.parse("2026-11-10T09:10")))
    }

    /** Recording on another device: only "alive" is filled; subject code mapped, time from "started". */
    @Test
    fun recordingFromAliveOnOtherDevice() {
        val s = Json.parseToJsonElement("""{"lesson":null,"alive":[{"subject":"Ph","started":"2026-11-10T08:40:00"}],$subjects}""").jsonObject
        assertEquals(Recording("Physik", false, 1800), de.mm20.launcher2.schoolbrief.live.RecordingParser.parse(s, LocalDateTime.parse("2026-11-10T09:10")))
        val s2 = Json.parseToJsonElement("""{"lesson":null,"alive":["Physik/2026-11-10-0800"],$subjects}""").jsonObject
        assertEquals("Physik", de.mm20.launcher2.schoolbrief.live.RecordingParser.parse(s2, LocalDateTime.parse("2026-11-10T09:10"))?.subject)
    }

    @Test
    fun subjectLanguages() {
        val s = Json.parseToJsonElement("""{$subjects}""").jsonObject
        assertEquals(mapOf("Physik" to "de", "English" to "en"), de.mm20.launcher2.schoolbrief.live.RecordingParser.subjectLangs(s))
    }

    /** today.read: today's plan from "plans" when "plan" is null, steps keep their index for today.set_step. */
    @Test
    fun todayPlanAndTodos() {
        val r = Json.parseToJsonElement("""{"day":"2026-11-10","plan":null,
            "plans":[{"day":"2026-11-09","steps":[{"step":"Alt","minutes":5,"done":true}]},
                     {"day":"2026-11-10","steps":[{"step":"Ankommen","minutes":15,"subject":"","done":true},
                                                  {"step":"Für Bio lernen","minutes":25,"subject":"Bio","done":false}]}],
            "todos":[{"id":"a1","text":"Venček info","done":false},{"id":"a2","text":"Kniha","done":true}]}""").jsonObject
        val t = de.mm20.launcher2.schoolbrief.live.TodayParser.parse(r, LocalDate.parse("2026-11-10"))
        assertEquals(listOf("Ankommen", "Für Bio lernen"), t.steps.map { it.text })
        assertEquals(1, t.steps[1].index)
        assertEquals(25, t.steps[1].minutes)
        assertEquals(listOf(Todo("a1", "Venček info", false), Todo("a2", "Kniha", true)), t.todos)
    }

    /** Offline tasks survive in the file, in order, until each one is sent. */
    @Test
    fun pendingQueueKeepsOrderOnDisk() {
        val f = java.io.File.createTempFile("pending", ".json").apply { delete() }
        val q = de.mm20.launcher2.schoolbrief.live.PendingQueue(f)
        val a = de.mm20.launcher2.schoolbrief.live.PendingOp("today.add_todo", kotlinx.serialization.json.buildJsonObject { put("text", kotlinx.serialization.json.JsonPrimitive("Kniha")) }, "local-1")
        val b = de.mm20.launcher2.schoolbrief.live.PendingOp("today.set_step", kotlinx.serialization.json.buildJsonObject { put("i", kotlinx.serialization.json.JsonPrimitive(1)) })
        q.add(a); q.add(b)
        assertEquals(listOf(a, b), de.mm20.launcher2.schoolbrief.live.PendingQueue(f).all())
        q.remove(a)
        assertEquals(listOf(b), de.mm20.launcher2.schoolbrief.live.PendingQueue(f).all())
        f.delete()
    }
}
