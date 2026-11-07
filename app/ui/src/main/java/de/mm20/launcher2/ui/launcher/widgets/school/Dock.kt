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
import org.koin.core.component.KoinComponent
import org.koin.core.component.inject

class DockVM : ViewModel(), KoinComponent {
    private val favorites: FavoritesService by inject()
    val icons: IconService by inject()

    /** The first [count] favorite apps: pinned ones in their order, then the most used. */
    fun apps(count: Int): Flow<List<SavableSearchable>> = favorites.getFavorites(includeTypes = listOf("app"), limit = count)
}

/** 4 apps on the cover screen, 7 when the screen is wide (unfolded Fold, tablet). */
@Composable
internal fun dockCount(): Int = if (LocalConfiguration.current.screenWidthDp >= 600) 7 else 4

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
    val sheets = LocalBottomSheetManager.current
    DockContent(
        count = apps.size,
        modifier = modifier,
        icon = { i, size -> DockAppIcon(apps[i], vm.icons, size) },
        label = { i -> apps[i].labelOverride ?: apps[i].label },
        onApp = { i -> apps[i].launch(context, null) },
        onEdit = { sheets.showEditFavoritesSheet() },
        onAssistant = { AssistantLauncher.open(context) },
    )
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
    onApp: (Int) -> Unit,
    onEdit: () -> Unit,
    onAssistant: () -> Unit,
) {
    // Ink on the wallpaper: near-white on dark content, near-black on light (the primary-button rule).
    val content = schoolContentColor()
    val ink = if (content.luminance() > 0.5f) Color(0xFFF4F4F6) else Color(0xFF16181C)
    val onInk = if (content.luminance() > 0.5f) Color(0xFF16181C) else Color(0xFFF4F4F6)
    val shadowTint = Color(0xFF0A0C10)
    Row(modifier.padding(horizontal = 16.dp), verticalAlignment = Alignment.CenterVertically) {
        if (count > 0) {
            Row(
                Modifier
                    .height(SchoolTokens.DockHeight)
                    .shadow(16.dp, RoundedCornerShape(32.dp), ambientColor = shadowTint, spotColor = shadowTint)
                    .clip(RoundedCornerShape(32.dp))
                    .background(MaterialTheme.colorScheme.surfaceContainerHigh.copy(alpha = 0.94f))
                    .padding(horizontal = 8.dp),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(4.dp),
            ) {
                for (i in 0 until count) {
                    val name = label(i)
                    Box(
                        Modifier
                            .size(52.dp)
                            .clip(CircleShape)
                            .combinedClickable(role = Role.Button, onClick = { onApp(i) }, onLongClick = onEdit)
                            .semantics { contentDescription = name },
                        contentAlignment = Alignment.Center,
                    ) { icon(i, 44.dp) }
                }
            }
            Spacer(Modifier.width(10.dp))
        }
        val askLabel = stringResource(R.string.assistant_button)
        Box(
            Modifier
                .size(SchoolTokens.DockHeight)
                .shadow(16.dp, CircleShape, ambientColor = shadowTint, spotColor = shadowTint)
                .clip(CircleShape)
                .background(ink)
                .combinedClickable(role = Role.Button, onClick = onAssistant)
                .semantics { contentDescription = askLabel },
            contentAlignment = Alignment.Center,
        ) {
            Icon(painterResource(R.drawable.mic_24px), contentDescription = null, tint = onInk, modifier = Modifier.size(26.dp))
        }
    }
}
