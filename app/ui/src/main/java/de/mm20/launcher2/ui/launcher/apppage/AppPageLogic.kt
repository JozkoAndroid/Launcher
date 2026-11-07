package de.mm20.launcher2.ui.launcher.apppage

import java.text.Collator
import java.text.Normalizer
import java.util.Locale

/** One app as the app page shows it. [ref] carries the real app; tests leave it null. */
data class AppEntry(
    val key: String,
    val label: String,
    /** Hidden from the list but still found by search. */
    val searchOnly: Boolean = false,
    val ref: Any? = null,
)

data class AppPageModel(
    val pinned: List<AppEntry>,
    val list: List<AppEntry>,
    /** Scrubber letters in order, each with the index of its first row in [list]. */
    val letters: List<Pair<String, Int>>,
    val pinnedCount: Int,
)

object AppPageLogic {

    const val MaxPinned = 8

    /**
     * No query: pinned apps (max 8, in pinned order), then every other visible app alphabetically.
     * With a query: matches only, search-only apps included, ignoring case and diacritics
     * ("cas" finds "Čas", "ubersetzer" finds "Übersetzer").
     */
    fun build(
        apps: List<AppEntry>,
        pinnedKeys: List<String>,
        query: String,
        locale: Locale = Locale.getDefault(),
    ): AppPageModel {
        val byKey = apps.associateBy { it.key }
        val pinnedAll = pinnedKeys.mapNotNull { byKey[it] }.take(MaxPinned)
        val pinnedSet = pinnedAll.mapTo(HashSet()) { it.key }
        val collator = Collator.getInstance(locale).apply { strength = Collator.PRIMARY }
        val q = normalize(query.trim())

        val pinned: List<AppEntry>
        val rest: List<AppEntry>
        if (q.isEmpty()) {
            pinned = pinnedAll
            rest = apps.filter { it.key !in pinnedSet && !it.searchOnly }
        } else {
            val match = { e: AppEntry -> normalize(e.label).contains(q) }
            pinned = pinnedAll.filter(match)
            rest = apps.filter { it.key !in pinnedSet && match(it) }
        }
        val sorted = rest.sortedWith { a, b -> collator.compare(a.label, b.label) }
        return AppPageModel(pinned, sorted, letterIndex(sorted, locale), pinnedAll.size)
    }

    /** First letter of each group; anything that is not a letter goes under "#". */
    fun letterOf(label: String, locale: Locale = Locale.getDefault()): String {
        val c = normalize(label).firstOrNull() ?: return "#"
        return if (c.isLetter()) c.toString().uppercase(locale) else "#"
    }

    private fun letterIndex(list: List<AppEntry>, locale: Locale): List<Pair<String, Int>> {
        val out = mutableListOf<Pair<String, Int>>()
        list.forEachIndexed { i, e ->
            val l = letterOf(e.label, locale)
            if (out.none { it.first == l }) out += l to i
        }
        return out
    }

    internal fun normalize(s: String): String =
        Normalizer.normalize(s, Normalizer.Form.NFD).replace(Regex("\\p{M}+"), "").lowercase(Locale.ROOT)
}
