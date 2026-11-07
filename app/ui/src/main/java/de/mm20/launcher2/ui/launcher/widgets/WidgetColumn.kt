package de.mm20.launcher2.ui.launcher.widgets

import androidx.compose.foundation.gestures.rememberDraggableState
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.SnackbarDuration
import androidx.compose.material3.SnackbarResult
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.key
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.layout.onPlaced
import androidx.compose.ui.layout.positionInParent
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.IntOffset
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.LocalLifecycleOwner
import androidx.lifecycle.lifecycleScope
import androidx.lifecycle.viewmodel.compose.viewModel
import de.mm20.launcher2.ui.R
import de.mm20.launcher2.ui.base.LocalAppWidgetHost
import de.mm20.launcher2.ui.ktx.animateTo
import de.mm20.launcher2.ui.launcher.sheets.WidgetPickerSheet
import de.mm20.launcher2.ui.locals.LocalSnackbarHostState
import de.mm20.launcher2.widgets.AppWidget
import kotlinx.coroutines.launch
import java.util.UUID

@Composable
fun WidgetColumn(
    modifier: Modifier = Modifier,
    editMode: Boolean = false,
    onEditModeChange: (Boolean) -> Unit,
    parentId: String,
) {

    val context = LocalContext.current
    val lifecycleOwner = LocalLifecycleOwner.current
    val viewModel: WidgetsVM = viewModel(
        key = "widgets-column-$parentId",
        factory = WidgetsVM.Factory(parentId),
    )
    val snackbarHostState = LocalSnackbarHostState.current

    var addNewWidget by rememberSaveable { mutableStateOf(false) }


    Column(
        modifier = modifier.fillMaxWidth(),
    ) {
        val scope = rememberCoroutineScope()
        Column {
            val widgets by viewModel.widgets.collectAsState()
            // Fork: unfolded Fold / tablet: the blocks become a two-column dashboard (edit mode stays one column).
            val wide = androidx.compose.ui.platform.LocalConfiguration.current.screenWidthDp >= WideDashboardMinWidthDp
            if (wide && !editMode) {
                WideDashboard(widgets, onWidgetUpdate = { viewModel.updateWidget(it) })
                return@Column
            }
            val swapThresholds = remember(widgets) {
                Array(widgets.size) { floatArrayOf(0f, 0f) }
            }
            val widgetsWithIndex = remember(widgets) { widgets.withIndex() }
            for ((i, widget) in widgetsWithIndex) {
                // Fork (round 7): sheet blocks that follow each other join into one sheet.
                val seg = de.mm20.launcher2.ui.launcher.widgets.school.Segment(
                    joinedTop = !editMode && i > 0 && isSheet(widgets[i - 1]) && isSheet(widget),
                    joinedBottom = !editMode && i < widgets.lastIndex && isSheet(widgets[i + 1]) && isSheet(widget),
                )
                key(widget.id) { androidx.compose.runtime.CompositionLocalProvider(de.mm20.launcher2.ui.launcher.widgets.school.LocalSegment provides seg) {
                    var dragOffsetAfterSwap = remember<Float?> { null }
                    val offsetY =
                        remember(widgets) { mutableFloatStateOf(dragOffsetAfterSwap ?: 0f) }

                    LaunchedEffect(widgets) {
                        dragOffsetAfterSwap = null
                    }

                    val widgetHost = LocalAppWidgetHost.current

                    WidgetItem(
                        widget = widget,
                        editMode = editMode,
                        onWidgetAdd = { widget, offset ->
                            viewModel.addWidget(widget, i + offset)
                        },
                        onWidgetRemove = {
                            lifecycleOwner.lifecycleScope.launch {
                                viewModel.removeWidget(widget)
                                val result = snackbarHostState.showSnackbar(
                                    message = context.getString(R.string.widget_removed),
                                    actionLabel = context.getString(R.string.action_undo),
                                    duration = SnackbarDuration.Short,
                                )
                                if (result == SnackbarResult.ActionPerformed) {
                                    viewModel.addWidget(widget, i)
                                } else {
                                    if (widget is AppWidget) {
                                        widgetHost.deleteAppWidgetId(widget.config.widgetId)
                                    }
                                }
                            }
                        },
                        onWidgetUpdate = {
                            viewModel.updateWidget(it)
                        },
                        modifier = Modifier
                            .fillMaxWidth()
                            .onPlaced {
                                swapThresholds[i][0] = it.positionInParent().y
                                swapThresholds[i][1] = it.positionInParent().y + it.size.height
                            }
                            .padding(top = if (i == 0) 0.dp else if (seg.joinedTop) de.mm20.launcher2.ui.launcher.widgets.school.SchoolTokens.Seam else de.mm20.launcher2.ui.launcher.widgets.school.SchoolTokens.GroupGap)
                            .offset {
                                IntOffset(0, offsetY.value.toInt())
                            },
                        draggableState = rememberDraggableState {
                            scope.launch {
                                val newOffset = offsetY.value + it
                                offsetY.value = newOffset
                                if (i > 0 && newOffset < (swapThresholds[i - 1][0] - swapThresholds[i - 1][1])) {
                                    if (dragOffsetAfterSwap == null) {
                                        dragOffsetAfterSwap =
                                            swapThresholds[i - 1][1] - swapThresholds[i - 1][0] + newOffset
                                        viewModel.moveUp(i)
                                    }
                                }
                                if (i < widgets.lastIndex && newOffset > (swapThresholds[i + 1][1] - swapThresholds[i + 1][0])) {
                                    if (dragOffsetAfterSwap == null) {
                                        dragOffsetAfterSwap =
                                            swapThresholds[i + 1][0] - swapThresholds[i + 1][1] + newOffset
                                        viewModel.moveDown(i)
                                    }
                                }
                            }
                        },
                        onDragStopped = {
                            scope.launch {
                                offsetY.animateTo(0f)
                            }
                        },
                    )
                } }
            }
        }

        val editButton by viewModel.editButton.collectAsState()
        if (editMode || editButton == true) {
            val title = stringResource(
                if (editMode) R.string.widget_add_widget
                else R.string.menu_edit_widgets,
            )

            Button(
                modifier = Modifier
                    .align(Alignment.CenterHorizontally)
                    .padding(vertical = 8.dp),
                contentPadding = ButtonDefaults.ButtonWithIconContentPadding,
                onClick = {
                    if (!editMode) {
                        onEditModeChange(true)
                    } else {
                        addNewWidget = true
                    }
                },
            ) {
                Icon(
                    modifier = Modifier
                        .padding(end = ButtonDefaults.IconSpacing)
                        .size(ButtonDefaults.IconSize),
                    painter = painterResource(
                        if (editMode) R.drawable.add_20px else R.drawable.edit_20px,
                    ),
                    contentDescription = null,
                )
                Text(title)
            }

        }
    }

    WidgetPickerSheet(
        expanded = addNewWidget,
        onDismiss = { addNewWidget = false },
        onWidgetSelected = {
            viewModel.addWidget(it)
            addNewWidget = false
        },
    )
}

/** Fork: blocks drawn as a section of the sheet (joined with their neighbors); others float on the wallpaper. */
internal fun isSheet(w: de.mm20.launcher2.widgets.Widget): Boolean = when (w) {
    is de.mm20.launcher2.widgets.BriefWidget, is de.mm20.launcher2.widgets.TasksWidget, is de.mm20.launcher2.widgets.WeekWidget,
    is de.mm20.launcher2.widgets.TestPrepWidget, is de.mm20.launcher2.widgets.FoldersWidget -> true
    is de.mm20.launcher2.widgets.LessonWidget, is de.mm20.launcher2.widgets.ContextWidget, is de.mm20.launcher2.widgets.ChipWidget,
    is de.mm20.launcher2.widgets.AssistantWidget, is de.mm20.launcher2.widgets.MyDayWidget -> false
    is AppWidget -> w.config.background
    else -> true // Kvaesitso's own widgets (weather, calendar, notes, ...) share the sheet look
}

/** Fork: width from which the home blocks sit in two columns (Fold 5 inner screen ~ 775dp, tablets). */
internal const val WideDashboardMinWidthDp = 600

/**
 * Fork: two-column dashboard for wide screens. Masonry: each block goes into the column that is
 * shorter so far (device test: alternating left/right left one column half empty after reordering).
 * The Gemini button spans the full width below.
 */
@Composable
private fun WideDashboard(widgets: List<de.mm20.launcher2.widgets.Widget>, onWidgetUpdate: (de.mm20.launcher2.widgets.Widget) -> Unit) {
    val full = widgets.filter { it is de.mm20.launcher2.widgets.AssistantWidget }
    val rest = widgets.filterNot { it is de.mm20.launcher2.widgets.AssistantWidget }
    val gap = 12.dp
    androidx.compose.ui.layout.Layout(
        content = {
            for (w in rest) key(w.id) { WidgetItem(widget = w, onWidgetUpdate = onWidgetUpdate, modifier = Modifier.fillMaxWidth()) }
        },
    ) { measurables, constraints ->
        val gapPx = gap.roundToPx()
        val colWidth = (constraints.maxWidth - gapPx) / 2
        val heights = intArrayOf(0, 0)
        val placed = measurables.map { m ->
            val p = m.measure(androidx.compose.ui.unit.Constraints.fixedWidth(colWidth))
            // Empty blocks (a chip with nothing to show) take no slot.
            if (p.height == 0) return@map Triple(p, 0, -1)
            val col = if (heights[0] <= heights[1]) 0 else 1
            val y = heights[col]
            heights[col] += p.height + gapPx
            Triple(p, y, col)
        }
        val height = (heights.maxOrNull() ?: 0).coerceAtLeast(0)
        layout(constraints.maxWidth, height) {
            for ((p, y, col) in placed) if (col >= 0) p.placeRelative(col * (colWidth + gapPx), y)
        }
    }
    for (w in full) {
        key(w.id) {
            WidgetItem(widget = w, onWidgetUpdate = onWidgetUpdate, modifier = Modifier.fillMaxWidth().padding(top = 8.dp))
        }
    }
}
