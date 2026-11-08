package de.mm20.launcher2.schoolbrief.live

import de.mm20.launcher2.schoolbrief.BriefLogic
import de.mm20.launcher2.schoolbrief.BriefRules
import de.mm20.launcher2.schoolbrief.DayOff
import de.mm20.launcher2.schoolbrief.NewsItem
import de.mm20.launcher2.schoolbrief.SchoolTask
import kotlinx.serialization.Serializable
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.buildJsonArray
import kotlinx.serialization.json.buildJsonObject
import kotlinx.serialization.json.contentOrNull
import kotlinx.serialization.json.jsonArray
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive
import kotlinx.serialization.json.put
import kotlinx.serialization.json.putJsonArray
import kotlinx.serialization.json.putJsonObject
import java.time.LocalDate
import java.time.format.TextStyle
import java.util.Locale

/** Writes the brief and picks the headlines with Gemini (free tier). Every answer is checked; failures fall back. */
internal class GeminiWriter(private val apiKey: String, private val model: String = DefaultModel) {

    @Serializable
    data class Answer(val brief: String, val news: List<NewsItem>)

    class Result(val brief: String, val news: List<NewsItem>)

    /** Null when Gemini fails twice or its answer fails the checks: the caller uses the template. */
    suspend fun write(
        today: LocalDate,
        tomorrow: Boolean,
        tasks: List<SchoolTask>,
        daysOff: List<DayOff>,
        candidates: Map<String, List<NewsCandidate>>,
    ): Result? {
        val urls = candidates.values.flatten().mapTo(HashSet()) { it.url }
        val overdue = BriefLogic.visibleTasks(tasks, today).filter { it.overdue }.map { it.task.title }
        var feedback: String? = null
        repeat(2) {
            val answer = try {
                ask(request(today, tomorrow, tasks, daysOff, candidates, feedback))
            } catch (e: Exception) {
                lastProblem = "request failed: ${e.message}"; return null
            }
            val problem = BriefRules.check(answer.brief, answer.news, tasks, urls, overdue)
            if (problem == null) {
                lastProblem = null; return Result(answer.brief.trim(), answer.news)
            }
            lastProblem = "answer rejected: $problem"
            feedback = problem
        }
        return null
    }

    /** Why the last [write] returned null (for logs and the settings sheet). */
    var lastProblem: String? = null
        private set

    private suspend fun ask(body: JsonObject): Answer {
        val res = Http.request(
            "https://generativelanguage.googleapis.com/v1beta/models/$model:generateContent", "POST",
            body = Json.encodeToString(JsonObject.serializer(), body),
            headers = mapOf("Content-Type" to "application/json", "x-goog-api-key" to apiKey),
            timeoutMs = 30_000,
        )
        if (!res.ok) throw HttpException(res.code, "Gemini ${res.code}: ${res.body.take(300)}")
        return parseAnswer(res.body)
    }

    companion object {
        /**
         * Google's alias for the current Flash-Lite model, so new versions need no app update.
         * Measured Nov 2026 on the free tier: Flash-Lite ~1 s; full Flash often "high demand" or 100+ s.
         */
        const val DefaultModel = "gemini-flash-lite-latest"

        private val json = Json { ignoreUnknownKeys = true }

        fun parseAnswer(responseBody: String): Answer {
            val root = Json.parseToJsonElement(responseBody).jsonObject
            val text = root["candidates"]!!.jsonArray[0].jsonObject["content"]!!.jsonObject["parts"]!!.jsonArray
                .first { it.jsonObject["text"] != null }.jsonObject["text"]!!.jsonPrimitive.contentOrNull!!
            return json.decodeFromString(Answer.serializer(), text)
        }

        private const val System =
            "Si asistent na domovskej obrazovke študenta. Píš po slovensky, tykaj, pokojne, bez výkričníkov a bez úvodných fráz. " +
                "Napíš 2 až 3 krátke vety o dni uvedenom v poli day. Začni tým, čo je najbližšie. Žiadne priania ani rady na záver. " +
                "Uveď najviac 3 položky: vždy najprv úlohy s when = PO TERMÍNE, potom čo je na rade dnes alebo zajtra, potom testy. Hovor k jednému človeku (ty), nie my. " +
                "Názvy úloh cituj v „…“ presne tak, ako sú v dátach, znak po znaku. Nikdy ich neprekladaj, neskracuj ani nemeň. " +
                "Ak je v ten deň voľno alebo víkend, povedz to a spomeň najdôležitejšiu vec na ďalší školský deň. " +
                "Z kandidátov vyber po jednej správe: sk (najdôležitejšia správa zo Slovenska; nie krimi ani bulvár), " +
                "eu (prednostne Európsky parlament a EÚ vo vzťahu k Slovensku, inak inštitúcie EÚ), " +
                "tech (najdôležitejšia technologická správa; nie veda, zdravie ani zábava), " +
                "aviation (lietadlá, aerolinky, letiská; nie videá ani zoznamy). Titulok a url skopíruj presne."

        private fun dayWord(today: LocalDate, d: LocalDate, sk: Locale): String = when (val n = java.time.temporal.ChronoUnit.DAYS.between(today, d)) {
            0L -> "dnes"
            1L -> "zajtra"
            else -> "o $n dní (${d.dayOfWeek.getDisplayName(TextStyle.FULL, sk)})"
        }

        fun request(
            today: LocalDate,
            tomorrow: Boolean,
            tasks: List<SchoolTask>,
            daysOff: List<DayOff>,
            candidates: Map<String, List<NewsCandidate>>,
            feedback: String?,
        ): JsonObject {
            val sk = Locale.forLanguageTag("sk")
            val day = if (tomorrow) today.plusDays(1) else today
            val input = buildJsonObject {
                put("today", "$today (${today.dayOfWeek.getDisplayName(TextStyle.FULL, sk)})")
                put("day", if (tomorrow) "zajtra, $day" else "dnes, $day")
                putJsonArray("days_off") { daysOff.forEach { add(buildJsonObject { put("title", it.title); put("start", it.start); put("end", it.end) }) } }
                // Same order and overdue rule as the Tasks block, so the brief never contradicts it.
                putJsonArray("tasks") {
                    BriefLogic.visibleTasks(tasks, today).forEach { r ->
                        val t = r.task
                        add(buildJsonObject {
                            put("kind", t.kind); put("title", t.title); put("subject", t.subject); put("due", t.due)
                            put("when", if (r.overdue) "PO TERMÍNE" else dayWord(today, r.due, sk))
                            t.time?.let { put("time", it) }
                        })
                    }
                }
                putJsonObject("news_candidates") {
                    candidates.forEach { (topic, list) ->
                        put(topic, buildJsonArray { list.forEach { add(buildJsonObject { put("title", it.title); put("url", it.url) }) } })
                    }
                }
                feedback?.let { put("previous_answer_rejected_because", it) }
            }
            val str = { buildJsonObject { put("type", "STRING") } }
            return buildJsonObject {
                putJsonObject("systemInstruction") { putJsonArray("parts") { add(buildJsonObject { put("text", System) }) } }
                putJsonArray("contents") {
                    add(buildJsonObject {
                        put("role", "user")
                        putJsonArray("parts") { add(buildJsonObject { put("text", Json.encodeToString(JsonObject.serializer(), input)) }) }
                    })
                }
                putJsonObject("generationConfig") {
                    put("responseMimeType", "application/json")
                    put("temperature", 0.4)
                    putJsonObject("responseSchema") {
                        put("type", "OBJECT")
                        putJsonArray("required") { add(kotlinx.serialization.json.JsonPrimitive("brief")); add(kotlinx.serialization.json.JsonPrimitive("news")) }
                        putJsonObject("properties") {
                            put("brief", str())
                            putJsonObject("news") {
                                put("type", "ARRAY")
                                putJsonObject("items") {
                                    put("type", "OBJECT")
                                    putJsonArray("required") { listOf("topic", "title", "url").forEach { add(kotlinx.serialization.json.JsonPrimitive(it)) } }
                                    putJsonObject("properties") {
                                        putJsonObject("topic") {
                                            put("type", "STRING")
                                            putJsonArray("enum") { BriefRules.Topics.forEach { add(kotlinx.serialization.json.JsonPrimitive(it)) } }
                                        }
                                        put("title", str()); put("url", str())
                                    }
                                }
                            }
                        }
                    }
                }
            }
        }
    }
}
