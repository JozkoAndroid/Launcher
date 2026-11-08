package de.mm20.launcher2.services.widgets

import android.appwidget.AppWidgetManager
import android.appwidget.AppWidgetProviderInfo
import android.content.Context
import android.content.pm.LauncherApps
import android.os.Build
import androidx.core.content.getSystemService
import de.mm20.launcher2.widgets.CalendarWidget
import de.mm20.launcher2.widgets.MusicWidget
import de.mm20.launcher2.widgets.NotesWidget
import de.mm20.launcher2.widgets.LessonWidget
import de.mm20.launcher2.widgets.BriefWidget
import de.mm20.launcher2.widgets.TasksWidget
import de.mm20.launcher2.widgets.FoldersWidget
import de.mm20.launcher2.widgets.ChipWidget
import de.mm20.launcher2.widgets.WeekWidget
import de.mm20.launcher2.widgets.TestPrepWidget
import de.mm20.launcher2.widgets.WeatherWidget
import de.mm20.launcher2.widgets.Widget
import de.mm20.launcher2.widgets.WidgetRepository
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.withContext
import java.util.UUID

class WidgetsService(
    private val context: Context,
    private val widgetRepository: WidgetRepository,
) {
    suspend fun getAppWidgetProviders(): List<AppWidgetProviderInfo> = withContext(Dispatchers.IO) {
        val appWidgetManager = AppWidgetManager.getInstance(context)
        val launcherApps =
            context.getSystemService<LauncherApps>() ?: return@withContext emptyList()
        val profiles = launcherApps.profiles
        val widgets = mutableListOf<AppWidgetProviderInfo>()
        for (profile in profiles) {
            widgets.addAll(appWidgetManager.getInstalledProvidersForProfile(profile))
        }

        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.P) {
            // Ignore widgets that the launcher is not supposed to access
            widgets.filter {
                it.widgetFeatures and AppWidgetProviderInfo.WIDGET_FEATURE_HIDE_FROM_PICKER == 0
            }
        } else {
            widgets
        }
    }

    fun getAvailableBuiltInWidgets(): Flow<List<BuiltInWidgetInfo>> {
        return flowOf(getBuiltInWidgets())
    }

    fun getBuiltInWidgets(): List<BuiltInWidgetInfo> {
        // Fork (round 19, device test: "some widgets do nothing when added"): only blocks that always show
        // something, school ones first. Left out: "Môj deň" (empty most of the day), the Gemini block (the
        // dock's spark button), "Práve teraz" (repeats the tiles, often empty), Kvaesitso's app grid (the
        // folders block replaces it). Existing ones keep working.
        return listOf(
            BuiltInWidgetInfo(type = LessonWidget.Type, label = context.getString(R.string.widget_name_lesson)),
            BuiltInWidgetInfo(type = BriefWidget.Type, label = context.getString(R.string.widget_name_brief)),
            BuiltInWidgetInfo(type = TasksWidget.Type, label = context.getString(R.string.widget_name_school_tasks)),
            BuiltInWidgetInfo(type = WeekWidget.Type, label = context.getString(R.string.widget_name_week)),
            BuiltInWidgetInfo(type = TestPrepWidget.Type, label = context.getString(R.string.widget_name_testprep)),
            BuiltInWidgetInfo(type = de.mm20.launcher2.widgets.FocusWidget.Type, label = context.getString(R.string.widget_name_focus)),
            BuiltInWidgetInfo(type = de.mm20.launcher2.widgets.AppGridWidget.Type, label = context.getString(R.string.widget_name_appgrid)),
            BuiltInWidgetInfo(type = FoldersWidget.Type, label = context.getString(R.string.widget_name_folders)),
            BuiltInWidgetInfo(type = WeatherWidget.Type, label = context.getString(R.string.widget_name_weather)),
            BuiltInWidgetInfo(type = CalendarWidget.Type, label = context.getString(R.string.widget_name_calendar)),
            BuiltInWidgetInfo(type = NotesWidget.Type, label = context.getString(R.string.widget_name_notes)),
            BuiltInWidgetInfo(type = MusicWidget.Type, label = context.getString(R.string.widget_name_music)),
        ) + ChipWidget.Kinds.map {
            BuiltInWidgetInfo(type = ChipWidget.TypePrefix + it, label = context.getString(ChipWidget.labelRes(it)))
        }
    }

    fun addWidget(widget: Widget, position: Int, parentId: UUID? = null) {
        widgetRepository.create(widget, position, parentId)
    }

    fun updateWidget(widget: Widget) {
        widgetRepository.update(widget)
    }

    fun getWidgets() = widgetRepository.get()

    fun countWidgets(type: String) = widgetRepository.count(type)

    fun removeWidget(widget: Widget) {
        widgetRepository.delete(widget)
    }

    companion object {
        const val AppWidgetHostId = 44203
    }
}