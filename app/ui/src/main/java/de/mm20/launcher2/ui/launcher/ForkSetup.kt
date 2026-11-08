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
    private const val Version = 10

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
            // v4 (folders on home) is superseded by v8.
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
        val tile = de.mm20.launcher2.widgets.ChipWidgetConfig(style = "tile", icon = "circle")
        listOf(
            LessonWidget(UUID.randomUUID()),
            BriefWidget(UUID.randomUUID()),
            TasksWidget(UUID.randomUUID()),
            WeatherWidget(UUID.randomUUID(), de.mm20.launcher2.widgets.WeatherWidgetConfig(showForecast = false)),
            chip("test", tile.copy(accent = true)),
            chip("nameday", tile.copy(font = "serif")),
            chip("data", de.mm20.launcher2.widgets.ChipWidgetConfig(style = "ring")),
            chip("steps", de.mm20.launcher2.widgets.ChipWidgetConfig(style = "ring")),
            chip("instagram", tile.copy(font = "condensed")),
            ContextWidget(UUID.randomUUID()),
        ).forEachIndexed { i, w -> widgets.create(w, i, parent) }
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
                de.mm20.launcher2.widgets.TestPrepWidget(UUID.randomUUID()),
                de.mm20.launcher2.widgets.WeekWidget(UUID.randomUUID()),
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
