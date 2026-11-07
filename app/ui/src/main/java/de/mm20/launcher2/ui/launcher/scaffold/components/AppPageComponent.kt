package de.mm20.launcher2.ui.launcher.scaffold.components

import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.lazy.LazyListState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.State
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.derivedStateOf
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalDensity
import androidx.lifecycle.viewmodel.compose.viewModel
import de.mm20.launcher2.ui.component.ShapedLauncherIcon
import de.mm20.launcher2.ui.launcher.apppage.AppPageActions
import de.mm20.launcher2.ui.launcher.apppage.AppPageContent
import de.mm20.launcher2.ui.launcher.apppage.AppPageVM
import de.mm20.launcher2.ui.launcher.scaffold.LauncherScaffoldState

/** Fork: Niagara-style app page (8 pinned apps, alphabetical list, scrubber, search). */
internal object AppPageComponent : ScaffoldComponent() {

    private val listState = LazyListState()

    override val isAtTop: State<Boolean?> = derivedStateOf { !listState.canScrollBackward }
    override val isAtBottom: State<Boolean?> = derivedStateOf { !listState.canScrollForward }

    /** The page has its own search field; a second search bar would compete as the primary action. */
    override val showSearchBar: Boolean = false
    override val hasIme: Boolean = true

    @Composable
    override fun Component(modifier: Modifier, insets: PaddingValues, state: LauncherScaffoldState) {
        val vm: AppPageVM = viewModel()
        val model by vm.model.collectAsState()
        val query by vm.query.collectAsState()
        val pinnedKeys by vm.pinnedKeySet.collectAsState()
        val context = LocalContext.current
        val density = LocalDensity.current

        // Leaving the page clears the search and returns to the top.
        LaunchedEffect(isActive) {
            if (!isActive) {
                vm.query.value = ""
                listState.scrollToItem(0)
            }
        }

        val actions = remember(vm) {
            AppPageActions(
                onQueryChange = { vm.query.value = it },
                onLaunch = { vm.launch(context, it) },
                onSetPinned = { e, p -> vm.setPinned(e, p) },
                onSetHidden = { e, h -> vm.setHidden(e, h) },
                onAppInfo = { vm.openAppInfo(context, it) },
            )
        }
        val m = model ?: return
        AppPageContent(
            model = m,
            query = query,
            pinnedKeys = pinnedKeys,
            actions = actions,
            icon = { e, size ->
                val px = with(density) { size.roundToPx() }
                val icon by remember(e.key, px) { vm.icon(e, px) }.collectAsState(null)
                ShapedLauncherIcon(size = size, icon = { icon })
            },
            modifier = modifier,
            contentPadding = insets,
            listState = listState,
            userScrollEnabled = !state.isDragged,
        )
    }
}
