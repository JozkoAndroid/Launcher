package de.mm20.launcher2.schoolbrief

import java.time.DayOfWeek
import java.time.LocalDate
import java.time.LocalTime
import java.time.temporal.ChronoUnit

/** Checks and fallbacks for the brief text. Port of the earlier check_brief(), plus a no-AI template. */
object BriefRules {

    // Round 22 (user: "news more about Slovakia"): Slovakia first, the EU with a Slovak angle, then tech and aviation.
    val Topics = listOf("sk", "eu", "tech", "aviation")

    /** The three daily slots; the evening one writes about tomorrow. */
    val Slots: List<LocalTime> = listOf(LocalTime.of(6, 30), LocalTime.of(13, 30), LocalTime.of(19, 30))

    fun coversTomorrow(now: LocalTime): Boolean = !now.isBefore(Slots[2])

    /** Returns null when valid, else the reason (fed back to the model on retry). */
    fun check(
        brief: String,
        news: List<NewsItem>,
        tasks: List<SchoolTask>,
        candidateUrls: Set<String>,
        overdueTitles: List<String> = emptyList(),
    ): String? {
        val text = brief.trim()
        if (text.isEmpty()) return "brief is empty"
        val sentences = text.split(Regex("(?<=[.!?])\\s+")).filter { it.isNotBlank() }
        if (sentences.size > 3) return "brief has ${sentences.size} sentences, max 3"
        val titles = tasks.map { it.title }
        for (m in Regex("[„\"“]([^“”\"]+)[“”\"]").findAll(text)) {
            val q = m.groupValues[1]
            if (titles.none { q in it }) return "brief quotes '$q', which is not an exact task title"
        }
        if (overdueTitles.isNotEmpty() && overdueTitles.none { "„$it“" in text || "\"$it\"" in text || "“$it”" in text }) {
            return "brief must quote the overdue task „${overdueTitles.first()}“"
        }
        if (news.map { it.topic } != Topics) return "news topics must be exactly $Topics"
        for (n in news) {
            if (n.title.isBlank() || !n.url.startsWith("https://")) return "news item incomplete: ${n.title}"
            if (n.url !in candidateUrls) return "news url not from the fetched feeds: ${n.url}"
        }
        return null
    }

    /**
     * No-AI brief in Slovak: free day or the most urgent items, titles quoted exactly, max 3 sentences.
     * Used without a Gemini key, offline, or when the AI answer fails the checks.
     */
    fun template(tasks: List<SchoolTask>, daysOff: List<DayOff>, today: LocalDate, now: LocalTime): String {
        val target = if (coversTomorrow(now)) today.plusDays(1) else today
        val dayWord = if (target == today) "Dnes" else "Zajtra"
        val off = daysOff.firstOrNull { !target.isBefore(LocalDate.parse(it.start)) && !target.isAfter(LocalDate.parse(it.end)) }
        val weekend = target.dayOfWeek == DayOfWeek.SATURDAY || target.dayOfWeek == DayOfWeek.SUNDAY
        val rows = BriefLogic.visibleTasks(tasks, today)
        val sentences = mutableListOf<String>()
        sentences += when {
            off != null -> "$dayWord máš voľno (${off.title})."
            weekend -> "$dayWord máš voľno."
            else -> "$dayWord máš školu."
        }
        val overdue = rows.filter { it.overdue }
        if (overdue.isNotEmpty()) {
            sentences += "Po termíne: " + overdue.take(2).joinToString(" a ") { "„${it.task.title}“" } + "."
        }
        val next = rows.filter { !it.overdue }.take(if (overdue.isEmpty()) 2 else 1)
        if (next.isNotEmpty()) {
            sentences += "Najbližšie: " + next.joinToString(" a ") { "„${it.task.title}“ (${dayLabel(it.due, today)})" } + "."
        }
        if (overdue.isEmpty() && next.isEmpty()) sentences += "Na najbližších 14 dní nemáš nič zapísané."
        return sentences.take(3).joinToString(" ")
    }

    private val weekdays = listOf("pondelok", "utorok", "streda", "štvrtok", "piatok", "sobota", "nedeľa")

    private fun dayLabel(d: LocalDate, today: LocalDate): String = when (ChronoUnit.DAYS.between(today, d)) {
        0L -> "dnes"
        1L -> "zajtra"
        in 2..6 -> weekdays[d.dayOfWeek.value - 1]
        else -> "${d.dayOfMonth}. ${d.monthValue}."
    }
}
