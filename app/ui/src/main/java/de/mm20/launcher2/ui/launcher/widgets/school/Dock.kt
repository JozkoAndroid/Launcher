package de.mm20.launcher2.ui.launcher.widgets.school

import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.background
import androidx.compose.foundation.combinedClickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Icon
import androidx.compose.material3.LocalContentColor
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.layout.boundsInWindow
import androidx.compose.ui.layout.onGloballyPositioned
import androidx.compose.runtime.setValue
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.luminance
import androidx.compose.ui.platform.LocalConfiguration
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewmodel.compose.viewModel
import de.mm20.launcher2.icons.IconService
import de.mm20.launcher2.search.SavableSearchable
import de.mm20.launcher2.services.favorites.FavoritesService
import de.mm20.launcher2.ui.R
import de.mm20.launcher2.ui.component.ShapedLauncherIcon
import de.mm20.launcher2.ui.launcher.sheets.LocalBottomSheetManager
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.launch
import org.koin.core.component.KoinComponent
import org.koin.core.component.inject

class DockVM : ViewModel(), KoinComponent {
    private val favorites: FavoritesService by inject()
    val icons: IconService by inject()

    /** Apps the user put in the dock (pinned apps, in their order). */
    private fun chosen(count: Int) = favorites.getFavorites(
        includeTypes = listOf("app"),
        minPinnedLevel = de.mm20.launcher2.searchable.PinnedLevel.AutomaticallySorted,
        limit = count,
    )

    /**
     * Round 15 (user: "how can I manually add apps to the dock"): the apps chosen with a long press on the
     * dock, exactly those. Until something is chosen: the most used apps (a new install starts filled).
     */
    fun apps(count: Int): Flow<List<SavableSearchable>> = kotlinx.coroutines.flow.combine(
        chosen(count),
        favorites.getFavorites(includeTypes = listOf("app"), minPinnedLevel = de.mm20.launcher2.searchable.PinnedLevel.NotPinned, limit = count),
    ) { picked, used -> if (picked.isNotEmpty()) picked else used }

    /**
     * Makes [apps] the dock, in this order. Kvaesitso's favorites store re-pins everything at once, so
     * pinned items that are not apps (contacts, files, ...) are passed through unchanged; other apps are
     * unpinned (they stay in the app list and search).
     */
    fun setDock(apps: List<SavableSearchable>) {
        kotlinx.coroutines.CoroutineScope(kotlinx.coroutines.Dispatchers.IO).launch {
            val manual = favorites.getFavorites(minPinnedLevel = de.mm20.launcher2.searchable.PinnedLevel.ManuallySorted).first()
            val auto = favorites.getFavorites(
                minPinnedLevel = de.mm20.launcher2.searchable.PinnedLevel.AutomaticallySorted,
                maxPinnedLevel = de.mm20.launcher2.searchable.PinnedLevel.AutomaticallySorted,
            ).first()
            favorites.updateFavorites(
                manuallySorted = apps + manual.filter { it.domain != "app" },
                automaticallySorted = auto.filter { it.domain != "app" },
            )
        }
    }

    fun launched(item: SavableSearchable) = favorites.reportLaunch(item)
}

/** 4 apps on the cover screen, 7 unfolded, 9 on a tablet. */
@Composable
internal fun dockCount(): Int = LocalConfiguration.current.screenWidthDp.let { w -> if (w >= 1000) 9 else if (w >= 600) 7 else 4 }

/**
 * The floating dock (round 7): favorite apps in one raised bar, and next to it the home screen's single
 * primary action, the assistant, as an ink circle. Long press on the bar edits the favorites.
 */
@Composable
fun FloatingDock(modifier: Modifier = Modifier) {
    val vm: DockVM = viewModel(key = "school-dock")
    val count = dockCount()
    val apps by remember(count) { vm.apps(count) }.collectAsState(emptyList())
    val context = LocalContext.current
    var editing by androidx.compose.runtime.saveable.rememberSaveable { androidx.compose.runtime.mutableStateOf(false) }
    DockContent(
        count = apps.size,
        modifier = modifier,
        icon = { i, size -> DockAppIcon(apps[i], vm.icons, size) },
        label = { i -> apps[i].labelOverride ?: apps[i].label },
        onApp = { i, bounds ->
            val item = apps[i]
            // Zoom out of the icon, as Kvaesitso's app list does; counted like any launch.
            val view = (context as? android.app.Activity)?.window?.decorView
            val options = if (bounds != null && view != null) {
                androidx.core.app.ActivityOptionsCompat.makeScaleUpAnimation(view, bounds.left, bounds.top, bounds.width(), bounds.height())
            } else androidx.core.app.ActivityOptionsCompat.makeBasic()
            if (item.launch(context, options.toBundle())) vm.launched(item)
        },
        onEdit = { editing = true },
        onAssistant = { AssistantLauncher.open(context) },
    )
    if (editing) {
        val folders: FoldersVM = viewModel(key = "school-folders")
        AppPickerSheet(
            vm = folders,
            initialName = "",
            withName = false,
            initial = apps.map { it.key }.toSet(),
            onSave = { _, picked ->
                // Kept apps stay in their place, new ones follow.
                val keep = apps.filter { a -> picked.any { it.key == a.key } }
                vm.setDock(keep + picked.filter { p -> keep.none { it.key == p.key } })
            },
            onDelete = null,
            onDismiss = { editing = false },
            title = stringResource(R.string.dock_edit_title),
            saveLabel = stringResource(R.string.dock_save),
            limit = count,
            allowEmpty = true,
        )
    }
}

@Composable
private fun DockAppIcon(item: SavableSearchable, icons: IconService, size: Dp) {
    val px = with(LocalDensity.current) { size.roundToPx() }
    val icon by remember(item.key, px) { icons.getIcon(item, px) }.collectAsState(null)
    ShapedLauncherIcon(size = size, icon = { icon })
}

@OptIn(ExperimentalFoundationApi::class)
@Composable
internal fun DockContent(
    count: Int,
    modifier: Modifier = Modifier,
    icon: @Composable (Int, Dp) -> Unit,
    label: (Int) -> String,
    onApp: (Int, android.graphics.Rect?) -> Unit,
    onEdit: () -> Unit,
    onAssistant: () -> Unit,
) {
    val wide = LocalConfiguration.current.screenWidthDp >= 600
    val slot = if (wide) 62.dp else 52.dp
    val iconSize = if (wide) 52.dp else 44.dp
    Row(modifier.padding(horizontal = 16.dp), verticalAlignment = Alignment.CenterVertically) {
        if (count > 0) {
            PillBar {
                for (i in 0 until count) {
                    val name = label(i)
                    // Plain holder, not state: positions change during animations and must not recompose the dock.
                    val bounds = remember { android.graphics.Rect() }
                    Box(
                        Modifier
                            .padding(horizontal = 2.dp)
                            .size(slot)
                            .onGloballyPositioned { c ->
                                val r = c.boundsInWindow()
                                bounds.set(r.left.toInt(), r.top.toInt(), r.right.toInt(), r.bottom.toInt())
                            }
                            .clip(CircleShape)
                            .combinedClickable(role = Role.Button, onClick = { onApp(i, bounds.takeUnless { it.isEmpty }) }, onLongClick = onEdit)
                            .semantics { contentDescription = name },
                        contentAlignment = Alignment.Center,
                    ) { icon(i, iconSize) }
                }
            }
            Spacer(Modifier.width(10.dp))
        }
        // Ink on the wallpaper: the home screen's one primary action.
        InkButton(R.drawable.mic_24px, stringResource(R.string.assistant_button), schoolContentColor(), onAssistant)
    }
}
