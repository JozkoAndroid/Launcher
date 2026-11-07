package de.mm20.launcher2.ui.launcher.apppage

import android.content.Context
import android.content.pm.LauncherApps
import androidx.core.app.ActivityOptionsCompat
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import de.mm20.launcher2.applications.AppRepository
import de.mm20.launcher2.icons.IconService
import de.mm20.launcher2.icons.LauncherIcon
import de.mm20.launcher2.search.Application
import de.mm20.launcher2.searchable.PinnedLevel
import de.mm20.launcher2.searchable.SavableSearchableRepository
import de.mm20.launcher2.searchable.VisibilityLevel
import de.mm20.launcher2.services.favorites.FavoritesService
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.stateIn
import org.koin.core.component.KoinComponent
import org.koin.core.component.inject

class AppPageVM : ViewModel(), KoinComponent {
    private val appRepository: AppRepository by inject()
    private val favoritesService: FavoritesService by inject()
    private val searchableRepository: SavableSearchableRepository by inject()
    private val iconService: IconService by inject()

    val query = MutableStateFlow("")

    private val apps: Flow<List<Application>> = appRepository.findMany()

    private val pinnedKeys: Flow<List<String>> = searchableRepository.getKeys(
        includeTypes = listOf("app"),
        minPinnedLevel = PinnedLevel.AutomaticallySorted,
        minVisibility = VisibilityLevel.SearchOnly,
        limit = AppPageLogic.MaxPinned,
    )

    private val searchOnlyKeys: Flow<List<String>> = searchableRepository.getKeys(
        includeTypes = listOf("app"),
        minVisibility = VisibilityLevel.SearchOnly,
        maxVisibility = VisibilityLevel.SearchOnly,
    )

    /** Fully hidden apps never appear, not even in search (Kvaesitso's own rule). */
    private val hiddenKeys: Flow<List<String>> = searchableRepository.getKeys(
        includeTypes = listOf("app"),
        minVisibility = VisibilityLevel.Hidden,
        maxVisibility = VisibilityLevel.Hidden,
    )

    val model: StateFlow<AppPageModel?> = combine(apps, pinnedKeys, searchOnlyKeys, hiddenKeys, query) { apps, pinned, searchOnly, hidden, q ->
        val so = searchOnly.toHashSet()
        val h = hidden.toHashSet()
        val entries = apps.filter { it.key !in h }.map { AppEntry(it.key, it.labelOverride ?: it.label, it.key in so, it) }
        AppPageLogic.build(entries, pinned, q)
    }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), null)

    fun icon(entry: AppEntry, sizePx: Int): Flow<LauncherIcon?> =
        iconService.getIcon(entry.ref as Application, sizePx)

    fun launch(context: Context, entry: AppEntry) {
        val app = entry.ref as? Application ?: return
        if (app.launch(context, ActivityOptionsCompat.makeBasic().toBundle())) {
            favoritesService.reportLaunch(app)
        }
    }

    fun setPinned(entry: AppEntry, pinned: Boolean) {
        val app = entry.ref as? Application ?: return
        if (pinned) favoritesService.pinItem(app) else favoritesService.unpinItem(app)
    }

    /** "Hide from list" = Kvaesitso's search-only level: gone from the list, still found by search. */
    fun setHidden(entry: AppEntry, hidden: Boolean) {
        val app = entry.ref as? Application ?: return
        favoritesService.setVisibility(app, if (hidden) VisibilityLevel.SearchOnly else VisibilityLevel.Default)
    }

    /** Pinned keys regardless of the current search, for the long-press menu. */
    val pinnedKeySet: StateFlow<Set<String>> = combine(pinnedKeys, query) { p, _ -> p.toSet() }
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), emptySet())

    fun openAppInfo(context: Context, entry: AppEntry) {
        val app = entry.ref as? Application ?: return
        runCatching {
            context.getSystemService(LauncherApps::class.java)
                .startAppDetailsActivity(app.componentName, app.user, null, null)
        }
    }
}
