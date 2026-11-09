package de.mm20.launcher2.schoolbrief

import android.content.Context
import de.mm20.launcher2.schoolbrief.live.GeminiWriter
import de.mm20.launcher2.schoolbrief.live.Http
import de.mm20.launcher2.schoolbrief.live.SecretStore
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.buildJsonObject
import kotlinx.serialization.json.contentOrNull
import kotlinx.serialization.json.jsonArray
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive
import kotlinx.serialization.json.put
import kotlinx.serialization.json.putJsonArray
import kotlinx.serialization.json.putJsonObject
import java.time.LocalDateTime
import java.time.format.TextStyle
import java.util.Locale

/**
 * [modelText]: what the model gets instead of [text] (quick buttons show a short label, send a full prompt).
 * [failed]: an error shown in place of an answer; never sent to the model (round 31).
 */
data class ChatMessage(val fromUser: Boolean, val text: String, val modelText: String? = null, val failed: Boolean = false)

/** "Spýtaj sa": a small Gemini chat that knows today's school data (tasks, timetable, brief). */
interface SchoolChat {
    val hasKey: Boolean
    suspend fun reply(history: List<ChatMessage>, feed: Feed?): Result<String>
}

internal class GeminiSchoolChat(context: Context) : SchoolChat {
    private val secrets = SecretStore(context)

    override val hasKey: Boolean get() = secrets.get(SecretStore.GeminiKey) != null

    override suspend fun reply(history: List<ChatMessage>, feed: Feed?): Result<String> = runCatching {
        val key = secrets.get(SecretStore.GeminiKey) ?: error("Chýba kľúč Gemini. Vlož ho v Nastavenia > Škola.")
        val body = Json.encodeToString(JsonObject.serializer(), request(history, feed, LocalDateTime.now()))
        // Round 16: every failure says what is wrong and what to do. Round 31 (user: "make Gemini more
        // reliable"): a busy or failing model is tried once more, then the next model (each has its own
        // free quota); a blocked or empty answer also moves on. Key problems stop at once.
        val started = java.lang.System.currentTimeMillis()
        var last: String? = null
        models@ for (model in listOf(GeminiWriter.DefaultModel) + FallbackModels) {
            for (attempt in 0 until 2) {
                if (java.lang.System.currentTimeMillis() - started > TotalBudgetMs) break@models
                val res = try {
                    ask(model, body, key)
                } catch (e: java.io.IOException) {
                    last = "Gemini sa nedá zastihnúť. Skontroluj pripojenie a skús znova."
                    if (attempt == 0) { kotlinx.coroutines.delay(1500); continue } else break@models
                }
                when (val step = next(res.code, res.body)) {
                    is Step.Answer -> return@runCatching step.text
                    is Step.Stop -> error(step.message)
                    is Step.Retry -> { last = step.message; if (attempt == 0) kotlinx.coroutines.delay(1500) else continue@models }
                    is Step.NextModel -> { last = step.message; continue@models }
                }
            }
        }
        error(last ?: "Gemini neodpovedal. Skús to znova.")
    }

    private suspend fun ask(model: String, body: String, key: String) = Http.request(
        "https://generativelanguage.googleapis.com/v1beta/models/$model:generateContent", "POST",
        body = body,
        headers = mapOf("Content-Type" to "application/json", "x-goog-api-key" to key),
        timeoutMs = 30_000,
    )

    /** What to do after one Gemini response. */
    internal sealed interface Step {
        data class Answer(val text: String) : Step
        data class Stop(val message: String) : Step
        data class Retry(val message: String) : Step
        data class NextModel(val message: String) : Step
    }

    companion object {
        /** All tries of one question together stop after this (each request has its own 30 s timeout). */
        private const val TotalBudgetMs = 60_000L

        /** One response decides: the answer, stop (key problem), the same model once more, or the next model. */
        internal fun next(code: Int, body: String): Step {
            if (code in 200..299) {
                val text = answerText(body)
                return if (text != null) Step.Answer(text) else Step.NextModel(blocked(body))
            }
            val message = explain(code, body)
            return when {
                code == 401 || code == 403 -> Step.Stop(message)
                code == 400 && body.contains("API_KEY", ignoreCase = true) -> Step.Stop(message)
                code == 429 || code == 404 || code == 400 -> Step.NextModel(message)
                else -> Step.Retry(message)
            }
        }

        /** The answer's text, or null when Gemini gave none (blocked, cut off before any text, empty). */
        internal fun answerText(body: String): String? = runCatching {
            Json.parseToJsonElement(body).jsonObject["candidates"]!!.jsonArray[0].jsonObject["content"]!!.jsonObject["parts"]!!
                .jsonArray.mapNotNull { it.jsonObject["text"]?.jsonPrimitive?.contentOrNull }.joinToString("").trim()
        }.getOrNull()?.takeIf { it.isNotEmpty() }

        private fun blocked(body: String): String {
            val o = runCatching { Json.parseToJsonElement(body).jsonObject }.getOrNull()
            val reason = o?.get("promptFeedback")?.jsonObject?.get("blockReason")?.jsonPrimitive?.contentOrNull
                ?: o?.get("candidates")?.jsonArray?.firstOrNull()?.jsonObject?.get("finishReason")?.jsonPrimitive?.contentOrNull
            return "Gemini neodpovedal" + (reason?.let { " ($it)" } ?: "") + ". Skús otázku napísať inak."
        }

        /** The chat as Gemini turns: failed answers left out, roles alternating, starting with the user. */
        internal fun turns(history: List<ChatMessage>): List<Pair<Boolean, String>> {
            val out = mutableListOf<Pair<Boolean, String>>()
            for (m in history.filter { !it.failed }.takeLast(16)) {
                val t = (m.modelText ?: m.text).trim()
                if (t.isEmpty()) continue
                if (out.isNotEmpty() && out.last().first == m.fromUser) out[out.lastIndex] = m.fromUser to out.last().second + "\n\n" + t
                else out += m.fromUser to t
            }
            while (out.isNotEmpty() && !out.first().first) out.removeAt(0)
            return out
        }

        /** Tried in order when the default model answers 404 (renamed or retired). */
        internal val FallbackModels = listOf("gemini-flash-latest", "gemini-2.5-flash-lite", "gemini-2.5-flash")

        /** A failed Gemini call in words: the cause, Google's own message, and the next step. */
        internal fun explain(code: Int, body: String): String {
            val google = runCatching {
                Json.parseToJsonElement(body).jsonObject["error"]!!.jsonObject["message"]!!.jsonPrimitive.content
            }.getOrNull()?.take(160)
            val what = when {
                code == 400 && body.contains("API_KEY", ignoreCase = true) ->
                    "Kľúč Gemini je neplatný. Vytvor nový na aistudio.google.com a vlož ho v Nastavenia > Škola."
                code == 401 || code == 403 ->
                    "Google kľúč Gemini odmieta (zrušený alebo zablokovaný). Vytvor nový na aistudio.google.com a vlož ho v Nastavenia > Škola."
                code == 404 -> "Model Gemini sa nenašiel. Aplikácia potrebuje aktualizáciu."
                code == 429 -> "Bezplatný limit otázok je na chvíľu vyčerpaný. Skús o minútu."
                code >= 500 -> "Gemini je práve preťažený. Skús znova o chvíľu."
                else -> "Gemini neodpovedal (kód $code)."
            }
            return if (google != null) "$what\n($google)" else what
        }

        private const val System =
            "Si stručný pomocník na domovskej obrazovke študenta. Odpovedaj po slovensky, tykaj, krátko (najviac 4 vety alebo krátky zoznam). " +
                "Na otázky o škole používaj iba priložené údaje (school_data): rozvrh, úlohy, testy, termíny a prehľad. " +
                "Názvy úloh uvádzaj presne tak, ako sú, v „…“, nikdy ich neprekladaj. Ak niečo v údajoch nie je, povedz to otvorene a nevymýšľaj. " +
                "Dátumy a dni v týždni preberaj presne z údajov, nikdy ich neprepočítavaj. " +
                "Študent chodí na nemeckú školu: Leistungsnachweis = písomná skúška na známku (test), Klassenarbeit/Klausur = veľká písomka, " +
                "Hausaufgabe = domáca úloha, Referat = prezentácia, Abgabe = odovzdanie, Termin = udalosť. " +
                "Pri precvičovaní: jedna otázka naraz, v jazyku, v ktorom sa predmet učí (lang v údajoch: de = nemecky, en = anglicky, sk = slovensky); " +
                "spätnú väzbu daj krátko po slovensky a potom polož ďalšiu otázku. Nikdy sa nepýtaj, čo test obsahuje, ak máš témy v údajoch. " +
                "Na iné otázky odpovedz bežne a stručne."

        /** "dnes, utorok 10. 11.", "zajtra, streda 11. 11.", "piatok 13. 11." — ready-made, so the model never computes weekdays. */
        fun skDay(d: java.time.LocalDate, today: java.time.LocalDate): String {
            val sk = Locale.forLanguageTag("sk")
            val base = "${d.dayOfWeek.getDisplayName(TextStyle.FULL, sk)} ${d.dayOfMonth}.\u00A0${d.monthValue}."
            return when (java.time.temporal.ChronoUnit.DAYS.between(today, d)) {
                0L -> "dnes, $base"
                1L -> "zajtra, $base"
                -1L -> "včera, $base"
                else -> base
            }
        }

        fun request(history: List<ChatMessage>, feed: Feed?, now: LocalDateTime): JsonObject {
            val sk = Locale.forLanguageTag("sk")
            val data = buildJsonObject {
                put("today", skDay(now.toLocalDate(), now.toLocalDate()) + ", " + now.toLocalTime().withNano(0).withSecond(0))
                if (feed != null) {
                    put("brief", feed.brief)
                    putJsonArray("tasks") {
                        BriefLogic.visibleTasks(feed.tasks, now.toLocalDate()).forEach { r ->
                            add(buildJsonObject {
                                put("kind", r.task.kind); put("title", r.task.title); put("subject", r.task.subject)
                                // Weekday spelled out: the model got "13.11.2026 = štvrtok" wrong when left to compute it.
                                put("due", skDay(r.due, now.toLocalDate()))
                                put("overdue", r.overdue); r.task.time?.let { put("time", it) }
                            })
                        }
                    }
                    putJsonObject("timetable") {
                        val names = mapOf("mon" to "pondelok", "tue" to "utorok", "wed" to "streda", "thu" to "štvrtok", "fri" to "piatok")
                        feed.timetable.forEach { (day, lessons) ->
                            put(names[day] ?: day, lessons.joinToString("; ") { "${it.start}-${it.end} ${it.subject}" })
                        }
                    }
                    putJsonArray("days_off") { feed.daysOff.forEach { add(buildJsonObject { put("title", it.title); put("start", it.start); put("end", it.end) }) } }
                }
            }
            return buildJsonObject {
                putJsonObject("systemInstruction") {
                    putJsonArray("parts") { add(buildJsonObject { put("text", System + "\nschool_data: " + data.toString()) }) }
                }
                putJsonArray("contents") {
                    turns(history).forEach { (user, text) ->
                        add(buildJsonObject {
                            put("role", if (user) "user" else "model")
                            putJsonArray("parts") { add(buildJsonObject { put("text", text) }) }
                        })
                    }
                }
                putJsonObject("generationConfig") { put("temperature", 0.5) }
            }
        }
    }
}
