package de.mm20.launcher2.ui.launcher.scaffold.components

import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.lazy.LazyListState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.State
import androidx.compose.runtime.derivedStateOf
import androidx.compose.ui.Modifier
import de.mm20.launcher2.ui.launcher.scaffold.LauncherScaffoldState
import de.mm20.launcher2.ui.launcher.widgets.school.SchoolWeekPage

/** Fork: swipe right from home: "Môj deň" (timeline, own tasks, assistant). Id kept for saved settings. */
internal object SchoolChatComponent : ScaffoldComponent() {
    private val listState = LazyListState()
    override val isAtTop: State<Boolean?> = derivedStateOf { !listState.canScrollBackward }
    override val isAtBottom: State<Boolean?> = derivedStateOf { !listState.canScrollForward }
    override val showSearchBar: Boolean = false
    override val hasIme: Boolean = true
    override val survivesPause: Boolean = true

    /** False as soon as the page starts leaving: the page drops focus so the keyboard goes with it. */
    val shown = androidx.compose.runtime.mutableStateOf(false)

    override fun onPreActivate(state: LauncherScaffoldState) { shown.value = true }
    override fun onPreDismiss(state: LauncherScaffoldState) { shown.value = false }

    @Composable
    override fun Component(modifier: Modifier, insets: PaddingValues, state: LauncherScaffoldState) {
        // The scaffold slides a page through [modifier]; without it the page sat in place over home (device test).
        androidx.compose.foundation.layout.Box(modifier) {
            de.mm20.launcher2.ui.launcher.widgets.school.DayPage(listState, shown, progress = { state.currentProgress })
        }
    }
}

/** Fork: swipe left from home. */
internal object SchoolWeekComponent : ScaffoldComponent() {
    override val showSearchBar: Boolean = false

    @Composable
    override fun Component(modifier: Modifier, insets: PaddingValues, state: LauncherScaffoldState) {
        androidx.compose.foundation.layout.Box(modifier) { SchoolWeekPage(insets) }
    }
}
