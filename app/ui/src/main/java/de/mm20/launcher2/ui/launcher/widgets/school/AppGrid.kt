package de.mm20.launcher2.ui.launcher.widgets.school

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.gestures.detectDragGesturesAfterLongPress
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Button
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.Text
import androidx.compose.material3.rememberModalBottomSheetState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Shadow
import androidx.compose.ui.graphics.graphicsLayer
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
import androidx.lifecycle.viewmodel.compose.viewModel
import de.mm20.launcher2.ui.R
import de.mm20.launcher2.widgets.AppGridConfig
import de.mm20.launcher2.widgets.AppGridWidget
import kotlin.math.roundToInt

/**
 * The apps block (round 23): chosen apps as big icons on the wallpaper, like a phone's home page, in the
 * user's order. Hold and drag an app to move it; hold without moving for its options; the quiet "+" adds
 * apps. Several blocks can sit between other blocks, so apps and widgets mix on one scrolling page.
 */
@Composable
fun AppGridWidget(widget: AppGridWidget, onWidgetUpdate: (de.mm20.launcher2.widgets.Widget) -> Unit) {
    val vm: FoldersVM = viewModel(key = "school-folders")
    val all by vm.allApps.collectAsState(emptyList())
    val context = LocalContext.current
    val haptic = LocalHapticFeedback.current
    val byKey = remember(all) { all.associateBy { it.key } }
    val apps = widget.config.apps.mapNotNull { byKey[it] }
    var picking by remember { mutableStateOf(false) }
    var menuFor by remember { mutableStateOf<String?>(null) }
    val current by rememberUpdatedState(widget)
    fun save(keys: List<String>) = onWidgetUpdate(current.copy(config = AppGridConfig(keys)))

    BoxWithConstraints(Modifier.fillMaxWidth().padding(vertical = 6.dp)) {
        // Cells of at least 84dp, 4 across on a phone; icons as large as the cell allows, up to 64dp.
        val cols = (maxWidth / 84.dp).toInt().coerceAtLeast(4)
        val cellW = maxWidth / cols
        val cellH = cellW + 22.dp
        val icon = (cellW * 0.68f).coerceAtMost(64.dp)
        val count = apps.size + 1 // + the add cell
        val rows = (count + cols - 1) / cols
        var dragKey by remember { mutableStateOf<String?>(null) }
        var drag by remember { mutableStateOf(Offset.Zero) }

        Box(Modifier.fillMaxWidth().height(cellH * rows)) {
            apps.forEachIndexed { i, app ->
                val lifted = dragKey == app.key
                val x = cellW * (i % cols)
                val y = cellH * (i / cols)
                val keys by rememberUpdatedState(widget.config.apps)
                Column(
                    Modifier
                        .offset(x, y)
                        .width(cellW)
                        .then(if (lifted) Modifier.offset { IntOffset(drag.x.roundToInt(), drag.y.roundToInt()) } else Modifier)
                        .graphicsLayer { val s = if (lifted) 1.1f else 1f; scaleX = s; scaleY = s }
                        .clip(RoundedCornerShape(SchoolTokens.ItemRadius))
                        .clickable(role = Role.Button) { if (app.launch(context, null)) vm.launched(app) }
                        .pointerInput(app.key) {
                            var moved = false
                            detectDragGesturesAfterLongPress(
                                onDragStart = { moved = false; dragKey = app.key; drag = Offset.Zero; haptic.performHapticFeedback(HapticFeedbackType.LongPress) },
                                onDrag = { change, amount -> change.consume(); drag += amount; moved = moved || drag.getDistance() > 12f },
                                onDragEnd = {
                                    if (!moved) menuFor = app.key
                                    else {
                                        // Drop: the cell under the icon's centre takes it.
                                        val from = keys.indexOf(app.key)
                                        val cx = (x.toPx() + cellW.toPx() / 2 + drag.x) / cellW.toPx()
                                        val cy = (y.toPx() + cellH.toPx() / 2 + drag.y) / cellH.toPx()
                                        val to = (cy.toInt().coerceIn(0, rows - 1) * cols + cx.toInt().coerceIn(0, cols - 1)).coerceIn(0, keys.lastIndex)
                                        if (from >= 0 && to != from) save(keys.toMutableList().apply { add(to, removeAt(from)) })
                                    }
                                    dragKey = null; drag = Offset.Zero
                                },
                                onDragCancel = { dragKey = null; drag = Offset.Zero },
                            )
                        }
                        .padding(vertical = 6.dp),
                    horizontalAlignment = Alignment.CenterHorizontally,
                ) {
                    AppIcon(app, vm.icons, icon)
                    Spacer(Modifier.height(6.dp))
                    CellLabel(app.labelOverride ?: app.label)
                }
            }
            // The quiet add cell, last.
            val i = apps.size
            Column(
                Modifier.offset(cellW * (i % cols), cellH * (i / cols)).width(cellW)
                    .clip(RoundedCornerShape(SchoolTokens.ItemRadius))
                    .clickable(role = Role.Button) { picking = true }
                    .padding(vertical = 6.dp),
                horizontalAlignment = Alignment.CenterHorizontally,
            ) {
                Box(
                    Modifier.size(icon).clip(RoundedCornerShape(icon * 0.3f)).background(schoolContentColor().copy(alpha = 0.12f)),
                    contentAlignment = Alignment.Center,
                ) { Icon(painterResource(R.drawable.add_24px), null, tint = schoolContentColor()) }
                Spacer(Modifier.height(6.dp))
                CellLabel(stringResource(if (apps.isEmpty()) R.string.appgrid_pick else R.string.apps_add))
            }
        }
    }

    if (picking) AppGridPicker(widget, vm, onSave = { save(it) }, onDismiss = { picking = false })
    menuFor?.let { key -> AppGridMenu(onRemove = { save(widget.config.apps - key); menuFor = null }, onEdit = { picking = true; menuFor = null }, onDismiss = { menuFor = null }) }
}

@Composable
private fun CellLabel(text: String) {
    val content = schoolContentColor()
    Text(
        text, color = content, maxLines = 1, overflow = TextOverflow.Ellipsis, textAlign = TextAlign.Center,
        style = MaterialTheme.typography.bodySmall.copy(
            fontSize = 13.sp,
            shadow = Shadow(Color.Black.copy(alpha = if (content.red > 0.5f) 0.4f else 0f), blurRadius = 8f),
        ),
        modifier = Modifier.padding(horizontal = 4.dp),
    )
}

/** Picking the block's apps: kept apps keep their place, new ones go to the end. */
@Composable
private fun AppGridPicker(widget: AppGridWidget, vm: FoldersVM, onSave: (List<String>) -> Unit, onDismiss: () -> Unit) {
    AppPickerSheet(
        vm = vm, initialName = "", withName = false, initial = widget.config.apps.toSet(),
        onSave = { _, picked ->
            val keys = picked.map { it.key }
            onSave(widget.config.apps.filter { it in keys } + keys.filter { it !in widget.config.apps })
        },
        onDelete = null, onDismiss = onDismiss,
        title = stringResource(R.string.widget_name_appgrid), saveLabel = stringResource(R.string.folders_save), allowEmpty = true,
    )
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun AppGridMenu(onRemove: () -> Unit, onEdit: () -> Unit, onDismiss: () -> Unit) {
    ModalBottomSheet(onDismissRequest = onDismiss, sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true)) {
        Column(Modifier.padding(bottom = 24.dp)) {
            for ((label, action) in listOf(stringResource(R.string.appgrid_remove) to onRemove, stringResource(R.string.appgrid_edit) to onEdit)) {
                Text(
                    label, style = MaterialTheme.typography.bodyLarge,
                    modifier = Modifier.fillMaxWidth().heightIn(min = 52.dp).clickable(onClick = action).padding(horizontal = 24.dp, vertical = 16.dp),
                )
            }
        }
    }
}

/** In the block's settings (tune button in edit mode): the same picker. */
@Composable
fun ConfigureAppGrid(widget: AppGridWidget, onWidgetUpdated: (de.mm20.launcher2.widgets.Widget) -> Unit) {
    val vm: FoldersVM = viewModel(key = "school-folders")
    var picking by remember { mutableStateOf(false) }
    Button(onClick = { picking = true }, modifier = Modifier.padding(vertical = 8.dp)) { Text(stringResource(R.string.appgrid_edit)) }
    if (picking) AppGridPicker(widget, vm, onSave = { onWidgetUpdated(widget.copy(config = AppGridConfig(it))) }, onDismiss = { picking = false })
}
