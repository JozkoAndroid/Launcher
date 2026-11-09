package de.mm20.launcher2.schoolbrief

import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.flowOf

/** A lesson recording running in Schoolhelp. */
/** Markers for [BriefRepository.syncState]; anything else is an error text (round 26). */
const val SyncWorking = "…"
const val SyncDone = "✓"

data class Recording(val subject: String, val paused: Boolean, val seconds: Long)

/**
 * What a Fach covered this school year, from Schoolhelp's stoff.read, for test prep and explanations.
 * [lang]: the language the Fach is taught in (de, en, sk), from lessons.status.
 */
data class StudyContext(val folder: String, val lang: String?, val current: String?, val points: List<String>, val recent: List<String>) {
    fun describe(): String = buildString {
        current?.let { append("Aktuálna téma: ").append(it) }
        if (points.isNotEmpty()) append(" (").append(points.joinToString("; ")).append(")")
        if (recent.isNotEmpty()) append(". Posledné hodiny: ").append(recent.joinToString("; "))
    }.trim()
}

/** A personal task ("eigene Aufgabe") in Schoolhelp's Heute page; [pending] = saved on the phone, not uploaded yet. */
@kotlinx.serialization.Serializable
data class Todo(val id: String, val text: String, val done: Boolean, val pending: Boolean = false)

/** One step of Schoolhelp's AI day plan; [index] is what today.set_step expects. Text as Schoolhelp wrote it. */
@kotlinx.serialization.Serializable
data class PlanStep(val index: Int, val text: String, val minutes: Int?, val subject: String, val done: Boolean)

/**
 * Schoolhelp's Heute: today's plan (empty on days without one) and the personal tasks.
 * [offline] = Schoolhelp could not be reached; this is the copy saved on the phone.
 */
@kotlinx.serialization.Serializable
data class TodayData(val day: String, val steps: List<PlanStep>, val todos: List<Todo>, val offline: Boolean = false)

sealed interface BriefStatus {
    data object NeedsLogin : BriefStatus
    data object Ok : BriefStatus
    data object Refreshing : BriefStatus
    data class LoggingIn(val step: String) : BriefStatus
    /** Last refresh failed; the cached feed stays on screen (dimmed once older than 12 h). */
    data class Error(val message: String) : BriefStatus
}

/** Source of the home screen feed: Schoolhelp + news feeds + Gemini, cached on the device. */
interface BriefRepository {
    val feed: Flow<Feed?>
    val status: StateFlow<BriefStatus>
    val hasGeminiKey: StateFlow<Boolean>

    /** Cheap to call often (every minute while the home screen is visible); does work only when due. */
    fun refreshIfDue()

    /** Opens Schoolhelp's login page via [openUrl]; the browser returns through [handleLoginCallback]. */
    fun login(openUrl: (String) -> Unit)

    /** launcherbrief://oauth?code=…&state=… from the bounce page. */
    fun handleLoginCallback(uri: String)
    fun logout()

    /** Stored encrypted on the device; null removes it (the brief then uses the template). */
    fun setGeminiKey(key: String?)

    /** Lesson recording in Schoolhelp, checked every 2 minutes while the home screen is visible. */
    val recording: StateFlow<Recording?>

    /** Ticks a task done (or not done again, for undo) in Schoolhelp, and updates the list at once. */
    fun markDone(taskId: String, done: Boolean = true)

    /** stoff.read for one Fach (folder name as in the timetable); null when offline or unknown. */
    suspend fun studyContext(folder: String): StudyContext? = null

    /** Schoolhelp answers the teacher's last question in the running recording (lessons.answer_now). */
    suspend fun answerNow(): Result<String> = Result.failure(IllegalStateException("offline"))

    /** Unread messages in Schoolhelp chats (not muted); checked with the recording. */
    val unreadChats: StateFlow<Int>

    /** Schoolhelp's Heute (plan steps and personal tasks); refreshed by [refreshToday]. */
    val today: StateFlow<TodayData?> get() = MutableStateFlow(null)
    fun refreshToday() {}
    /** Adds a personal task (today.add_todo); shows at once, the server id follows. */
    fun addTodo(text: String) {}
    fun setTodoDone(id: String, done: Boolean) {}
    fun setStepDone(index: Int, done: Boolean) {}

    /**
     * Adds a homework, test or appointment only the student sees (calendar.add_private); shown in the
     * task list at once and kept on the phone until Schoolhelp has it. [kind] = SchoolTask kind.
     */
    fun addEvent(kind: String, title: String, date: java.time.LocalDate, subject: String?) {}

    /** Changes saved on the phone that Schoolhelp does not have yet. */
    val pendingChanges: StateFlow<Int> get() = MutableStateFlow(0)

    /** When Schoolhelp last answered (ms), and the last failure in words (settings diagnostics, round 25). */
    val lastContact: StateFlow<Long> get() = MutableStateFlow(0L)
    val lastProblem: StateFlow<String?> get() = MutableStateFlow(null)

    val syncState: StateFlow<String?> get() = MutableStateFlow(null)

    /** Send waiting changes and reread now. */
    fun retryNow() {}

    /** Pause/continue or stop: Schoolhelp asks for a tap to confirm, so the caller opens Schoolhelp. */
    fun pauseRecording(pause: Boolean)
    fun stopRecording()
}

/** Invented data for screenshots and tests. */
class SampleBriefRepository : BriefRepository {
    override val feed: Flow<Feed?> = flowOf(SampleFeed.create())
    override val status = MutableStateFlow<BriefStatus>(BriefStatus.Ok)
    override val hasGeminiKey = MutableStateFlow(false)
    override fun refreshIfDue() {}
    override fun login(openUrl: (String) -> Unit) {}
    override fun handleLoginCallback(uri: String) {}
    override fun logout() {}
    override fun setGeminiKey(key: String?) {}
    override val recording = MutableStateFlow<Recording?>(null)
    override val unreadChats = MutableStateFlow(0)
    override fun markDone(taskId: String, done: Boolean) {}
    override fun pauseRecording(pause: Boolean) {}
    override fun stopRecording() {}
}
