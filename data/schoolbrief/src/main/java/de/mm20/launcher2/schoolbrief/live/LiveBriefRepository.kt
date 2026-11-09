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
import kotlinx.coroutines.sync.withLock
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
            recording.value = RecordingParser.parse(s, java.time.LocalDateTime.now())
            RecordingParser.subjectLangs(s).takeIf { it.isNotEmpty() }?.let { langs = it }
        }
    }

    /** Fach folder -> teaching language (de, en, sk), from lessons.status. */
    @Volatile private var langs: Map<String, String> = emptyMap()

    override suspend fun studyContext(folder: String): de.mm20.launcher2.schoolbrief.StudyContext? = runCatching {
        if (langs.isEmpty()) runCatching { RecordingParser.subjectLangs(mcp.appCall("lessons.status")) }.getOrNull()?.let { langs = it }
        val r = mcp.appCall("stoff.read", buildJsonObject { put("folder", folder); put("limit", 8) })
        val root = (r["result"] as? JsonObject) ?: r
        val jetzt = root["jetzt"] as? JsonObject
        de.mm20.launcher2.schoolbrief.StudyContext(
            folder = folder,
            lang = langs[folder],
            current = jetzt?.get("title")?.jsonPrimitive?.contentOrNull,
            points = (jetzt?.get("points") as? kotlinx.serialization.json.JsonArray)?.mapNotNull { it.jsonPrimitive.contentOrNull }.orEmpty(),
            recent = (root["lessons"] as? kotlinx.serialization.json.JsonArray)?.mapNotNull { (it as? JsonObject)?.get("title")?.jsonPrimitive?.contentOrNull }
                ?.filterNot { it.contains("Kein Unterrichtsinhalt", true) }?.take(8).orEmpty(),
        )
    }.onFailure { Log.w(Tag, "stoff.read failed", it) }.getOrNull()

    override suspend fun answerNow(): Result<String> = runCatching {
        val r = mcp.appCall("lessons.answer_now")
        val root = (r["result"] as? JsonObject) ?: r
        listOf("answer", "text", "reply", "result").firstNotNullOfOrNull { root[it]?.let { e -> (e as? kotlinx.serialization.json.JsonPrimitive)?.contentOrNull } }
            ?: root["error"]?.jsonPrimitive?.contentOrNull?.let { error(it) }
            ?: error("Schoolhelp nevrátil odpoveď.")
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

    override fun markDone(taskId: String, done: Boolean) {
        // Change the list at once (undo shows the task again); Schoolhelp's mark_done changes only the student's own tick.
        feed.value?.let { f -> feed.value = f.copy(tasks = f.tasks.map { if (it.id == taskId) it.copy(done = done) else it }) }
        scope.launch {
            runCatching { mcp.appCall("mark_done", buildJsonObject { put("event_id", taskId); put("done", done) }) }
                .onSuccess { feed.value?.let { writeCache(it) } }
                .onFailure { Log.w(Tag, "mark_done failed", it); lastAttempt = 0; prefs.edit().putLong(KeyDataAt, 0).apply() }
        }
    }

    // --- Schoolhelp Heute: plan steps and personal tasks, saved on the phone until Schoolhelp has them ---

    private val todayFile = File(context.filesDir, "schoolbrief/today.json")
    private val pending = PendingQueue(File(context.filesDir, "schoolbrief/pending.json"))
    private val syncLock = kotlinx.coroutines.sync.Mutex()
    override val pendingChanges = MutableStateFlow(pending.all().size)

    override val today = MutableStateFlow(
        runCatching { json.decodeFromString(de.mm20.launcher2.schoolbrief.TodayData.serializer(), todayFile.readText()) }.getOrNull()
    )
    private var todayAt = 0L

    /** "Skúsiť znova teraz": the normal sync and recording check, now instead of on their timers. */
    override fun retryNow() {
        todayAt = 0
        lastRecordingCheck = 0
        refreshToday()
        checkRecordingIfDue()
    }

    /** While the Day page is visible: every 3 minutes, or every minute while changes are waiting. */
    override fun refreshToday() {
        val wait = if (pending.isEmpty()) 3 * 60_000L else 60_000L
        if (!auth.isLoggedIn || syncLock.isLocked || System.currentTimeMillis() - todayAt < wait) return
        scope.launch { sync() }
    }

    /** Sends waiting changes in order, then reads today.read; on failure the phone's copy stays (offline). */
    private suspend fun sync() = syncLock.withLock {
        todayAt = System.currentTimeMillis()
        val sent = flush()
        runCatching { mcp.appCallAll("today.read") }
            .onSuccess { r -> setToday(withPending(TodayParser.parse(r, LocalDate.now())).copy(offline = !sent)) }
            .onFailure { e ->
                Log.w(Tag, "today.read failed", e)
                setToday((today.value ?: de.mm20.launcher2.schoolbrief.TodayData(LocalDate.now().toString(), emptyList(), emptyList())).copy(offline = true))
            }
    }

    /** True when the queue is empty afterwards. Stops at the first failure so the order is kept. */
    private suspend fun flush(): Boolean {
        var allSent = true
        for (op in pending.all()) {
            // A task added and ticked off before it was ever uploaded needs no upload.
            if (op.localId != null && today.value?.todos?.any { it.id == op.localId && it.done } == true) {
                pending.remove(op); continue
            }
            val result = runCatching { mcp.appCall(op.action, op.args) }.onFailure { Log.w(Tag, "${op.action} failed", it) }
            val refused = (result.exceptionOrNull() as? IllegalStateException)?.message?.startsWith("Schoolhelp:") == true
            // Not reachable at all: keep everything in order and stop.
            if (result.isFailure && !refused) return false
            if (refused) {
                // A tool error: kept and tried again (see PendingOp.afterRefusal); the others still go out.
                val next = op.afterRefusal()
                if (next != null) { pending.replace(op, next); allSent = false; continue }
            }
            pending.remove(op)
            pendingChanges.value = pending.all().size
            if (op.action == "calendar.add_private") { lastAttempt = 0; prefs.edit().putLong(KeyDataAt, 0).apply() }
        }
        return allSent
    }

    /** Tasks still waiting in the queue, shown after the ones Schoolhelp returned. */
    private fun withPending(d: de.mm20.launcher2.schoolbrief.TodayData): de.mm20.launcher2.schoolbrief.TodayData {
        val local = pending.all().filter { it.localId != null && it.action == "today.add_todo" }.mapNotNull { op ->
            val text = op.args["text"]?.jsonPrimitive?.contentOrNull ?: return@mapNotNull null
            val old = today.value?.todos?.firstOrNull { it.id == op.localId }
            de.mm20.launcher2.schoolbrief.Todo(op.localId!!, text, old?.done ?: false, pending = true)
        }
        return d.copy(todos = d.todos + local)
    }

    private fun setToday(d: de.mm20.launcher2.schoolbrief.TodayData) {
        today.value = d
        runCatching {
            todayFile.parentFile?.mkdirs()
            todayFile.writeText(json.encodeToString(de.mm20.launcher2.schoolbrief.TodayData.serializer(), d))
        }
    }

    /** Applies a change on the phone at once, queues it for Schoolhelp and tries to send it. */
    private fun change(op: PendingOp?, local: (de.mm20.launcher2.schoolbrief.TodayData) -> de.mm20.launcher2.schoolbrief.TodayData) {
        val base = today.value ?: de.mm20.launcher2.schoolbrief.TodayData(LocalDate.now().toString(), emptyList(), emptyList())
        setToday(local(base))
        op?.let { pending.add(it); pendingChanges.value = pending.all().size }
        scope.launch { sync() }
    }

    override fun addEvent(kind: String, title: String, date: LocalDate, subject: String?) {
        val t = title.trim().take(200)
        if (t.isEmpty()) return
        val id = "local-${System.currentTimeMillis()}"
        pending.add(PendingOp("calendar.add_private", buildJsonObject {
            put("title", t); put("start", date.toString()); put("kind", kind)
            subject?.takeIf { it.isNotBlank() }?.let { put("folder", it) }
        }, localId = id))
        pendingChanges.value = pending.all().size
        // In the task list at once (and in the saved copy), until the next Schoolhelp refresh replaces it.
        feed.value?.let { f ->
            val task = de.mm20.launcher2.schoolbrief.SchoolTask(id = id, kind = kind, title = t, subject = subject.orEmpty(), due = date.toString())
            val next = f.copy(tasks = f.tasks + task)
            feed.value = next
            writeCache(next)
        }
        scope.launch { sync() }
    }

    override fun addTodo(text: String) {
        val t = text.trim().take(200)
        if (t.isEmpty()) return
        val id = "local-${System.currentTimeMillis()}"
        change(PendingOp("today.add_todo", buildJsonObject { put("text", t) }, localId = id)) {
            it.copy(todos = it.todos + de.mm20.launcher2.schoolbrief.Todo(id, t, false, pending = true))
        }
    }

    override fun setTodoDone(id: String, done: Boolean) {
        // A task that is not uploaded yet only changes on the phone (sent as a new task if it is open again).
        val op = if (id.startsWith("local-")) null
        else PendingOp("today.update_todo", buildJsonObject { put("todo_id", id); put("done", done) })
        change(op) { it.copy(todos = it.todos.map { t -> if (t.id == id) t.copy(done = done) else t }) }
    }

    override fun setStepDone(index: Int, done: Boolean) {
        change(PendingOp("today.set_step", buildJsonObject { put("i", index); put("done", done) })) {
            it.copy(steps = it.steps.map { s -> if (s.index == index) s.copy(done = done) else s })
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
        // Tasks saved offline go up as soon as Schoolhelp answers, also from the home screen.
        if (auth.isLoggedIn && !pending.isEmpty()) refreshToday()
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
        // Changes saved on the phone go first, so the list read next already has them.
        if (!pending.isEmpty()) runCatching { syncLock.withLock { flush() } }
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
        private const val DataMaxAgeMs = 15 * 60_000L
        private const val RetryMs = 5 * 60_000L

        /** The most recent slot at or before [now] (yesterday's evening slot before 06:30). */
        fun latestSlot(now: LocalDateTime): LocalDateTime {
            val today: LocalDate = now.toLocalDate()
            val slots = BriefRules.Slots.map { today.atTime(it) }.filter { !it.isAfter(now) }
            return slots.lastOrNull() ?: today.minusDays(1).atTime(BriefRules.Slots.last())
        }
    }
}
