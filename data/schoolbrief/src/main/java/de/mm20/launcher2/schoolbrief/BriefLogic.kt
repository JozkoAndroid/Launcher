package de.mm20.launcher2.schoolbrief

import java.time.DayOfWeek
import java.time.Duration
import java.time.LocalDate
import java.time.LocalDateTime
import java.time.LocalTime
import java.time.OffsetDateTime

/** A task as the Tasks block shows it. */
data class TaskRow(
    val task: SchoolTask,
    val due: LocalDate,
    val overdue: Boolean,
)

/** What the Lesson block shows right now. */
sealed interface LessonState {
    /** In a lesson; [progress] is 0..1 through it. */
    data class InLesson(val current: Lesson, val next: Lesson?, val progress: Float) : LessonState

    /** Before the first lesson or in a break. */
    data class Upcoming(val next: Lesson) : LessonState

    /** Lessons are over for today. */
    data class DayOver(val next: NextSchoolDay?) : LessonState

    /** Holiday from Schoolhelp, e.g. "Ferien". */
    data class DayOff(val title: String, val next: NextSchoolDay?) : LessonState

    /** Saturday or Sunday. */
    data class Weekend(val next: NextSchoolDay?) : LessonState
}

/** First lesson of the next day with school (skips weekends and days off, up to [BriefLogic.LookAheadDays]). */
data class NextSchoolDay(val date: LocalDate, val first: Lesson)

object BriefLogic {

    const val AheadDays = 14L
    const val LookAheadDays = 21L
    val StaleAfter: Duration = Duration.ofHours(12)

    /**
     * Homework and projects: hidden once done, kept while overdue.
     * Tests and Termine: shown until their date passes, even if ticked.
     * Overdue first, then by date and time. Nothing beyond [AheadDays].
     */
    fun visibleTasks(tasks: List<SchoolTask>, today: LocalDate): List<TaskRow> {
        val horizon = today.plusDays(AheadDays)
        return tasks.mapNotNull { t ->
            val due = runCatching { LocalDate.parse(t.due) }.getOrNull() ?: return@mapNotNull null
            if (due.isAfter(horizon)) return@mapNotNull null
            val datedKind = t.kind == SchoolTask.Test || t.kind == SchoolTask.Appointment
            when {
                datedKind && due.isBefore(today) -> null
                !datedKind && t.done -> null
                else -> TaskRow(t, due, overdue = !datedKind && due.isBefore(today))
            }
        }.sortedWith(
            compareByDescending<TaskRow> { it.overdue }
                .thenBy { it.due }
                .thenBy { it.task.time ?: "" }
        )
    }

    fun lessonState(feed: Feed, now: LocalDateTime): LessonState {
        val today = now.toLocalDate()
        dayOff(feed, today)?.let { return LessonState.DayOff(it.title, nextSchoolDay(feed, today)) }
        if (today.dayOfWeek == DayOfWeek.SATURDAY || today.dayOfWeek == DayOfWeek.SUNDAY) {
            return LessonState.Weekend(nextSchoolDay(feed, today))
        }
        val lessons = lessonsOn(feed, today)
        val t = now.toLocalTime()
        lessons.firstOrNull { t >= time(it.start) && t < time(it.end) }?.let { current ->
            val next = lessons.firstOrNull { time(it.start) >= time(current.end) }
            val total = Duration.between(time(current.start), time(current.end)).seconds.toFloat()
            val done = Duration.between(time(current.start), t).seconds.toFloat()
            return LessonState.InLesson(current, next, (done / total).coerceIn(0f, 1f))
        }
        lessons.firstOrNull { time(it.start) > t }?.let { return LessonState.Upcoming(it) }
        return LessonState.DayOver(nextSchoolDay(feed, today))
    }

    fun nextSchoolDay(feed: Feed, after: LocalDate): NextSchoolDay? {
        for (i in 1..LookAheadDays) {
            val d = after.plusDays(i)
            if (dayOff(feed, d) != null) continue
            lessonsOn(feed, d).firstOrNull()?.let { return NextSchoolDay(d, it) }
        }
        return null
    }

    fun isStale(feed: Feed, now: OffsetDateTime): Boolean {
        val updated = runCatching { OffsetDateTime.parse(feed.updated) }.getOrNull() ?: return true
        return Duration.between(updated, now) > StaleAfter
    }

    private fun dayOff(feed: Feed, date: LocalDate): DayOff? = feed.daysOff.firstOrNull {
        val start = runCatching { LocalDate.parse(it.start) }.getOrNull() ?: return@firstOrNull false
        val end = runCatching { LocalDate.parse(it.end) }.getOrNull() ?: return@firstOrNull false
        !date.isBefore(start) && !date.isAfter(end)
    }

    private fun lessonsOn(feed: Feed, date: LocalDate): List<Lesson> {
        val key = when (date.dayOfWeek) {
            DayOfWeek.MONDAY -> "mon"
            DayOfWeek.TUESDAY -> "tue"
            DayOfWeek.WEDNESDAY -> "wed"
            DayOfWeek.THURSDAY -> "thu"
            DayOfWeek.FRIDAY -> "fri"
            else -> return emptyList()
        }
        return feed.timetable[key].orEmpty().sortedBy { it.start }
    }

    private fun time(s: String): LocalTime = LocalTime.parse(s)
}
