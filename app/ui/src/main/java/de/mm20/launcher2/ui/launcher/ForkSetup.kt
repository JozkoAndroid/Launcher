package de.mm20.launcher2.ui.launcher

import android.content.Context
import androidx.appcompat.app.AppCompatDelegate
import androidx.core.os.LocaleListCompat
import de.mm20.launcher2.preferences.GestureAction
import de.mm20.launcher2.preferences.WidgetScreenTarget
import de.mm20.launcher2.preferences.ui.GestureSettings
import de.mm20.launcher2.preferences.ui.UiSettings
import de.mm20.launcher2.widgets.AssistantWidget
import de.mm20.launcher2.widgets.BriefWidget
import de.mm20.launcher2.widgets.CalendarWidget
import de.mm20.launcher2.widgets.ContextWidget
import de.mm20.launcher2.widgets.LessonWidget
import de.mm20.launcher2.widgets.MusicWidget
import de.mm20.launcher2.widgets.NotesWidget
import de.mm20.launcher2.widgets.TasksWidget
import de.mm20.launcher2.widgets.WeatherWidget
import de.mm20.launcher2.widgets.WidgetRepository
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.launch
import org.koin.core.component.KoinComponent
import org.koin.core.component.inject
import java.util.UUID

/**
 * Fork: one-time setup so the launcher is the spec's home screen on first start and after updates
 * from earlier test builds. Runs each step once (versioned), never again after the user changes things.
 */
object ForkSetup : KoinComponent {
    private const val Prefs = "fork_setup"
    private const val Version = 14

    private val uiSettings: UiSettings by inject()
    private val gestureSettings: GestureSettings by inject()
    private val widgets: WidgetRepository by inject()
    private val searchUiSettings: de.mm20.launcher2.preferences.ui.SearchUiSettings by inject()

    fun run(context: Context) {
        val prefs = context.getSharedPreferences(Prefs, Context.MODE_PRIVATE)
        val from = prefs.getInt("version", 0)
        if (from >= Version) return

        // Slovak UI regardless of the phone's language (spec). Changeable later in Android's per-app language.
        if (AppCompatDelegate.getApplicationLocales().isEmpty) {
            AppCompatDelegate.setApplicationLocales(LocaleListCompat.forLanguageTags("sk"))
        }

        CoroutineScope(SupervisorJob() + Dispatchers.IO).launch {
            if (from < 1) setupV1()
            // v2 (device test): one app list only. Kvaesitso's list with favorites on swipe up and down;
            // the extra app page and the unfolded split view are gone.
            if (gestureSettings.swipeUp.first().let { it is GestureAction.AppPage || it is GestureAction.Widgets }) {
                gestureSettings.setSwipeUp(GestureAction.Search)
            }
            if (gestureSettings.swipeRight.first() is GestureAction.AppPage) gestureSettings.setSwipeRight(GestureAction.NoAction)
            // v3 (device test): no keyboard when the app list opens by a swipe; only on tapping the field.
            if (from < 3) searchUiSettings.setOpenKeyboard(false)
            // v5: swipe right = school chat, swipe left = week (only where nothing else is set).
            if (from < 5) {
                if (gestureSettings.swipeRight.first() is GestureAction.NoAction) gestureSettings.setSwipeRight(GestureAction.SchoolChat)
                if (gestureSettings.swipeLeft.first() is GestureAction.NoAction) gestureSettings.setSwipeLeft(GestureAction.SchoolWeek)
            }
            // v6 (device test): the chat page is gone. Swipe left opens the side panel (widget screen 2)
            // with app widgets and the chat docked at the bottom; swipe right does nothing.
            if (from < 6) {
                if (gestureSettings.swipeRight.first() is GestureAction.SchoolChat) gestureSettings.setSwipeRight(GestureAction.NoAction)
                if (gestureSettings.swipeLeft.first().let { it is GestureAction.SchoolWeek || it is GestureAction.SchoolChat || it is GestureAction.NoAction }) {
                    gestureSettings.setSwipeLeft(GestureAction.Widgets(WidgetScreenTarget.Widgets2))
                }
                val panel = WidgetScreenTarget.Widgets2.id
                if (widgets.get(panel).first().isEmpty()) {
                    widgets.create(de.mm20.launcher2.widgets.WeekWidget(UUID.randomUUID()), 0, panel)
                }
            }
            // v7 (device test): swipe down = notifications as usual; the app list stays on swipe up.
            if (from < 7 && gestureSettings.swipeDown.first() is GestureAction.Search) {
                gestureSettings.setSwipeDown(GestureAction.Notifications)
            }
            // v8 (round 7): the recommended layout, asked for by the user. Home: the lesson on the wallpaper,
            // then one sheet (brief, tasks), then the context chips; the dock floats below. Side panel:
            // weather forecast, test prep, week, folders. App widgets the user added stay where they are.
            if (from < 8) {
                applyRecommendedLayout()
                if (gestureSettings.swipeRight.first().let { it is GestureAction.NoAction || it is GestureAction.SchoolWeek }) {
                    gestureSettings.setSwipeRight(GestureAction.SchoolChat) // "Môj deň"
                }
            }
            // v9 (device test: "add more of your widgets on the home screen"): richer home, panel unchanged.
            if (from == 8) layoutHome()
            // v10: folders live on the apps page (the panel's second page), not as a block.
            if (from < 10) {
                widgets.get(WidgetScreenTarget.Widgets2.id).first().filterIsInstance<de.mm20.launcher2.widgets.FoldersWidget>().forEach { widgets.delete(it) }
            }
            // v11: the "+" tile (add to Schoolhelp) joins the home tiles, first in their row.
            if (from in 8..10) {
                val home = widgets.get(WidgetScreenTarget.Default.id).first()
                if (home.none { it is de.mm20.launcher2.widgets.ChipWidget && it.kind == "add" }) {
                    val at = home.indexOfFirst { it is de.mm20.launcher2.widgets.ChipWidget }.let { if (it < 0) home.size else it }
                    widgets.create(
                        de.mm20.launcher2.widgets.ChipWidget(UUID.randomUUID(), "add", de.mm20.launcher2.widgets.ChipWidgetConfig(style = "tile", icon = "plain")),
                        at, WidgetScreenTarget.Default.id,
                    )
                }
            }
            // v13 (minimal design): the search pill at the bottom; tiles monochrome with plain icons.
            if (from < 13) {
                uiSettings.setBottomSearchBar(true)
                for (parent in listOf(WidgetScreenTarget.Default.id, WidgetScreenTarget.Widgets2.id)) {
                    widgets.get(parent).first().filterIsInstance<de.mm20.launcher2.widgets.ChipWidget>().forEach { w ->
                        widgets.update(w.copy(config = w.config.copy(icon = if (w.config.icon == "circle") "plain" else w.config.icon, accent = false)))
                    }
                }
            }
            // v14 (round 15, user: "your suggested layout, no duplicates"): the recommended home and panel.
            // App widgets stay. Fresh installs get it just below.
            if (from in 1..13) applyRecommendedLayout()
            // v4 (folders on home) is superseded by v8.
            // Fresh install (round 15, reinstall after a signing key change): the steps above only upgrade
            // older layouts (layoutHome ran for v8 phones only), so a new install got round 1's blocks.
            // Start from today's recommended home and panel instead.
            if (from == 0) applyRecommendedLayout()
            prefs.edit().putInt("version", Version).apply()
        }
    }

    /**
     * Recommended home (round 8): the lesson on the wallpaper; one sheet with the brief, tasks and today's
     * weather; then a grid of tiles (name day in serif, mobile data and steps as rings, Instagram, the
     * next test as a countdown); then the context chips. The dock floats below. App widgets stay.
     */
    private suspend fun layoutHome() {
        val parent = WidgetScreenTarget.Default.id
        widgets.get(parent).first().filter { it !is de.mm20.launcher2.widgets.AppWidget }.forEach { widgets.delete(it) }
        fun chip(kind: String, cfg: de.mm20.launcher2.widgets.ChipWidgetConfig) = de.mm20.launcher2.widgets.ChipWidget(UUID.randomUUID(), kind, cfg)
        val tile = de.mm20.launcher2.widgets.ChipWidgetConfig(style = "tile", icon = "plain")
        // Round 15 (user: "your suggested layout, no duplicates"): each thing once. The lesson block's
        // header already carries the weather, so no weather card here (the full one is on the panel);
        // no "Práve teraz", its values are the tiles. Tiles last, right above the dock, under the thumb.
        listOf(
            LessonWidget(UUID.randomUUID()),
            BriefWidget(UUID.randomUUID()),
            TasksWidget(UUID.randomUUID()),
            chip("add", tile),
            chip("test", tile),
            chip("nameday", tile.copy(font = "serif")),
            chip("data", de.mm20.launcher2.widgets.ChipWidgetConfig(style = "ring")),
            chip("steps", de.mm20.launcher2.widgets.ChipWidgetConfig(style = "ring")),
            chip("instagram", tile.copy(font = "condensed")),
        ).forEachIndexed { i, w -> widgets.create(w, i, parent) }
    }

    /** From the school settings: put the recommended blocks back (app widgets stay). */
    fun resetLayout(context: Context) {
        CoroutineScope(SupervisorJob() + Dispatchers.IO).launch { applyRecommendedLayout() }
    }

    private suspend fun applyRecommendedLayout() {
        suspend fun layout(parent: UUID, blocks: List<de.mm20.launcher2.widgets.Widget>) {
            val current = widgets.get(parent).first()
            current.filter { it !is de.mm20.launcher2.widgets.AppWidget }.forEach { widgets.delete(it) }
            blocks.forEachIndexed { i, w -> widgets.create(w, i, parent) }
        }
        layoutHome()
        layout(
            WidgetScreenTarget.Widgets2.id, listOf(
                WeatherWidget(UUID.randomUUID()),
                de.mm20.launcher2.widgets.WeekWidget(UUID.randomUUID()),
                de.mm20.launcher2.widgets.TestPrepWidget(UUID.randomUUID()),
            )
        )
    }

    private suspend fun setupV1() {
        run {
            // The blocks live on the home screen (Kvaesitso's default put them on a swipe-up page).
            uiSettings.setHomeScreenWidgets(true)

            // Replace Kvaesitso's default widgets, which duplicate the school blocks, with the spec's five blocks.
            val parent = WidgetScreenTarget.Default.id
            val current = widgets.get(parent).first()
            current.filter { it is WeatherWidget || it is MusicWidget || it is CalendarWidget || it is NotesWidget }
                .forEach { widgets.delete(it) }
            val have = current.map { it::class }.toSet()
            listOf(
                LessonWidget(UUID.randomUUID()),
                BriefWidget(UUID.randomUUID()),
                TasksWidget(UUID.randomUUID()),
                ContextWidget(UUID.randomUUID()),
                AssistantWidget(UUID.randomUUID()),
            ).forEachIndexed { i, w -> if (w::class !in have) widgets.create(w, i, parent) }
        }
    }
}
