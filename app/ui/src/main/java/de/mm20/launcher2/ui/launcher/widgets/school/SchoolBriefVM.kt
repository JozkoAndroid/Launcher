package de.mm20.launcher2.ui.launcher.widgets.school

import androidx.lifecycle.ViewModel
import de.mm20.launcher2.schoolbrief.BriefRepository
import de.mm20.launcher2.schoolbrief.Feed
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.flow
import org.koin.core.component.KoinComponent
import org.koin.core.component.inject
import java.time.LocalDateTime

class SchoolBriefVM : ViewModel(), KoinComponent {
    private val repository: BriefRepository by inject()

    val feed: Flow<Feed?> = repository.feed

    /** Ticks on every full minute so the lesson block and due labels stay current. */
    val now: Flow<LocalDateTime> = flow {
        while (true) {
            val t = LocalDateTime.now()
            emit(t)
            delay(60_000L - (t.second * 1000L + t.nano / 1_000_000L))
        }
    }
}

/** One quiet line in the Context block. Step 1: sample items; real rules come in build step 5. */
data class ContextItem(val key: String, val text: String)
