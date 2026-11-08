package de.mm20.launcher2.ui.launcher.widgets

import android.appwidget.AppWidgetManager
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.core.animateDpAsState
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.foundation.gestures.DraggableState
import androidx.compose.foundation.gestures.Orientation
import androidx.compose.foundation.gestures.draggable
import androidx.compose.foundation.gestures.rememberDraggableState
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.zIndex
import de.mm20.launcher2.ui.R
import de.mm20.launcher2.ui.component.LauncherCard
import de.mm20.launcher2.ui.launcher.sheets.ConfigureWidgetSheet
import de.mm20.launcher2.ui.launcher.widgets.calendar.CalendarWidget
import de.mm20.launcher2.ui.launcher.widgets.external.AppWidget
import de.mm20.launcher2.ui.launcher.widgets.favorites.AppsWidget
import de.mm20.launcher2.ui.launcher.widgets.music.MusicWidget
import de.mm20.launcher2.ui.launcher.widgets.notes.NotesWidget
import de.mm20.launcher2.ui.launcher.widgets.weather.WeatherWidget
import de.mm20.launcher2.ui.theme.transparency.transparency
import de.mm20.launcher2.widgets.AppWidget
import de.mm20.launcher2.widgets.CalendarWidget
import de.mm20.launcher2.widgets.AppsWidget
import de.mm20.launcher2.widgets.MusicWidget
import de.mm20.launcher2.widgets.NotesWidget
import de.mm20.launcher2.widgets.BriefWidget
import de.mm20.launcher2.widgets.ContextWidget
import de.mm20.launcher2.widgets.AssistantWidget
import de.mm20.launcher2.widgets.FoldersWidget
import de.mm20.launcher2.widgets.ChipWidget
import de.mm20.launcher2.widgets.WeekWidget
import de.mm20.launcher2.widgets.MyDayWidget
import de.mm20.launcher2.widgets.TestPrepWidget
import de.mm20.launcher2.widgets.LessonWidget
import de.mm20.launcher2.widgets.TasksWidget
import de.mm20.launcher2.ui.launcher.widgets.school.BriefWidget
import de.mm20.launcher2.ui.launcher.widgets.school.ContextWidget
import de.mm20.launcher2.ui.launcher.widgets.school.AssistantWidget
import de.mm20.launcher2.ui.launcher.widgets.school.LessonWidget
import de.mm20.launcher2.ui.launcher.widgets.school.TasksWidget
import de.mm20.launcher2.widgets.WeatherWidget
import de.mm20.launcher2.widgets.Widget

@Composable
fun WidgetItem(
    widget: Widget,
    modifier: Modifier = Modifier,
    editMode: Boolean = false,
    onWidgetAdd: (widget: Widget, offset: Int) -> Unit = { _, _ -> },
    onWidgetUpdate: (widget: Widget) -> Unit = {},
    onWidgetRemove: () -> Unit = {},
    draggableState: DraggableState = rememberDraggableState {},
    onDragStopped: () -> Unit = {}
) {
    val context = LocalContext.current

    var configure by rememberSaveable { mutableStateOf(false) }

    var isDragged by remember { mutableStateOf(false) }
    val elevation by animateDpAsState(if (isDragged) 8.dp else 0.dp)

    val backgroundOpacity by animateFloatAsState(
        if (widget is AppWidget && !widget.config.background && !editMode) 0f else MaterialTheme.transparency.surface,
        label = "widgetCardBackgroundOpacity",
    )

    // Fork: school blocks draw their own card; Kvaesitso's widgets get the same card look
    // (tinted surface, 24dp corners, no shadow) so the dashboard reads as one design.
    val schoolBlock = widget is LessonWidget || widget is BriefWidget || widget is TasksWidget ||
        widget is ContextWidget || widget is AssistantWidget || widget is FoldersWidget || widget is ChipWidget ||
        widget is WeekWidget || widget is MyDayWidget || widget is TestPrepWidget
    val forkCard = !schoolBlock && !(widget is AppWidget && !widget.config.background)
    LauncherCard(
        modifier = modifier.zIndex(if (isDragged) 1f else 0f),
        elevation = if (forkCard) 0.dp else elevation,
        backgroundOpacity = if (forkCard && !editMode) 0.86f else backgroundOpacity,
        shape = if (forkCard && !editMode) de.mm20.launcher2.ui.launcher.widgets.school.segmentShape() else MaterialTheme.shapes.medium,
        color = if (forkCard && !editMode) MaterialTheme.colorScheme.surfaceContainer.copy(alpha = 0.86f)
        else MaterialTheme.colorScheme.surface.copy(alpha = backgroundOpacity.coerceIn(0f, 1f)),
    ) {
        Column {
            AnimatedVisibility(editMode) {
                Row(
                    modifier = Modifier.padding(8.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Icon(
                        painterResource(R.drawable.drag_indicator_24px),
                        contentDescription = null,
                        modifier = Modifier.draggable(
                            state = draggableState,
                            orientation = Orientation.Vertical,
                            startDragImmediately = true,
                            onDragStarted = {
                                isDragged = true
                            },
                            onDragStopped = {
                                isDragged = false
                                onDragStopped()
                            }
                        )
                    )
                    Text(
                        text = widget.getLabel(LocalContext.current),
                        style = MaterialTheme.typography.titleMedium,
                        modifier = Modifier
                            .weight(1f)
                            .padding(horizontal = 8.dp),
                        overflow = TextOverflow.Ellipsis,
                        maxLines = 1
                    )
                    // Fork (round 16): on the wide dashboard, the column this block sits in; tap for the next.
                    val widthDp = androidx.compose.ui.platform.LocalConfiguration.current.screenWidthDp
                    if (widthDp >= WideDashboardMinWidthDp && widget !is de.mm20.launcher2.widgets.ChipWidget && widget !is de.mm20.launcher2.widgets.AssistantWidget) {
                        val ctx = LocalContext.current
                        val cols = de.mm20.launcher2.ui.launcher.widgets.school.DashboardColumns.count(widthDp.toFloat())
                        // Read so the label follows a change.
                        de.mm20.launcher2.ui.launcher.widgets.school.DashboardColumns.changes.intValue
                        val col = de.mm20.launcher2.ui.launcher.widgets.school.DashboardColumns.column(ctx, widget, 0, cols)
                        androidx.compose.material3.TextButton(onClick = {
                            de.mm20.launcher2.ui.launcher.widgets.school.DashboardColumns.set(ctx, widget, (col + 1) % cols)
                        }) { Text(stringResource(R.string.dashboard_column, col + 1)) }
                    }
                    IconButton(onClick = {
                        configure = true
                    }) {
                        Icon(
                            painterResource(R.drawable.tune_24px),
                            contentDescription = stringResource(R.string.settings)
                        )
                    }
                    IconButton(onClick = { onWidgetRemove() }) {
                        Icon(
                            painterResource(R.drawable.delete_24px),
                            contentDescription = stringResource(R.string.widget_action_remove)
                        )
                    }
                }
            }
            AnimatedVisibility(!editMode) {
                when (widget) {
                    is WeatherWidget -> {
                        WeatherWidget(widget)
                    }

                    is MusicWidget -> {
                        MusicWidget(widget)
                    }

                    is CalendarWidget -> {
                        CalendarWidget(widget)
                    }

                    is AppsWidget -> {
                        AppsWidget(widget)
                    }

                    is NotesWidget -> {
                        NotesWidget(
                            widget,
                            onWidgetAdd = onWidgetAdd,
                        )
                    }

                    is LessonWidget -> LessonWidget(widget)
                    is BriefWidget -> BriefWidget(widget)
                    is TasksWidget -> TasksWidget(widget)
                    is ContextWidget -> ContextWidget(widget)
                    is AssistantWidget -> AssistantWidget(widget)
                    is FoldersWidget -> de.mm20.launcher2.ui.launcher.widgets.school.FoldersWidget(widget)
                    is ChipWidget -> de.mm20.launcher2.ui.launcher.widgets.school.ChipWidget(widget)
                    is WeekWidget -> de.mm20.launcher2.ui.launcher.widgets.school.WeekWidget(widget)
                    is MyDayWidget -> de.mm20.launcher2.ui.launcher.widgets.school.MyDayWidget(widget)
                    is TestPrepWidget -> de.mm20.launcher2.ui.launcher.widgets.school.TestPrepWidget(widget)

                    is AppWidget -> {
                        AppWidget(
                            widget,
                            onWidgetUpdate = onWidgetUpdate,
                            onWidgetRemove = onWidgetRemove,
                        )
                    }
                }
            }
        }
    }
    ConfigureWidgetSheet(
        expanded = configure,
        widget = widget,
        onWidgetUpdated = onWidgetUpdate,
        onDismiss = { configure = false },
    )
}
