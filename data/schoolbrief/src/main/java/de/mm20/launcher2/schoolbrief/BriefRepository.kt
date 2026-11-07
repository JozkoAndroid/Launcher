package de.mm20.launcher2.schoolbrief

import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.flowOf

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

    /** Opens Schoolhelp's login page via [openUrl] and waits for the browser to come back. */
    fun login(openUrl: (String) -> Unit)
    fun logout()

    /** Stored encrypted on the device; null removes it (the brief then uses the template). */
    fun setGeminiKey(key: String?)
}

/** Invented data for screenshots and tests. */
class SampleBriefRepository : BriefRepository {
    override val feed: Flow<Feed?> = flowOf(SampleFeed.create())
    override val status = MutableStateFlow<BriefStatus>(BriefStatus.Ok)
    override val hasGeminiKey = MutableStateFlow(false)
    override fun refreshIfDue() {}
    override fun login(openUrl: (String) -> Unit) {}
    override fun logout() {}
    override fun setGeminiKey(key: String?) {}
}
