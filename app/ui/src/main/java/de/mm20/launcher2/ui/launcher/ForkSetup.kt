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
    private const val Version = 7

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
            // v4: folders block on the home screen, after the context chips.
            if (from < 4) {
                val parent = WidgetScreenTarget.Default.id
                val current = widgets.get(parent).first()
                if (current.none { it is de.mm20.launcher2.widgets.FoldersWidget }) {
                    val at = current.indexOfFirst { it is ContextWidget }.let { if (it < 0) current.size else it + 1 }
                    widgets.create(de.mm20.launcher2.widgets.FoldersWidget(UUID.randomUUID()), at, parent)
                }
            }
            prefs.edit().putInt("version", Version).apply()
        }
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
