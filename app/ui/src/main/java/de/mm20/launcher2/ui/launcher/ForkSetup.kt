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
    private const val Version = 1

    private val uiSettings: UiSettings by inject()
    private val gestureSettings: GestureSettings by inject()
    private val widgets: WidgetRepository by inject()

    fun run(context: Context) {
        val prefs = context.getSharedPreferences(Prefs, Context.MODE_PRIVATE)
        if (prefs.getInt("version", 0) >= Version) return

        // Slovak UI regardless of the phone's language (spec). Changeable later in Android's per-app language.
        if (AppCompatDelegate.getApplicationLocales().isEmpty) {
            AppCompatDelegate.setApplicationLocales(LocaleListCompat.forLanguageTags("sk"))
        }

        CoroutineScope(SupervisorJob() + Dispatchers.IO).launch {
            // The blocks live on the home screen (Kvaesitso's default put them on a swipe-up page),
            // which is also what lets the unfolded Fold show two panes.
            uiSettings.setHomeScreenWidgets(true)
            // Swipe up opens the app page, like the Samsung app drawer; swipe right still works too.
            if (gestureSettings.swipeUp.first() is GestureAction.Widgets) gestureSettings.setSwipeUp(GestureAction.AppPage)

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

            prefs.edit().putInt("version", Version).apply()
        }
    }
}
