package de.mm20.launcher2.ui.launcher.apppage

import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.background
import androidx.compose.foundation.combinedClickable
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.gestures.detectVerticalDragGestures
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyListState
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import de.mm20.launcher2.ui.R
import kotlinx.coroutines.launch

/** App page tokens: same scale as the home blocks (approved design plan). */
private object AppPageTokens {
    val Gutter = 24.dp
    val MaxWidth = 560.dp
    val SearchHeight = 48.dp
    val Radius = 12.dp
    val PinnedRow = 56.dp
    val PinnedIcon = 32.dp
    val ListRow = 48.dp
    val ListIcon = 24.dp
    val IconGap = 16.dp
    val SectionGap = 24.dp
    val ScrubberWidth = 40.dp
    const val SecondaryAlpha = 0.8f
}

/** Callbacks from the app page; kept apart from the VM so the screen renders in tests. */
class AppPageActions(
    val onQueryChange: (String) -> Unit,
    val onLaunch: (AppEntry) -> Unit,
    val onSetPinned: (AppEntry, Boolean) -> Unit,
    val onSetHidden: (AppEntry, Boolean) -> Unit,
    val onAppInfo: (AppEntry) -> Unit,
)

@Composable
fun AppPageContent(
    model: AppPageModel,
    query: String,
    pinnedKeys: Set<String>,
    actions: AppPageActions,
    icon: @Composable (AppEntry, Dp) -> Unit,
    modifier: Modifier = Modifier,
    contentPadding: PaddingValues = PaddingValues(),
    listState: LazyListState = rememberLazyListState(),
    userScrollEnabled: Boolean = true,
) {
    val scope = rememberCoroutineScope()
    // Rows before the alphabetical list: search, pinned rows, and the gap item.
    val listOffset = 1 + model.pinned.size + 1
    Box(modifier.fillMaxSize(), contentAlignment = Alignment.TopCenter) {
        Row(Modifier.widthIn(max = AppPageTokens.MaxWidth).fillMaxHeight()) {
            LazyColumn(
                state = listState,
                modifier = Modifier.weight(1f).fillMaxHeight(),
                contentPadding = contentPadding,
                userScrollEnabled = userScrollEnabled,
            ) {
                item(key = "search") {
                    SearchField(query, actions.onQueryChange)
                }
                items(model.pinned, key = { "p:" + it.key }) { e ->
                    AppRow(e, pinned = true, isPinned = true, pinnedFull = false, actions, icon)
                }
                item(key = "gap") { Spacer(Modifier.height(if (model.pinned.isEmpty()) 8.dp else AppPageTokens.SectionGap)) }
                items(model.list, key = { "l:" + it.key }) { e ->
                    AppRow(e, pinned = false, isPinned = e.key in pinnedKeys, pinnedFull = model.pinnedCount >= AppPageLogic.MaxPinned, actions, icon)
                }
                if (query.isNotBlank() && model.pinned.isEmpty() && model.list.isEmpty()) {
                    item(key = "empty") { EmptyResult(query) { actions.onQueryChange("") } }
                }
            }
            if (query.isBlank() && model.letters.size > 1) {
                Scrubber(
                    letters = model.letters.map { it.first },
                    modifier = Modifier.padding(contentPadding),
                    onLetter = { i -> scope.launch { listState.scrollToItem(listOffset + model.letters[i].second) } },
                )
            }
        }
    }
}

@Composable
private fun SearchField(query: String, onChange: (String) -> Unit) {
    val onSurface = MaterialTheme.colorScheme.onSurface
    Box(
        Modifier
            .fillMaxWidth()
            .padding(horizontal = AppPageTokens.Gutter, vertical = 8.dp)
            .heightIn(min = AppPageTokens.SearchHeight)
            .clip(RoundedCornerShape(AppPageTokens.Radius))
            .background(MaterialTheme.colorScheme.surfaceContainerHigh)
            .padding(horizontal = 16.dp),
        contentAlignment = Alignment.CenterStart,
    ) {
        Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(12.dp)) {
            Icon(
                painterResource(R.drawable.search_24px),
                contentDescription = null,
                tint = onSurface.copy(alpha = AppPageTokens.SecondaryAlpha),
                modifier = Modifier.size(20.dp),
            )
            Box(Modifier.weight(1f)) {
                if (query.isEmpty()) {
                    Text(
                        stringResource(R.string.app_page_search_hint),
                        style = MaterialTheme.typography.bodyLarge,
                        color = onSurface.copy(alpha = AppPageTokens.SecondaryAlpha),
                    )
                }
                BasicTextField(
                    value = query,
                    onValueChange = onChange,
                    singleLine = true,
                    // 16sp minimum on mobile (design rules).
                    textStyle = MaterialTheme.typography.bodyLarge.copy(color = onSurface, fontSize = 16.sp),
                    cursorBrush = SolidColor(MaterialTheme.colorScheme.primary),
                    keyboardOptions = KeyboardOptions(imeAction = ImeAction.Go),
                    modifier = Modifier.fillMaxWidth(),
                )
            }
        }
    }
}

@OptIn(ExperimentalFoundationApi::class)
@Composable
private fun AppRow(
    e: AppEntry,
    pinned: Boolean,
    isPinned: Boolean,
    pinnedFull: Boolean,
    actions: AppPageActions,
    icon: @Composable (AppEntry, Dp) -> Unit,
) {
    var menu by remember { mutableStateOf(false) }
    val onSurface = MaterialTheme.colorScheme.onSurface
    Box {
        Row(
            Modifier
                .fillMaxWidth()
                .heightIn(min = if (pinned) AppPageTokens.PinnedRow else AppPageTokens.ListRow)
                .padding(horizontal = 8.dp)
                .clip(RoundedCornerShape(AppPageTokens.Radius))
                .combinedClickable(onClick = { actions.onLaunch(e) }, onLongClick = { menu = true })
                .padding(horizontal = AppPageTokens.Gutter - 8.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            icon(e, if (pinned) AppPageTokens.PinnedIcon else AppPageTokens.ListIcon)
            Spacer(Modifier.width(AppPageTokens.IconGap))
            Text(
                text = e.label,
                style = if (pinned) MaterialTheme.typography.titleLarge.copy(fontWeight = FontWeight.Medium, fontSize = 20.sp)
                else MaterialTheme.typography.bodyLarge.copy(fontSize = 16.sp),
                color = onSurface,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
                modifier = Modifier.weight(1f, fill = false),
            )
            if (e.searchOnly) {
                Spacer(Modifier.width(8.dp))
                Text(
                    stringResource(R.string.app_page_hidden_hint),
                    style = MaterialTheme.typography.bodyLarge.copy(fontSize = 16.sp),
                    color = onSurface.copy(alpha = AppPageTokens.SecondaryAlpha),
                    maxLines = 1,
                )
            }
        }
        DropdownMenu(expanded = menu, onDismissRequest = { menu = false }) {
            when {
                isPinned -> DropdownMenuItem(
                    text = { Text(stringResource(R.string.app_page_unpin)) },
                    onClick = { actions.onSetPinned(e, false); menu = false },
                )
                pinnedFull -> DropdownMenuItem(
                    // Disabled with the reason in the label (design rules: explain disabled options).
                    text = { Text(stringResource(R.string.app_page_pin_full)) },
                    enabled = false,
                    onClick = {},
                )
                else -> DropdownMenuItem(
                    text = { Text(stringResource(R.string.app_page_pin)) },
                    onClick = { actions.onSetPinned(e, true); menu = false },
                )
            }
            DropdownMenuItem(
                text = { Text(stringResource(if (e.searchOnly) R.string.app_page_unhide else R.string.app_page_hide)) },
                onClick = { actions.onSetHidden(e, !e.searchOnly); menu = false },
            )
            DropdownMenuItem(
                text = { Text(stringResource(R.string.app_page_info)) },
                onClick = { actions.onAppInfo(e); menu = false },
            )
        }
    }
}

@Composable
private fun EmptyResult(query: String, onClear: () -> Unit) {
    Column(Modifier.fillMaxWidth().padding(horizontal = AppPageTokens.Gutter, vertical = 16.dp)) {
        Text(
            stringResource(R.string.app_page_no_results, query),
            style = MaterialTheme.typography.bodyLarge,
            color = MaterialTheme.colorScheme.onSurface.copy(alpha = AppPageTokens.SecondaryAlpha),
        )
        TextButton(onClick = onClear, modifier = Modifier.padding(top = 4.dp)) {
            Text(stringResource(R.string.app_page_clear_search))
        }
    }
}

/** Letter strip on the right edge: tap or drag to jump. */
@Composable
private fun Scrubber(letters: List<String>, modifier: Modifier, onLetter: (Int) -> Unit) {
    var active by remember { mutableStateOf<Int?>(null) }
    var heightPx by remember { mutableStateOf(1) }
    fun pick(y: Float) {
        val i = ((y / heightPx) * letters.size).toInt().coerceIn(0, letters.lastIndex)
        if (i != active) {
            active = i; onLetter(i)
        }
    }
    val jumpLabel = stringResource(R.string.app_page_letter_index)
    Column(
        modifier
            .width(AppPageTokens.ScrubberWidth)
            .fillMaxHeight()
            .padding(vertical = 72.dp)
            .semantics { contentDescription = jumpLabel }
            .pointerInput(letters) {
                heightPx = size.height.coerceAtLeast(1)
                detectTapGestures { pick(it.y); active = null }
            }
            .pointerInput(letters) {
                heightPx = size.height.coerceAtLeast(1)
                detectVerticalDragGestures(
                    onDragStart = { pick(it.y) },
                    onDragEnd = { active = null },
                    onDragCancel = { active = null },
                ) { change, _ -> pick(change.position.y) }
            },
        verticalArrangement = Arrangement.SpaceEvenly,
        horizontalAlignment = Alignment.CenterHorizontally,
    ) {
        letters.forEachIndexed { i, l ->
            Text(
                l,
                textAlign = TextAlign.Center,
                style = MaterialTheme.typography.labelMedium.copy(fontWeight = FontWeight.SemiBold),
                color = if (i == active) MaterialTheme.colorScheme.primary
                else MaterialTheme.colorScheme.onSurface.copy(alpha = AppPageTokens.SecondaryAlpha),
            )
        }
    }
}
