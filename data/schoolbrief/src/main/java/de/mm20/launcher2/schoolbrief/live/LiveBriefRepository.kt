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
import kotlinx.serialization.json.booleanOrNull
import kotlinx.serialization.json.contentOrNull
import kotlinx.serialization.json.doubleOrNull
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

    override val recording = MutableStateFlow<de.mm20.launcher2.schoolbrief.Recording?>(null)
    override val unreadChats = MutableStateFlow(0)
    private var recordingJob: Job? = null
    private var lastRecordingCheck = 0L

    /** Every 2 minutes while the home screen is visible (any day: recordings also happen outside lessons). */
    private fun checkRecordingIfDue() {
        if (recordingJob?.isActive == true || System.currentTimeMillis() - lastRecordingCheck < 2 * 60_000L) return
        lastRecordingCheck = System.currentTimeMillis()
        recordingJob = scope.launch { readRecording(); readUnreadChats() }
    }

    private suspend fun readRecording() {
        runCatching { mcp.appCall("lessons.status") }.onSuccess { s ->
            recording.value = (s["lesson"] as? JsonObject)?.let { l ->
                de.mm20.launcher2.schoolbrief.Recording(
                    subject = l["folder"]?.jsonPrimitive?.contentOrNull ?: l["subject"]?.jsonPrimitive?.contentOrNull ?: "",
                    paused = l["paused"]?.jsonPrimitive?.booleanOrNull == true,
                    seconds = l["audio_seconds"]?.jsonPrimitive?.doubleOrNull?.toLong() ?: 0,
                )
            }
        }
    }

    private suspend fun readUnreadChats() {
        runCatching { mcp.appCall("talk.list", buildJsonObject { put("limit", 30) }) }.onSuccess { r ->
            val chats = ((r["result"] as? JsonObject) ?: r)["chats"]?.jsonArray ?: return@onSuccess
            unreadChats.value = chats.sumOf { c ->
                val o = c.jsonObject
                if (o["muted"]?.jsonPrimitive?.booleanOrNull == true) 0 else (o["unread"]?.jsonPrimitive?.intOrNull ?: 0)
            }
        }
    }

    override fun markDone(taskId: String) {
        // Hide at once; Schoolhelp's mark_done changes only the student's own tick.
        feed.value?.let { f -> feed.value = f.copy(tasks = f.tasks.map { if (it.id == taskId) it.copy(done = true) else it }) }
        scope.launch {
            runCatching { mcp.appCall("mark_done", buildJsonObject { put("event_id", taskId); put("done", true) }) }
                .onSuccess { feed.value?.let { writeCache(it) } }
                .onFailure { Log.w(Tag, "mark_done failed", it); lastAttempt = 0; prefs.edit().putLong(KeyDataAt, 0).apply() }
        }
    }

    override fun pauseRecording(pause: Boolean) {
        scope.launch {
            runCatching { mcp.appCall("lessons.pause", buildJsonObject { put("on", pause) }) }
            lastRecordingCheck = 0
        }
    }

    override fun stopRecording() {
        scope.launch {
            runCatching { mcp.appCall("lessons.stop") }
            lastRecordingCheck = 0
        }
    }

    override fun refreshIfDue() {
        if (auth.isLoggedIn) checkRecordingIfDue()
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
                val url = auth.begin()
                status.value = BriefStatus.LoggingIn("browser")
                launch(Dispatchers.Main) { openUrl(url) }
            } catch (e: Exception) {
                Log.w(Tag, "login failed", e)
                status.value = BriefStatus.Error(e.message ?: "Prihlásenie zlyhalo.")
            }
        }
    }

    override fun handleLoginCallback(uri: String) {
        if (job?.isActive == true) job?.cancel()
        job = scope.launch {
            try {
                status.value = BriefStatus.LoggingIn("token")
                auth.complete(uri)
                lastAttempt = 0
                refresh(forceBrief = true)
            } catch (e: Exception) {
                Log.w(Tag, "login callback failed", e)
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
                // No "limit": Schoolhelp rejects large values ("Capability number out of range").
                // Its own page size is used; "offset" only from the second page on.
                val page = mcp.appCall("list_events", buildJsonObject {
                    put("start", today.minusDays(30).toString())
                    put("end", today.plusDays(15).toString())
                    if (offset != null && offset > 0) put("offset", offset)
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
