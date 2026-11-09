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

    /** [renews] = the first day of the next cycle. */
    data class MobileData(val usedBytes: Long, val planBytes: Long, val renews: LocalDate? = null) : ContextCard {
        override val rank = 3
    }

    /** Today's school lunch, from the lunch app's notification (Lunch Bell; never EduPage). */
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

/** The chip block kind a card belongs to (see ChipWidget.Kinds). */
fun ContextCard.kind(): String = when (this) {
    is ContextCard.TestSoon -> "test"
    is ContextCard.Chats -> "chats"
    is ContextCard.Lunch -> "lunch"
    is ContextCard.NameDay -> "nameday"
    is ContextCard.Birthday -> "birthday"
    is ContextCard.Music -> "music"
    is ContextCard.Steps -> "steps"
    is ContextCard.MobileData -> "data"
    is ContextCard.Instagram -> "instagram"
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

    /** Whether a card is urgent enough for "Práve teraz" (dedicated blocks show their card always). */
    fun urgent(c: ContextCard, now: LocalTime, s: ContextSettings): Boolean = when (c) {
        is ContextCard.MobileData -> showData(c.usedBytes, s)
        is ContextCard.Steps -> showSteps(now, c.steps, s)
        is ContextCard.Instagram -> showInstagram(c.minutes)
        else -> true
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

    /** One resume or pause of one of the app's screens (activities). */
    data class AppEvent(val time: Long, val screen: String, val resumed: Boolean)

    /**
     * Minutes the app was on screen today. The app counts as open while any of its screens is resumed,
     * so switching between its screens is one session. A pause before any resume today means the app
     * was already open at midnight (counted once, from midnight). Capped at the time since midnight.
     */
    fun foregroundMinutes(events: List<AppEvent>, nowMs: Long, dayStartMs: Long): Long {
        var total = 0L
        val open = mutableSetOf<String>()
        var since = 0L
        var seenAny = false
        for (e in events.sortedBy { it.time }) {
            if (e.resumed) {
                if (open.isEmpty()) since = maxOf(e.time, dayStartMs)
                open += e.screen
            } else if (e.screen in open) {
                open -= e.screen
                if (open.isEmpty()) total += e.time - since
            } else if (!seenAny) {
                total += e.time - dayStartMs
            }
            seenAny = true
        }
        if (open.isNotEmpty()) total += nowMs - since
        return (total.coerceIn(0, nowMs - dayStartMs)) / 60_000
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

    /** App names that look like a lunch app such as Lunch Bell (used when no app is chosen in settings). */
    private val lunchKeywords = listOf("lunch bell", "jedál", "jedal", "obed", "lunch", "food", "menu", "mensa", "essen")

    /**
     * EduPage never sends the menu: its notifications (grades, messages) read as a wrong lunch (device test),
     * so it is never a lunch source, not even when chosen before.
     */
    fun isEduPage(pkg: String, label: String = ""): Boolean =
        pkg.contains("edupage", ignoreCase = true) || label.contains("edupage", ignoreCase = true)

    fun looksLikeLunchApp(label: String): Boolean =
        !isEduPage("", label) && lunchKeywords.any { label.contains(it, ignoreCase = true) }

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
