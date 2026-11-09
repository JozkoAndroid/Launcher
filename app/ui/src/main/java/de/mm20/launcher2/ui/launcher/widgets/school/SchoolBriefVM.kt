package de.mm20.launcher2.ui.launcher.widgets.school

import android.content.Context
import android.content.Intent
import android.net.Uri
import androidx.lifecycle.ViewModel
import de.mm20.launcher2.schoolbrief.BriefRepository
import de.mm20.launcher2.schoolbrief.BriefStatus
import de.mm20.launcher2.schoolbrief.Feed
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.flow
import org.koin.core.component.KoinComponent
import org.koin.core.component.inject
import java.time.LocalDateTime

class SchoolBriefVM : ViewModel(), KoinComponent {
    private val repository: BriefRepository by inject()

    val feed: Flow<Feed?> = repository.feed
    val status: StateFlow<BriefStatus> = repository.status
    val hasGeminiKey: StateFlow<Boolean> = repository.hasGeminiKey
    val pendingChanges: StateFlow<Int> = repository.pendingChanges
    val lastContact: StateFlow<Long> = repository.lastContact
    val lastProblem: StateFlow<String?> = repository.lastProblem
    fun retryNow() = repository.retryNow()

    /** Schoolhelp's lesson topics for a subject (stoff.read); null when offline or unknown. */
    suspend fun studyContext(folder: String) = runCatching { repository.studyContext(folder) }.getOrNull()

    /**
     * Ticks on every full minute while a school block is on screen. Each tick asks the repository
     * to refresh if due, so data only loads while the home screen is actually visible.
     */
    val now: Flow<LocalDateTime> = flow {
        while (true) {
            repository.refreshIfDue()
            val t = LocalDateTime.now()
            emit(t)
            delay(60_000L - (t.second * 1000L + t.nano / 1_000_000L))
        }
    }

    fun login(context: Context) = repository.login { url ->
        context.startActivity(Intent(Intent.ACTION_VIEW, Uri.parse(url)).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK))
    }

    fun logout() = repository.logout()
    fun setGeminiKey(key: String?) = repository.setGeminiKey(key)
    fun retry() = repository.refreshIfDue()

    fun addEvent(kind: String, title: String, date: java.time.LocalDate, subject: String?) = repository.addEvent(kind, title, date, subject)

    fun markDone(taskId: String, done: Boolean = true) = repository.markDone(taskId, done)

    val recording: StateFlow<de.mm20.launcher2.schoolbrief.Recording?> = repository.recording

    /** Schoolhelp confirms pause/stop with a tap, so its app opens right after the request. */
    fun pauseRecording(context: Context, pause: Boolean) {
        repository.pauseRecording(pause); Links.openSchoolhelp(context)
    }

    fun stopRecording(context: Context) {
        repository.stopRecording(); Links.openSchoolhelp(context)
    }
}

/** One quiet line in the Context block; music adds an inline play/pause button. */
data class ContextItem(
    val key: String,
    val text: String,
    @androidx.annotation.DrawableRes val icon: Int? = null,
    val onClick: (() -> Unit)? = null,
    val playing: Boolean? = null,
    val onTogglePlay: (() -> Unit)? = null,
    /** For the tile, glance and ring styles: big [value], small [label], optional 0..1 [progress]. */
    val value: String = text,
    val label: String? = null,
    val progress: Float? = null,
    /** Countdown in days (test tile): drawn as a row of dots, one per day left. */
    val days: Int? = null,
)
