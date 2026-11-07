package de.mm20.launcher2.schoolbrief

import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.flowOf

/** A lesson recording running in Schoolhelp. */
data class Recording(val subject: String, val paused: Boolean, val seconds: Long)

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

    /** Unread messages in Schoolhelp chats (not muted); checked with the recording. */
    val unreadChats: StateFlow<Int>

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
    override fun pauseRecording(pause: Boolean) {}
    override fun stopRecording() {}
}
