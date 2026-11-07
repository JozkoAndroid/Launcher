package de.mm20.launcher2.ui.launcher.widgets.school

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Button
import androidx.compose.material3.Checkbox
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.rememberModalBottomSheetState
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
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewmodel.compose.viewModel
import de.mm20.launcher2.applications.AppRepository
import de.mm20.launcher2.icons.IconService
import de.mm20.launcher2.search.Application
import de.mm20.launcher2.search.SavableSearchable
import de.mm20.launcher2.services.tags.TagsService
import de.mm20.launcher2.ui.R
import de.mm20.launcher2.ui.component.ShapedLauncherIcon
import de.mm20.launcher2.widgets.FoldersWidget
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map
import org.koin.core.component.KoinComponent
import org.koin.core.component.inject

/** Folders = Kvaesitso tags, so they also work in the app list's search. */
class FoldersVM : ViewModel(), KoinComponent {
    private val tags: TagsService by inject()
    private val apps: AppRepository by inject()
    val icons: IconService by inject()

    val folders: Flow<List<String>> = tags.getAllTags()
    fun items(tag: String): Flow<List<SavableSearchable>> = tags.getTaggedItems(tag)
    val allApps: Flow<List<Application>> = apps.findMany().map { list -> list.sortedBy { (it.labelOverride ?: it.label).lowercase() } }

    fun save(oldName: String?, name: String, items: List<SavableSearchable>) {
        if (oldName == null) tags.createTag(name, items) else tags.updateTag(oldName, newName = name, items = items)
    }

    fun delete(tag: String) = tags.deleteTag(tag)
}

@OptIn(ExperimentalLayoutApi::class)
@Composable
fun FoldersWidget(widget: FoldersWidget) {
    val vm: FoldersVM = viewModel(key = "school-folders")
    val folders by vm.folders.collectAsState(emptyList())
    var open by rememberSaveable { mutableStateOf<String?>(null) }
    var editing by rememberSaveable { mutableStateOf<String?>(null) }
    var creating by rememberSaveable { mutableStateOf(false) }

    FolderCard {
        Text(
            stringResource(R.string.widget_name_folders),
            style = MaterialTheme.typography.titleMedium.copy(fontWeight = FontWeight.SemiBold),
        )
        if (folders.isEmpty()) {
            Spacer(Modifier.height(4.dp))
            Text(
                stringResource(R.string.folders_empty_hint), style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
        Spacer(Modifier.height(12.dp))
        FlowRow(horizontalArrangement = Arrangement.spacedBy(12.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
            for (f in folders) {
                val items by remember(f) { vm.items(f) }.collectAsState(emptyList())
                FolderTile(f, onClick = { open = f }) { MiniGrid(items.take(4), vm.icons) }
            }
            FolderTile(stringResource(R.string.folders_new), onClick = { creating = true }) {
                Icon(painterResource(R.drawable.add_20px), null, tint = MaterialTheme.colorScheme.onSurfaceVariant)
            }
        }
    }

    open?.let { tag ->
        FolderAppsSheet(tag, vm, onEdit = { editing = tag; open = null }, onDismiss = { open = null })
    }
    if (creating || editing != null) {
        FolderEditor(editing, vm, onDismiss = { creating = false; editing = null })
    }
}

@Composable
private fun FolderCard(content: @Composable androidx.compose.foundation.layout.ColumnScope.() -> Unit) {
    Column(
        Modifier.fillMaxWidth().padding(vertical = SchoolTokens.BlockPad)
            .clip(RoundedCornerShape(SchoolTokens.CardRadius))
            .background(MaterialTheme.colorScheme.surfaceContainer.copy(alpha = SchoolTokens.CardAlpha))
            .padding(SchoolTokens.Inset),
        content = content,
    )
}

/** A 60dp tile like a phone folder: up to 4 tiny icons, the name below. */
@Composable
private fun FolderTile(label: String, onClick: () -> Unit, content: @Composable () -> Unit) {
    Column(
        Modifier.width(72.dp).clip(RoundedCornerShape(SchoolTokens.ItemRadius)).clickable(role = Role.Button, onClick = onClick),
        horizontalAlignment = Alignment.CenterHorizontally,
    ) {
        Box(
            Modifier.size(60.dp).clip(RoundedCornerShape(18.dp)).background(MaterialTheme.colorScheme.surfaceContainerHighest),
            contentAlignment = Alignment.Center,
        ) { content() }
        Spacer(Modifier.height(6.dp))
        Text(
            label, style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurface,
            maxLines = 1, overflow = TextOverflow.Ellipsis, textAlign = TextAlign.Center,
        )
    }
}

@Composable
private fun MiniGrid(items: List<SavableSearchable>, icons: IconService) {
    Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
        for (r in 0..1) {
            Row(horizontalArrangement = Arrangement.spacedBy(4.dp)) {
                for (c in 0..1) {
                    val item = items.getOrNull(r * 2 + c)
                    if (item == null) Spacer(Modifier.size(22.dp)) else AppIcon(item, icons, 22.dp)
                }
            }
        }
    }
}

@Composable
private fun AppIcon(item: SavableSearchable, icons: IconService, size: Dp) {
    val px = with(LocalDensity.current) { size.roundToPx() }
    val icon by remember(item.key, px) { icons.getIcon(item, px) }.collectAsState(null)
    ShapedLauncherIcon(size = size, icon = { icon })
}

@OptIn(ExperimentalMaterial3Api::class, ExperimentalLayoutApi::class)
@Composable
private fun FolderAppsSheet(tag: String, vm: FoldersVM, onEdit: () -> Unit, onDismiss: () -> Unit) {
    val context = LocalContext.current
    val items by remember(tag) { vm.items(tag) }.collectAsState(emptyList())
    ModalBottomSheet(onDismissRequest = onDismiss) {
        Row(Modifier.fillMaxWidth().padding(horizontal = 24.dp), verticalAlignment = Alignment.CenterVertically) {
            Text(tag, style = MaterialTheme.typography.titleLarge.copy(fontWeight = FontWeight.SemiBold), modifier = Modifier.weight(1f))
            TextButton(onClick = onEdit) { Text(stringResource(R.string.folders_edit)) }
        }
        FlowRow(
            Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 8.dp).padding(bottom = 24.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp),
            maxItemsInEachRow = 4,
        ) {
            for (item in items) {
                Column(
                    Modifier.weight(1f).clip(RoundedCornerShape(SchoolTokens.ItemRadius))
                        .clickable(role = Role.Button) { item.launch(context, null); onDismiss() }
                        .padding(vertical = 8.dp),
                    horizontalAlignment = Alignment.CenterHorizontally,
                ) {
                    AppIcon(item, vm.icons, 52.dp)
                    Spacer(Modifier.height(6.dp))
                    Text(item.labelOverride ?: item.label, style = MaterialTheme.typography.bodyMedium, maxLines = 1, overflow = TextOverflow.Ellipsis)
                }
            }
            repeat((4 - items.size % 4) % 4) { Spacer(Modifier.weight(1f)) }
        }
    }
}

/** Create ([oldName] = null) or edit a folder: name plus a searchable list of apps to tick. */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun FolderEditor(oldName: String?, vm: FoldersVM, onDismiss: () -> Unit) {
    val apps by vm.allApps.collectAsState(emptyList())
    val current by remember(oldName) { oldName?.let { vm.items(it) } ?: kotlinx.coroutines.flow.flowOf(emptyList()) }.collectAsState(emptyList())
    var name by rememberSaveable(oldName) { mutableStateOf(oldName ?: "") }
    var query by rememberSaveable { mutableStateOf("") }
    var picked by remember(current) { mutableStateOf(current.map { it.key }.toSet()) }
    val shown = apps.filter { query.isBlank() || (it.labelOverride ?: it.label).contains(query, ignoreCase = true) }

    ModalBottomSheet(onDismissRequest = onDismiss, sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true)) {
        Column(Modifier.padding(horizontal = 24.dp)) {
            OutlinedTextField(name, { name = it }, label = { Text(stringResource(R.string.folders_name)) }, singleLine = true, modifier = Modifier.fillMaxWidth())
            Spacer(Modifier.height(8.dp))
            OutlinedTextField(query, { query = it }, placeholder = { Text(stringResource(R.string.folders_search)) }, singleLine = true, modifier = Modifier.fillMaxWidth())
            Spacer(Modifier.height(8.dp))
            Row(verticalAlignment = Alignment.CenterVertically) {
                // One primary action; delete stays a quiet text button (design rules).
                Button(
                    onClick = {
                        vm.save(oldName, name.trim(), apps.filter { it.key in picked })
                        onDismiss()
                    },
                    enabled = name.isNotBlank() && picked.isNotEmpty(),
                ) { Text(stringResource(R.string.folders_save)) }
                Spacer(Modifier.weight(1f))
                if (oldName != null) {
                    TextButton(onClick = { vm.delete(oldName); onDismiss() }) { Text(stringResource(R.string.folders_delete)) }
                }
            }
        }
        LazyColumn(Modifier.fillMaxWidth().heightIn(max = 520.dp)) {
            items(shown, key = { it.key }) { app ->
                val on = app.key in picked
                Row(
                    Modifier.fillMaxWidth().heightIn(min = 52.dp)
                        .clickable { picked = if (on) picked - app.key else picked + app.key }
                        .padding(horizontal = 24.dp),
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    AppIcon(app, vm.icons, 32.dp)
                    Spacer(Modifier.width(16.dp))
                    Text(app.labelOverride ?: app.label, style = MaterialTheme.typography.bodyLarge, modifier = Modifier.weight(1f), maxLines = 1, overflow = TextOverflow.Ellipsis)
                    Checkbox(checked = on, onCheckedChange = null)
                }
            }
        }
    }
}
