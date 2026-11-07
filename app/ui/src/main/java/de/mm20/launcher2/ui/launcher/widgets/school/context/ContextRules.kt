package de.mm20.launcher2.ui.launcher.widgets.school.context

import java.time.LocalDate
import java.time.LocalTime
import java.time.temporal.ChronoUnit

/** One context card. Lower [rank] = more urgent; music always first (spec). */
sealed interface ContextCard {
    val rank: Int

    data class Music(val title: String, val artist: String?, val playing: Boolean) : ContextCard {
        override val rank = 0
    }

    /** A test within the next 3 days. */
    data class TestSoon(val title: String, val subject: String, val days: Long) : ContextCard {
        override val rank = 1
    }

    /** Unread messages in Schoolhelp chats (muted chats not counted). */
    data class Chats(val unread: Int) : ContextCard {
        override val rank = 2
    }

    data class MobileData(val usedBytes: Long, val planBytes: Long) : ContextCard {
        override val rank = 3
    }

    /** Today's school lunch, from the EduPage food app's notification. */
    data class Lunch(val text: String, val packageName: String, val tomorrow: Boolean = false) : ContextCard {
        override val rank = 4
    }

    data class Birthday(val name: String, val contactId: Long) : ContextCard {
        override val rank = 5
    }

    /** Today's Slovak name day; [contactName] = a contact who celebrates it, if any. */
    data class NameDay(val names: List<String>, val contactName: String?, val contactId: Long?) : ContextCard {
        override val rank = 6
    }

    data class Steps(val steps: Long, val goal: Long) : ContextCard {
        override val rank = 7
    }

    data class Instagram(val minutes: Long) : ContextCard {
        override val rank = 8
    }
}

/** User settings for the cards; defaults are the spec's values. */
data class ContextSettings(
    val planBytes: Long = 3_500_000_000L,
    /** Any start day of a cycle; cycles repeat every [cycleDays] from it, before and after. */
    val cycleAnchor: LocalDate = LocalDate.parse("2026-11-18"),
    val cycleDays: Long = 30,
    val stepsGoal: Long = 8000,
    val stepsAfter: LocalTime = LocalTime.of(15, 0),
)

object ContextRules {

    /** Raised from 3 once tests and chats joined: the most urgent four. */
    const val MaxCards = 4
    const val DataThreshold = 0.8

    /** Start of the rolling cycle that contains [today]. */
    fun cycleStart(today: LocalDate, s: ContextSettings): LocalDate {
        val days = ChronoUnit.DAYS.between(s.cycleAnchor, today)
        return s.cycleAnchor.plusDays(Math.floorDiv(days, s.cycleDays) * s.cycleDays)
    }

    fun showData(usedBytes: Long, s: ContextSettings): Boolean = s.planBytes > 0 && usedBytes >= s.planBytes * DataThreshold

    fun showSteps(now: LocalTime, steps: Long, s: ContextSettings): Boolean = !now.isBefore(s.stepsAfter) && steps < s.stepsGoal

    fun showInstagram(minutes: Long): Boolean = minutes >= 1

    /** "--11-07" (no year) and "2009-11-07" both match 7 November; Feb 29 birthdays show on Feb 28 in other years. */
    fun isBirthdayToday(contactDate: String, today: LocalDate): Boolean {
        val m = Regex("""^(?:\d{4}|-)-(\d{2})-(\d{2})""").find(contactDate.trim()) ?: return false
        val month = m.groupValues[1].toInt()
        val day = m.groupValues[2].toInt()
        if (month == today.monthValue && day == today.dayOfMonth) return true
        return month == 2 && day == 29 && !today.isLeapYear && today.monthValue == 2 && today.dayOfMonth == 28
    }

    /** Foreground minutes from resume/pause event pairs (ms timestamps); an open session counts until [nowMs]. */
    fun foregroundMinutes(events: List<Pair<Long, Boolean>>, nowMs: Long, dayStartMs: Long): Long {
        var total = 0L
        var since: Long? = null
        for ((t, resumed) in events.sortedBy { it.first }) {
            if (resumed) {
                if (since == null) since = maxOf(t, dayStartMs)
            } else if (since != null) {
                total += t - since; since = null
            } else {
                // Paused without a resume today: the session started before midnight.
                total += t - dayStartMs
            }
        }
        if (since != null) total += nowMs - since
        return total / 60_000
    }

    fun pick(cards: List<ContextCard>): List<ContextCard> = cards.sortedBy { it.rank }.take(MaxCards)

    /** The nearest open test in the next [days] days, if any. */
    fun testSoon(tasks: List<de.mm20.launcher2.schoolbrief.SchoolTask>, today: LocalDate, days: Long = 3): ContextCard.TestSoon? =
        tasks.asSequence()
            .filter { it.kind == de.mm20.launcher2.schoolbrief.SchoolTask.Test && !it.done }
            .mapNotNull { t -> runCatching { LocalDate.parse(t.due) }.getOrNull()?.let { t to ChronoUnit.DAYS.between(today, it) } }
            .filter { it.second in 0..days }
            .minByOrNull { it.second }
            ?.let { (t, d) -> ContextCard.TestSoon(t.title, t.subject, d) }

    /** A lunch stays relevant until this time on its day; before that it shows as "zajtra" the evening before. */
    val LunchUntil: java.time.LocalTime = java.time.LocalTime.of(15, 0)

    /** Whether a lunch for [lunchDay] is still worth showing at [now] (device test: hidden every evening before). */
    fun lunchVisible(lunchDay: LocalDate, now: java.time.LocalDateTime): Boolean =
        now.isBefore(lunchDay.atTime(LunchUntil)) && !lunchDay.isAfter(now.toLocalDate().plusDays(1))

    /** App names that look like the EduPage food app (used when no app is chosen in settings). */
    private val lunchKeywords = listOf("lunch bell", "edupage", "jedál", "jedal", "obed", "lunch", "food", "menu", "mensa", "essen")

    fun looksLikeLunchApp(label: String): Boolean = lunchKeywords.any { label.contains(it, ignoreCase = true) }

    /** The notification text worth showing: big text if present, else text, else title; one line. */
    fun lunchText(title: String?, text: String?, bigText: String?): String? =
        listOf(bigText, text, title).firstOrNull { !it.isNullOrBlank() }?.replace(Regex("\\s+"), " ")?.trim()

    /**
     * Lunch Bell shows a short menu code ("2", "Z2"): returns it, or null when the text is a real
     * description (then it is shown as is). Device test: the notification read "Z2".
     */
    fun menuNumber(text: String): String? = text.trim().takeIf { Regex("""^[A-Za-z]{0,2}\d{1,2}[A-Za-z]?$""").matches(it) }

    /**
     * Which day a lunch notification is for: one posted from 12:00 on is about the next day
     * (Lunch Bell announces tomorrow's menu), earlier ones about the same day.
     */
    fun lunchDay(postedAt: java.time.LocalDateTime): LocalDate =
        if (postedAt.hour >= 12) postedAt.toLocalDate().plusDays(1) else postedAt.toLocalDate()

    /** Name-day lookup: "MM-dd" -> names. Contacts whose first name matches are offered first. */
    fun nameDay(calendar: Map<String, List<String>>, today: LocalDate): List<String> =
        calendar["%02d-%02d".format(today.monthValue, today.dayOfMonth)].orEmpty()

    fun firstNameMatches(displayName: String, names: List<String>): Boolean {
        val first = displayName.trim().substringBefore(' ')
        return names.any { it.equals(first, ignoreCase = true) }
    }
}
