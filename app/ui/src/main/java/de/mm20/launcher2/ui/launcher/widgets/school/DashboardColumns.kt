package de.mm20.launcher2.ui.launcher.widgets.school

import android.content.Context
import androidx.compose.runtime.mutableIntStateOf
import de.mm20.launcher2.widgets.Widget

/**
 * Which column each block sits in on the wide dashboard (unfolded Fold, tablet). Round 16, device test:
 * blocks went into whichever column was shorter, so when the lesson block grew (recording row) the blocks
 * after it jumped to the other side, and nothing could be placed on purpose. Now each block keeps its
 * column: chosen in edit mode, otherwise a default by kind. Within a column the list order holds.
 */
object DashboardColumns {
    private const val Prefs = "dashboard_columns"

    /** Bumped on every change, read by the dashboard so it lays out again. */
    val changes = mutableIntStateOf(0)

    fun column(context: Context, w: Widget, index: Int, cols: Int): Int {
        val stored = context.getSharedPreferences(Prefs, Context.MODE_PRIVATE).getInt(w.id.toString(), -1)
        return (if (stored >= 0) stored else default(w, index, cols)).coerceIn(0, cols - 1)
    }

    fun set(context: Context, w: Widget, column: Int) {
        context.getSharedPreferences(Prefs, Context.MODE_PRIVATE).edit().putInt(w.id.toString(), column).apply()
        changes.intValue++
    }

    /** Now and the to-do list on the left, the reading blocks on the right; anything else spread by id. */
    private fun default(w: Widget, index: Int, cols: Int): Int = when (w) {
        is de.mm20.launcher2.widgets.LessonWidget, is de.mm20.launcher2.widgets.TasksWidget,
        is de.mm20.launcher2.widgets.MyDayWidget, is de.mm20.launcher2.widgets.FoldersWidget,
        is de.mm20.launcher2.widgets.AppGridWidget, is de.mm20.launcher2.widgets.FocusWidget -> 0
        is de.mm20.launcher2.widgets.WeatherWidget, is de.mm20.launcher2.widgets.BriefWidget,
        is de.mm20.launcher2.widgets.CalendarWidget, is de.mm20.launcher2.widgets.NotesWidget,
        is de.mm20.launcher2.widgets.WeekWidget, is de.mm20.launcher2.widgets.TestPrepWidget,
        // Tiles under the reading blocks: the right column is the shorter one by default.
        is de.mm20.launcher2.widgets.ChipWidget -> 1
        // Stable per block, so the edit-mode label and the dashboard always agree.
        else -> (w.id.hashCode() and Int.MAX_VALUE) % cols
    }

    /**
     * Always two on wide screens (round 22, device test: three looked cluttered, and switching between
     * two and three when the tablet turned merged columns and lost the arrangement). Below 600dp the
     * dashboard is one column anyway.
     */
    @Suppress("UNUSED_PARAMETER")
    fun count(widthDp: Float) = 2
}
