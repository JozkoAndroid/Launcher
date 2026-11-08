package de.mm20.launcher2.ui.launcher.scaffold.components

import androidx.compose.foundation.background
import androidx.compose.ui.draw.clip
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.windowInsetsPadding
import de.mm20.launcher2.ui.launcher.helper.detectLongPressPassive
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.foundation.layout.fillMaxSize
import androidx.activity.compose.BackHandler
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.core.animateDpAsState
import androidx.compose.animation.expandVertically
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.shrinkVertically
import androidx.compose.foundation.ScrollState
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.layout.asPaddingValues
import androidx.compose.foundation.layout.ime
import androidx.compose.foundation.layout.navigationBars
import androidx.compose.foundation.layout.union
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.CenterAlignedTopAppBar
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.SideEffect
import androidx.compose.runtime.State
import androidx.compose.runtime.derivedStateOf
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.input.nestedscroll.NestedScrollConnection
import androidx.compose.ui.input.nestedscroll.NestedScrollSource
import androidx.compose.ui.input.nestedscroll.nestedScroll
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import androidx.compose.ui.zIndex
import de.mm20.launcher2.preferences.WidgetScreenTarget
import de.mm20.launcher2.ui.R
import de.mm20.launcher2.ui.launcher.scaffold.LauncherScaffoldState
import de.mm20.launcher2.ui.launcher.widgets.WidgetColumn
import kotlinx.coroutines.launch

internal class WidgetsComponent(
    private val target: WidgetScreenTarget
) : ScaffoldComponent() {

    companion object {
        /**
         * Cache for widget component instances.
         * Components are created lazily only when needed.
         */
        private val componentCache = mutableMapOf<WidgetScreenTarget, WidgetsComponent>()

        /**
         * Get or create a WidgetsComponent for the given target.
         * This ensures we reuse the same instance for each target.
         */
        fun forTarget(target: WidgetScreenTarget): WidgetsComponent {
            return componentCache.getOrPut(target) {
                WidgetsComponent(target)
            }
        }
    }

    private val scrollState = ScrollState(0)

    override val isAtTop: State<Boolean?> = derivedStateOf {
        !scrollState.canScrollBackward
    }

    override val isAtBottom: State<Boolean?> = derivedStateOf {
        !scrollState.canScrollForward
    }

    // In note widget
    override val hasIme: Boolean = true

    @Composable
    override fun Component(
        modifier: Modifier,
        insets: PaddingValues,
        state: LauncherScaffoldState
    ) {
        var editMode by rememberSaveable { mutableStateOf(false) }

        val scope = rememberCoroutineScope()
        val topPadding by animateDpAsState(if (editMode) 64.dp else 0.dp)

        val scrollConnection = remember(state) {
            object: NestedScrollConnection {
                override fun onPostScroll(
                    consumed: Offset,
                    available: Offset,
                    source: NestedScrollSource
                ): Offset {
                    val delta = consumed.y
                    if (!editMode) {
                        state.onComponentScroll(-delta)
                    }
                    return super.onPostScroll(consumed, available, source)
                }
            }
        }

        // Round 9 (device test): on the widget panel, swiping up past the end opens the app search, like
        // swiping up on home. A pull of 72dp beyond the end, or a fast upward fling there.
        val editNow by androidx.compose.runtime.rememberUpdatedState(editMode)
        val pullPx = with(androidx.compose.ui.platform.LocalDensity.current) { 72.dp.toPx() }
        val toSearch = remember(state) {
            object : NestedScrollConnection {
                var pulled = 0f
                // Round 11: armed while the finger pulls past an end, opened when it lifts. Opening mid-drag let
                // the same drag scroll the search list, which drops the keyboard (device test: no keyboard).
                // Round 14: past either end, up at the bottom or down at the top (device test: swipe down).
                var armed = false
                override fun onPostScroll(consumed: Offset, available: Offset, source: NestedScrollSource): Offset {
                    if (editNow || source != NestedScrollSource.UserInput) return Offset.Zero
                    if (available.y != 0f) {
                        pulled += kotlin.math.abs(available.y)
                        if (pulled > pullPx) armed = true
                    } else if (consumed.y != 0f) {
                        pulled = 0f; armed = false
                    }
                    return Offset.Zero
                }
                override suspend fun onPreFling(available: androidx.compose.ui.unit.Velocity): androidx.compose.ui.unit.Velocity {
                    // A pull past the end already happened (pulled > 0): the content is at its end.
                    val open = !editNow && (armed || (pulled > 0f && kotlin.math.abs(available.y) > 2500f))
                    pulled = 0f; armed = false
                    if (!open) return androidx.compose.ui.unit.Velocity.Zero
                    scope.launch {
                        state.onSearchBarTap()
                        state.isSearchBarFocused = true
                    }
                    return available
                }
            }
        }

        // Fork: the second widget screen is the side panel (swipe left), one scrolling page of blocks.
        val widgetsPage: @Composable () -> Unit = {
            Column(
                modifier = Modifier
                    .fillMaxSize()
                    .nestedScroll(scrollConnection)
                    // Fork: long press edits the panel, like the home screen.
                    .pointerInput(editMode) {
                        if (!editMode) detectLongPressPassive {
                            scope.launch { state.lock(hideSearchBar = true) }
                            editMode = true
                        }
                    }
                    .verticalScroll(scrollState)
                    .padding(horizontal = 8.dp)
                    .padding(top = topPadding)
                    .padding(insets)
                    .padding(bottom = 24.dp),
                horizontalAlignment = Alignment.CenterHorizontally,
            ) {
                WidgetColumn(
                    modifier = Modifier.widthIn(max = 900.dp).fillMaxHeight(),
                    editMode = editMode,
                    onEditModeChange = {
                        scope.launch { state.lock(hideSearchBar = true) }
                        editMode = it
                    },
                    parentId = target.id.toString(),
                )
            }
        }
        androidx.compose.foundation.layout.Box(modifier.nestedScroll(toSearch)) {
            // Round 23 (user: "combine the two pages on the right into one, scrollable"): one page of blocks;
            // the apps are an apps block among them, so apps and widgets mix like on a phone's home page.
            widgetsPage()
        }
        if (editMode) {
            BackHandler {
                editMode = false
                scope.launch { state.unlock() }
            }
        }
        AnimatedVisibility(
            editMode,
            modifier = Modifier.zIndex(10f),
            enter = fadeIn() + expandVertically(expandFrom = Alignment.Top),
            exit = shrinkVertically(shrinkTowards = Alignment.Top) + fadeOut(),
        ) {
            CenterAlignedTopAppBar(
                title = { Text(stringResource(R.string.menu_edit_widgets)) },
                navigationIcon = {
                    IconButton(
                        onClick = {
                            editMode = false
                            scope.launch { state.unlock() }
                        }
                    ) {
                        Icon(painterResource(R.drawable.arrow_back_24px), stringResource(R.string.action_done))
                    }
                },
                colors = TopAppBarDefaults.topAppBarColors(
                    containerColor = MaterialTheme.colorScheme.surfaceContainer
                )
            )
        }
    }

    override suspend fun onDismiss(state: LauncherScaffoldState) {
        super.onDismiss(state)
        // Leaving for search keeps the position, so coming back lands exactly where you were.
        if (state.currentComponent is SearchComponent) return
        scrollState.scrollTo(0)
    }
}
