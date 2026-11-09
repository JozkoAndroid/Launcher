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

/** [modelText]: what the model gets instead of [text] (quick buttons show a short label, send a full prompt). */
data class ChatMessage(val fromUser: Boolean, val text: String, val modelText: String? = null)

/** "Spýtaj sa": a small Gemini chat that knows today's school data (tasks, timetable, brief). */
interface SchoolChat {
    val hasKey: Boolean

    /** [onProgress]: a short note while the answer is being put together ("Pozerám do Schoolhelp…"). */
    suspend fun reply(history: List<ChatMessage>, feed: Feed?, onProgress: (String) -> Unit = {}): Result<String>

    /** Claude's estimated spend this calendar month in USD; null when Gemini answers. */
    fun claudeSpent(): Double? = null
}

internal class GeminiSchoolChat(context: Context) : SchoolChat {
    private val secrets = SecretStore(context)

    override val hasKey: Boolean get() = secrets.get(SecretStore.GeminiKey) != null

    override suspend fun reply(history: List<ChatMessage>, feed: Feed?, onProgress: (String) -> Unit): Result<String> = runCatching {
        val key = secrets.get(SecretStore.GeminiKey) ?: error("Chýba kľúč Gemini. Vlož ho v Nastavenia > Škola.")
        val body = Json.encodeToString(JsonObject.serializer(), request(history, feed, LocalDateTime.now()))
        // Round 16 (device test: only "Gemini 401" was shown): the default model, and if Google no longer
        // knows it (404) the fallbacks; every failure says what is wrong and what to do.
        var res = ask(GeminiWriter.DefaultModel, body, key)
        for (m in FallbackModels) {
            if (res.code != 404) break
            res = ask(m, body, key)
        }
        if (!res.ok) error(explain(res.code, res.body))
        Json.parseToJsonElement(res.body).jsonObject["candidates"]!!.jsonArray[0].jsonObject["content"]!!.jsonObject["parts"]!!
            .jsonArray.mapNotNull { it.jsonObject["text"]?.jsonPrimitive?.contentOrNull }.joinToString("").trim()
            .ifEmpty { error("Prázdna odpoveď.") }
    }

    private suspend fun ask(model: String, body: String, key: String) = Http.request(
        "https://generativelanguage.googleapis.com/v1beta/models/$model:generateContent", "POST",
        body = body,
        headers = mapOf("Content-Type" to "application/json", "x-goog-api-key" to key),
        timeoutMs = 30_000,
    )

    companion object {
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
            val data = schoolData(feed, now)
            return buildJsonObject {
                putJsonObject("systemInstruction") {
                    putJsonArray("parts") { add(buildJsonObject { put("text", System + "\nschool_data: " + data.toString()) }) }
                }
                putJsonArray("contents") {
                    history.takeLast(16).forEach { m ->
                        add(buildJsonObject {
                            put("role", if (m.fromUser) "user" else "model")
                            putJsonArray("parts") { add(buildJsonObject { put("text", m.modelText ?: m.text) }) }
                        })
                    }
                }
                putJsonObject("generationConfig") { put("temperature", 0.5) }
            }
        }

        /** Today's school data for a chat model: date and time, brief, visible tasks, timetable, days off. */
        fun schoolData(feed: Feed?, now: LocalDateTime): JsonObject {
            return buildJsonObject {
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
        }
    }
}
