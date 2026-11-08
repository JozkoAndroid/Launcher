package de.mm20.launcher2.ui.launcher.widgets.school

import android.content.Context
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.combinedClickable
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.foundation.gestures.detectDragGesturesAfterLongPress
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.asPaddingValues
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBars
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.itemsIndexed
import androidx.compose.foundation.lazy.grid.rememberLazyGridState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.hapticfeedback.HapticFeedbackType
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalHapticFeedback
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.IntOffset
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import androidx.lifecycle.viewmodel.compose.viewModel
import de.mm20.launcher2.search.SavableSearchable
import de.mm20.launcher2.services.favorites.FavoritesService
import de.mm20.launcher2.ui.R
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.launch
import org.koin.core.component.KoinComponent
import org.koin.core.component.inject

/** One cell of the apps page: an app (by key) or a folder (a Kvaesitso tag). */
sealed interface PageEntry {
    val id: String
    data class App(val item: SavableSearchable) : PageEntry { override val id = "app:" + item.key }
    data class Folder(val name: String) : PageEntry { override val id = "folder:$name" }
}

/**
 * The apps page (round 10): a normal home screen page of apps and folders, in the user's order.
 * The order is a short list of ids on the phone; every folder (tag) is on the page.
 */
class AppsPageVM(app: android.app.Application) : androidx.lifecycle.AndroidViewModel(app), KoinComponent {
    private val favorites: FavoritesService by inject()
    private val prefs = app.getSharedPreferences("apps_page", Context.MODE_PRIVATE)
    private val order = MutableStateFlow(prefs.getString("order", null)?.split('\n')?.filter { it.isNotBlank() })
    val folders = FoldersVM()

    val entries = combine(order, folders.allApps, folders.folders) { ids, apps, tags ->
        val byKey = apps.associateBy { "app:" + it.key }
        val listed = ids.orEmpty().mapNotNull { id ->
            when {
                id.startsWith("folder:") -> id.removePrefix("folder:").takeIf { it in tags }?.let { PageEntry.Folder(it) }
                else -> byKey[id]?.let { PageEntry.App(it) }
            }
        }
        // Folders made anywhere show up on the page.
        listed + tags.filter { t -> listed.none { it is PageEntry.Folder && it.name == t } }.map { PageEntry.Folder(it) }
    }

    init {
        // First start: the most used apps after the dock's four.
        if (order.value == null) viewModelScope.launch {
            val used = favorites.getFavorites(includeTypes = listOf("app"), minPinnedLevel = de.mm20.launcher2.searchable.PinnedLevel.NotPinned, limit = 24).first()
            save(used.drop(4).map { "app:" + it.key })
        }
    }

    private fun save(ids: List<String>) {
        order.value = ids
        prefs.edit().putString("order", ids.joinToString("\n")).apply()
    }

    fun move(current: List<PageEntry>, from: Int, to: Int) {
        val ids = current.map { it.id }.toMutableList()
        if (from !in ids.indices || to !in ids.indices) return
        ids.add(to, ids.removeAt(from))
        save(ids)
    }

    fun add(current: List<PageEntry>, keys: List<String>) = save(current.map { it.id } + keys.map { "app:$it" }.filter { id -> current.none { it.id == id } })
    fun remove(current: List<PageEntry>, entry: PageEntry) = save(current.map { it.id } - entry.id)

    /** Puts the app into the folder and takes it off the page, as on a phone. */
    fun moveToFolder(current: List<PageEntry>, app: PageEntry.App, folder: String) = viewModelScope.launch {
        val inside = folders.items(folder).first()
        folders.save(folder, folder, inside + app.item)
        remove(current, app)
    }

    fun launched(item: SavableSearchable) = favorites.reportLaunch(item)
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun AppsPage(modifier: Modifier = Modifier) {
    val vm: AppsPageVM = viewModel(key = "school-apps-page")
    val entries by vm.entries.collectAsState(emptyList())
    val context = LocalContext.current
    val haptic = LocalHapticFeedback.current
    val grid = rememberLazyGridState()
    var dragging by remember { mutableStateOf<String?>(null) }
    var menu by remember { mutableStateOf<PageEntry?>(null) }
    var openFolder by rememberSaveable { mutableStateOf<String?>(null) }
    var editFolder by rememberSaveable { mutableStateOf<String?>(null) }
    var newFolder by rememberSaveable { mutableStateOf(false) }
    var adding by rememberSaveable { mutableStateOf(false) }
    val top = WindowInsets.statusBars.asPaddingValues().calculateTopPadding()
    val current by androidx.compose.runtime.rememberUpdatedState(entries)

    /** Long press, then drag: the cell follows the finger through the grid; without moving: its options. */
    fun Modifier.draggableCell(id: String): Modifier = pointerInput(id) {
        var moved = false
        fun indexAt(p: Offset) = grid.layoutInfo.visibleItemsInfo.firstOrNull { info ->
            p.x.toInt() in info.offset.x until info.offset.x + info.size.width &&
                p.y.toInt() in info.offset.y until info.offset.y + info.size.height
        }?.index?.takeIf { it < current.size }
        detectDragGesturesAfterLongPress(
            onDragStart = { moved = false; dragging = id; haptic.performHapticFeedback(HapticFeedbackType.LongPress) },
            onDrag = { change, _ ->
                change.consume()
                val info = grid.layoutInfo.visibleItemsInfo.firstOrNull { it.key == id } ?: return@detectDragGesturesAfterLongPress
                val from = current.indexOfFirst { it.id == id }
                val to = indexAt(Offset(info.offset.x + change.position.x, info.offset.y + change.position.y)) ?: return@detectDragGesturesAfterLongPress
                if (to != from) { vm.move(current, from, to); moved = true }
            },
            onDragEnd = { if (!moved) menu = current.firstOrNull { it.id == id }; dragging = null },
            onDragCancel = { dragging = null },
        )
    }

    LazyVerticalGrid(
        // As many 80dp cells as fit: 4 on the cover, about 10 unfolded, more on a tablet.
        columns = GridCells.Adaptive(80.dp),
        state = grid,
        modifier = modifier.fillMaxSize(),
        contentPadding = PaddingValues(start = 12.dp, end = 12.dp, top = top + 76.dp, bottom = 160.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp),
    ) {
        itemsIndexed(entries, key = { _, e -> e.id }) { _, e ->
            val lifted = dragging == e.id
            // The lifted cell grows slightly while it is carried.
            Box(Modifier.animateItem().graphicsLayer { val s = if (lifted) 1.08f else 1f; scaleX = s; scaleY = s }) {
                when (e) {
                    is PageEntry.App -> PageCell(e.item.labelOverride ?: e.item.label, Modifier.draggableCell(e.id), onClick = {
                        if (e.item.launch(context, null)) vm.launched(e.item)
                    }) { AppIcon(e.item, vm.folders.icons, 54.dp) }
                    is PageEntry.Folder -> {
                        val inside by remember(e.name) { vm.folders.items(e.name) }.collectAsState(emptyList())
                        PageCell(e.name, Modifier.draggableCell(e.id), onClick = { openFolder = e.name }) {
                            Box(
                                Modifier.size(54.dp).clip(RoundedCornerShape(18.dp)).background(MaterialTheme.colorScheme.surfaceContainerHigh.copy(alpha = 0.9f)),
                                contentAlignment = Alignment.Center,
                            ) { MiniGrid(inside.take(4), vm.folders.icons) }
                        }
                    }
                }
            }
        }
        // Adding is quiet (ghost tile): the page's job is opening apps.
        item(key = "add") {
            PageCell(stringResource(R.string.apps_add), Modifier, onClick = { adding = true }) {
                Box(
                    Modifier.size(54.dp).clip(RoundedCornerShape(18.dp)).background(schoolContentColor().copy(alpha = 0.12f)),
                    contentAlignment = Alignment.Center,
                ) { Icon(painterResource(R.drawable.add_24px), null, tint = schoolContentColor()) }
            }
        }
    }

    menu?.let { e ->
        ModalBottomSheet(onDismissRequest = { menu = null }, sheetState = androidx.compose.material3.rememberModalBottomSheetState(skipPartiallyExpanded = true)) {
            Column(Modifier.padding(bottom = 24.dp)) {
                when (e) {
                    is PageEntry.App -> {
                        val folders by vm.folders.folders.collectAsState(emptyList())
                        for (f in folders) SheetAction(stringResource(R.string.apps_to_folder, f)) { vm.moveToFolder(entries, e, f); menu = null }
                        SheetAction(stringResource(R.string.apps_remove)) { vm.remove(entries, e); menu = null }
                    }
                    is PageEntry.Folder -> SheetAction(stringResource(R.string.folders_edit)) { editFolder = e.name; menu = null }
                }
                SheetAction(stringResource(R.string.folders_new)) { newFolder = true; menu = null }
            }
        }
    }
    openFolder?.let { f -> FolderAppsSheet(f, vm.folders, onEdit = { editFolder = f; openFolder = null }, onDismiss = { openFolder = null }) }
    editFolder?.let { f -> FolderEditor(f, vm.folders, onDismiss = { editFolder = null }) }
    if (newFolder) FolderEditor(null, vm.folders, onDismiss = { newFolder = false })
    if (adding) {
        AppPickerSheet(
            vm = vm.folders, initialName = "", withName = false,
            initial = entries.filterIsInstance<PageEntry.App>().map { it.item.key }.toSet(),
            onSave = { _, apps -> vm.add(entries, apps.map { it.key }) },
            onDelete = null, onDismiss = { adding = false },
        )
    }
}

/** Icon over a one-line label, on the wallpaper (label with a soft shadow for bright photos). */
@OptIn(androidx.compose.foundation.ExperimentalFoundationApi::class)
@Composable
private fun PageCell(label: String, gesture: Modifier, onClick: () -> Unit, icon: @Composable () -> Unit) {
    val content = schoolContentColor()
    Column(
        Modifier.fillMaxWidth().clip(RoundedCornerShape(SchoolTokens.ItemRadius))
            // The long click only stops the tap from firing; the drag detector inside handles the long press.
            .combinedClickable(role = Role.Button, onClick = onClick, onLongClick = {})
            .then(gesture)
            .padding(vertical = 6.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
    ) {
        icon()
        Spacer(Modifier.height(6.dp))
        Text(
            label, color = content, maxLines = 1, overflow = TextOverflow.Ellipsis, textAlign = TextAlign.Center,
            style = MaterialTheme.typography.bodySmall.copy(
                fontSize = 13.sp,
                shadow = androidx.compose.ui.graphics.Shadow(androidx.compose.ui.graphics.Color.Black.copy(alpha = if (content.red > 0.5f) 0.4f else 0f), blurRadius = 8f),
            ),
            modifier = Modifier.padding(horizontal = 4.dp),
        )
    }
}

@Composable
private fun SheetAction(label: String, onClick: () -> Unit) {
    Text(
        label, style = MaterialTheme.typography.bodyLarge,
        modifier = Modifier.fillMaxWidth().heightIn(min = 52.dp).clickable(onClick = onClick).padding(horizontal = 24.dp, vertical = 16.dp),
    )
}
