package de.mm20.launcher2.ui.launcher.scaffold.components

import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.lazy.LazyListState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.State
import androidx.compose.runtime.derivedStateOf
import androidx.compose.ui.Modifier
import de.mm20.launcher2.ui.launcher.scaffold.LauncherScaffoldState
import de.mm20.launcher2.ui.launcher.widgets.school.SchoolChatPage
import de.mm20.launcher2.ui.launcher.widgets.school.SchoolWeekPage

/** Fork: swipe right from home. */
internal object SchoolChatComponent : ScaffoldComponent() {
    private val listState = LazyListState()
    override val isAtTop: State<Boolean?> = derivedStateOf { !listState.canScrollBackward }
    override val isAtBottom: State<Boolean?> = derivedStateOf { !listState.canScrollForward }
    override val showSearchBar: Boolean = false
    override val hasIme: Boolean = true
    override val survivesPause: Boolean = true

    @Composable
    override fun Component(modifier: Modifier, insets: PaddingValues, state: LauncherScaffoldState) {
        SchoolChatPage(insets, listState)
    }
}

/** Fork: swipe left from home. */
internal object SchoolWeekComponent : ScaffoldComponent() {
    override val showSearchBar: Boolean = false

    @Composable
    override fun Component(modifier: Modifier, insets: PaddingValues, state: LauncherScaffoldState) {
        SchoolWeekPage(insets)
    }
}
