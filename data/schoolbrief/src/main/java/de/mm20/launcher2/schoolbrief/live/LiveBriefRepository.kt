package de.mm20.launcher2.schoolbrief.live

import android.content.Context
import android.util.Log
import de.mm20.launcher2.schoolbrief.BriefRepository
import de.mm20.launcher2.schoolbrief.BriefRules
import de.mm20.launcher2.schoolbrief.BriefStatus
import de.mm20.launcher2.schoolbrief.Feed
import de.mm20.launcher2.schoolbrief.FeedBuilder
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.launch
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.buildJsonObject
import kotlinx.serialization.json.intOrNull
import kotlinx.serialization.json.jsonArray
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive
import kotlinx.serialization.json.put
import java.io.File
import java.time.LocalDate
import java.time.LocalDateTime
import java.time.OffsetDateTime
import java.time.ZoneId
import java.time.temporal.ChronoUnit

/**
 * Live feed, built on the phone:
 * - Schoolhelp (tasks, timetable) refreshed when the home screen is visible and data is over 30 minutes old;
 * - the brief and headlines rewritten once per slot (06:30, 13:30, 19:30) with Gemini, else the template;
 * - everything cached, so the screen is never empty.
 */
internal class LiveBriefRepository(context: Context) : BriefRepository {

    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.IO)
    private val secrets = SecretStore(context)
    private val auth = SchoolhelpAuth(secrets)
    private val mcp = McpClient(auth)
    private val prefs = context.getSharedPreferences("schoolbrief", Context.MODE_PRIVATE)
    private val cacheFile = File(context.filesDir, "schoolbrief/feed.json")
    private val json = Json { ignoreUnknownKeys = true; prettyPrint = false }

    override val feed = MutableStateFlow(readCache())
    override val status = MutableStateFlow<BriefStatus>(if (auth.isLoggedIn) BriefStatus.Ok else BriefStatus.NeedsLogin)
    override val hasGeminiKey = MutableStateFlow(secrets.get(SecretStore.GeminiKey) != null)

    private var job: Job? = null
    private var lastAttempt = 0L

    override fun refreshIfDue() {
        if (job?.isActive == true) return
        if (!auth.isLoggedIn) {
            if (status.value !is BriefStatus.LoggingIn) status.value = BriefStatus.NeedsLogin
            return
        }
        val now = System.currentTimeMillis()
        val dataAge = now - prefs.getLong(KeyDataAt, 0)
        val briefDue = latestSlot(LocalDateTime.now()).isAfter(briefAt())
        val due = feed.value == null || dataAge > DataMaxAgeMs || briefDue
        // After a failure, wait 5 minutes before trying again.
        if (!due || now - lastAttempt < RetryMs) return
        lastAttempt = now
        job = scope.launch { refresh(forceBrief = false) }
    }

    override fun login(openUrl: (String) -> Unit) {
        if (job?.isActive == true) job?.cancel()
        job = scope.launch {
            try {
                status.value = BriefStatus.LoggingIn("register")
                val req = auth.begin()
                status.value = BriefStatus.LoggingIn("browser")
                launch(Dispatchers.Main) { openUrl(req.authorizeUrl) }
                auth.complete(req)
                refresh(forceBrief = true)
            } catch (e: Exception) {
                Log.w(Tag, "login failed", e)
                status.value = if (auth.isLoggedIn) BriefStatus.Error(e.message ?: "Chyba") else BriefStatus.Error(e.message ?: "Prihlásenie zlyhalo.")
            }
        }
    }

    override fun logout() {
        job?.cancel()
        auth.logout()
        status.value = BriefStatus.NeedsLogin
    }

    override fun setGeminiKey(key: String?) {
        secrets.put(SecretStore.GeminiKey, key?.trim()?.takeIf { it.isNotEmpty() })
        hasGeminiKey.value = secrets.get(SecretStore.GeminiKey) != null
        // Rewrite the brief with (or without) AI on the next visible minute.
        prefs.edit().remove(KeyBriefAt).apply()
        lastAttempt = 0
    }

    private suspend fun refresh(forceBrief: Boolean) {
        status.value = BriefStatus.Refreshing
        try {
            val nowZ = OffsetDateTime.now(ZoneId.systemDefault())
            val today = nowZ.toLocalDate()

            val timetable = mcp.appCall("timetable.read")
            val items = mutableListOf<JsonObject>()
            var offset: Int? = 0
            var pages = 0
            while (offset != null && pages++ < 10) {
                val page = mcp.appCall("list_events", buildJsonObject {
                    put("start", today.minusDays(30).toString())
                    put("end", today.plusDays(15).toString())
                    put("offset", offset)
                    put("limit", 100)
                })
                page["items"]?.jsonArray?.forEach { items += it.jsonObject }
                offset = page["next_offset"]?.jsonPrimitive?.intOrNull
            }
            val parts = FeedBuilder.tasks(items, today)
            val lessons = FeedBuilder.timetable(timetable)

            val previous = feed.value
            val briefDue = forceBrief || previous == null || latestSlot(nowZ.toLocalDateTime()).isAfter(briefAt())
            val wasTemplate = prefs.getBoolean(KeyBriefIsTemplate, true)
            var brief = previous?.brief.orEmpty()
            var news = previous?.news.orEmpty()
            var isTemplate = wasTemplate

            if (briefDue || wasTemplate) {
                val candidates = runCatching { NewsSource.fetch(nowZ) }.getOrDefault(emptyMap())
                val key = secrets.get(SecretStore.GeminiKey)
                val tomorrow = BriefRules.coversTomorrow(nowZ.toLocalTime())
                val ai = if (key != null && briefDue) GeminiWriter(key).write(today, tomorrow, parts.tasks, parts.daysOff, candidates) else null
                if (ai != null) {
                    brief = ai.brief; news = ai.news; isTemplate = false
                } else if (briefDue || wasTemplate) {
                    brief = BriefRules.template(parts.tasks, parts.daysOff, today, nowZ.toLocalTime())
                    news = NewsSource.newestPerTopic(candidates).ifEmpty { news }
                    isTemplate = true
                }
                if (briefDue) prefs.edit().putString(KeyBriefAt, nowZ.toLocalDateTime().toString()).apply()
            }

            val f = Feed(
                updated = nowZ.truncatedTo(ChronoUnit.SECONDS).toString(),
                brief = brief,
                news = news,
                tasks = parts.tasks,
                daysOff = parts.daysOff,
                timetable = lessons,
            )
            writeCache(f)
            feed.value = f
            prefs.edit().putLong(KeyDataAt, System.currentTimeMillis()).putBoolean(KeyBriefIsTemplate, isTemplate).apply()
            status.value = BriefStatus.Ok
        } catch (e: McpClient.LoggedOut) {
            status.value = BriefStatus.NeedsLogin
        } catch (e: Exception) {
            Log.w(Tag, "refresh failed", e)
            status.value = BriefStatus.Error(e.message ?: e.javaClass.simpleName)
        }
    }

    private fun briefAt(): LocalDateTime =
        prefs.getString(KeyBriefAt, null)?.let { runCatching { LocalDateTime.parse(it) }.getOrNull() } ?: LocalDateTime.MIN

    private fun readCache(): Feed? = runCatching { json.decodeFromString(Feed.serializer(), cacheFile.readText()) }.getOrNull()

    private fun writeCache(f: Feed) {
        cacheFile.parentFile?.mkdirs()
        val tmp = File(cacheFile.parentFile, "feed.json.tmp")
        tmp.writeText(json.encodeToString(Feed.serializer(), f))
        tmp.renameTo(cacheFile)
    }

    companion object {
        private const val Tag = "SchoolBrief"
        private const val KeyDataAt = "data_at"
        private const val KeyBriefAt = "brief_at"
        private const val KeyBriefIsTemplate = "brief_is_template"
        private const val DataMaxAgeMs = 30 * 60_000L
        private const val RetryMs = 5 * 60_000L

        /** The most recent slot at or before [now] (yesterday's evening slot before 06:30). */
        fun latestSlot(now: LocalDateTime): LocalDateTime {
            val today: LocalDate = now.toLocalDate()
            val slots = BriefRules.Slots.map { today.atTime(it) }.filter { !it.isAfter(now) }
            return slots.lastOrNull() ?: today.minusDays(1).atTime(BriefRules.Slots.last())
        }
    }
}
