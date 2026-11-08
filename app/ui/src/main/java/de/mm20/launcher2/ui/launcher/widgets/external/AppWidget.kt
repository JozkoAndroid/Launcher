package de.mm20.launcher2.ui.launcher.widgets.external

import android.appwidget.AppWidgetManager
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.material3.Button
import androidx.compose.material3.OutlinedButton
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
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import de.mm20.launcher2.ui.R
import de.mm20.launcher2.ui.component.Banner
import de.mm20.launcher2.ui.launcher.sheets.WidgetPickerSheet
import de.mm20.launcher2.ui.locals.LocalDarkTheme
import de.mm20.launcher2.ui.locals.LocalPreferDarkContentOverWallpaper
import de.mm20.launcher2.widgets.AppWidget
import de.mm20.launcher2.widgets.AppsWidget
import de.mm20.launcher2.widgets.CalendarWidget
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
import de.mm20.launcher2.widgets.WeatherWidget
import de.mm20.launcher2.widgets.Widget

@Composable
fun AppWidget(
    widget: AppWidget,
    onWidgetUpdate: (Widget) -> Unit,
    onWidgetRemove: () -> Unit,
) {
    val context = LocalContext.current

    val lightBackground =
        if (widget.config.background) !LocalDarkTheme.current else LocalPreferDarkContentOverWallpaper.current

    val widgetInfo = remember(widget.config.widgetId) {
        AppWidgetManager.getInstance(context)
            .getAppWidgetInfo(widget.config.widgetId)
    }
    if (widgetInfo == null) {
        var replaceWidget by rememberSaveable {
            mutableStateOf(false)
        }
        Banner(
            modifier = Modifier.padding(16.dp),
            text = stringResource(R.string.app_widget_loading_failed),
            icon = R.drawable.warning_24px,
            secondaryAction = {
                OutlinedButton(onClick = onWidgetRemove) {
                    Text(stringResource(R.string.widget_action_remove))
                }
            },
            primaryAction = {
                Button(onClick = { replaceWidget = true }) {
                    Text(stringResource(R.string.widget_action_replace))
                }
            }
        )
        WidgetPickerSheet(
            expanded = replaceWidget,
            onDismiss = { replaceWidget = false },
            onWidgetSelected = {
                val updatedWidget = when (it) {
                    is AppWidget -> widget.copy(
                        config = widget.config.copy(
                            widgetId = it.config.widgetId
                        )
                    )

                    is WeatherWidget -> it.copy(id = widget.id)
                    is MusicWidget -> it.copy(id = widget.id)
                    is CalendarWidget -> it.copy(id = widget.id)
                    is AppsWidget -> it.copy(id = widget.id)
                    is NotesWidget -> it.copy(id = widget.id)
                    is LessonWidget -> it.copy(id = widget.id)
                    is BriefWidget -> it.copy(id = widget.id)
                    is TasksWidget -> it.copy(id = widget.id)
                    is ContextWidget -> it.copy(id = widget.id)
                    is AssistantWidget -> it.copy(id = widget.id)
                    is FoldersWidget -> it.copy(id = widget.id)
                    is ChipWidget -> it.copy(id = widget.id)
                    is WeekWidget -> it.copy(id = widget.id)
                    is MyDayWidget -> it.copy(id = widget.id)
                    is TestPrepWidget -> it.copy(id = widget.id)
                    is de.mm20.launcher2.widgets.FocusWidget -> it.copy(id = widget.id)
                }
                onWidgetUpdate(updatedWidget)
                replaceWidget = false
            }
        )
    } else {
        // Fork (device test: third-party widgets showed only the app icon or nothing): widgets added at the
        // provider's minimum size (often one cell on Android 12+) get the full card width and a real default height.
        val density = androidx.compose.ui.platform.LocalDensity.current
        val oldDefaultW = with(density) { widgetInfo.minWidth.toDp() }.value.toInt()
        val oldDefaultH = with(density) { widgetInfo.minHeight.toDp() }.value.toInt()
        val width = widget.config.width?.takeIf { it != oldDefaultW }
        val height = if (widget.config.height <= oldDefaultH) defaultAppWidgetHeightDp(widgetInfo, density) else widget.config.height
        Box(modifier = Modifier.fillMaxWidth(), contentAlignment = Alignment.Center) {
            AppWidgetHost(
                widgetId = widget.config.widgetId,
                widgetInfo = widgetInfo,
                modifier = Modifier
                    .then(
                        if (width == null) Modifier.fillMaxWidth() else Modifier.width(width.dp)
                    )
                    .height(height.dp),
                borderless = widget.config.borderless,
                useThemeColors = widget.config.themeColors,
                onLightBackground = lightBackground,
            )
        }
    }
}

/** Default height for a new app widget: its target rows on Android 12+ (about 96dp each), never below its minimum. */
internal fun defaultAppWidgetHeightDp(info: android.appwidget.AppWidgetProviderInfo, density: androidx.compose.ui.unit.Density): Int {
    val minDp = with(density) { info.minHeight.toDp() }.value
    val cells = if (android.os.Build.VERSION.SDK_INT >= 31) info.targetCellHeight else 0
    var h = maxOf(minDp, cells * 96f, 96f)
    if (android.os.Build.VERSION.SDK_INT >= 31 && info.maxResizeHeight > 0) {
        h = minOf(h, with(density) { info.maxResizeHeight.toDp() }.value).coerceAtLeast(minDp)
    }
    return kotlin.math.round(h).toInt()
}
