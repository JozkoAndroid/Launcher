package de.mm20.launcher2.schoolbrief

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test
import java.time.LocalDate
import java.time.LocalDateTime
import java.time.OffsetDateTime

class BriefLogicTest {

    // A Wednesday.
    private val today = LocalDate.parse("2026-11-11")

    private fun task(id: String, kind: String, due: String, done: Boolean = false, time: String? = null) =
        SchoolTask(id = id, kind = kind, title = "T $id", due = due, done = done, time = time)

    @Test
    fun doneHomeworkIsHiddenButTickedTestStaysUntilItsDate() {
        val rows = BriefLogic.visibleTasks(
            listOf(
                task("hw", SchoolTask.Homework, "2026-11-12", done = true),
                task("test", SchoolTask.Test, "2026-11-20", done = true),
            ),
            today,
        )
        assertEquals(listOf("test"), rows.map { it.task.id })
    }

    @Test
    fun pastTestsAndTermineDisappearOverdueHomeworkStays() {
        val rows = BriefLogic.visibleTasks(
            listOf(
                task("oldTest", SchoolTask.Test, "2026-11-10"),
                task("oldTermin", SchoolTask.Appointment, "2026-11-10"),
                task("lateHw", SchoolTask.Homework, "2026-11-09"),
            ),
            today,
        )
        assertEquals(listOf("lateHw"), rows.map { it.task.id })
        assertTrue(rows.single().overdue)
    }

    @Test
    fun overdueFirstThenDateThenTimeAndNothingBeyond14Days() {
        val rows = BriefLogic.visibleTasks(
            listOf(
                task("far", SchoolTask.Test, "2026-11-26"),
                task("edge", SchoolTask.Test, "2026-11-25"),
                task("late", SchoolTask.Appointment, "2026-11-12", time = "17:00"),
                task("early", SchoolTask.Appointment, "2026-11-12", time = "09:00"),
                task("overdue", SchoolTask.Project, "2026-11-01"),
            ),
            today,
        )
        assertEquals(listOf("overdue", "early", "late", "edge"), rows.map { it.task.id })
    }

    @Test
    fun invalidDateIsSkippedNotCrashing() {
        assertTrue(BriefLogic.visibleTasks(listOf(task("x", SchoolTask.Test, "soon")), today).isEmpty())
    }

    private val feed = Feed(
        updated = "2026-11-11T06:30:00+01:00",
        brief = "",
        timetable = mapOf(
            "wed" to listOf(
                Lesson("09:55", "11:30", "Deutsch"),
                Lesson("08:00", "09:35", "Slovakisch"),
            ),
            "thu" to listOf(Lesson("08:00", "09:35", "Sport")),
        ),
        daysOff = listOf(DayOff("Ferien", "2026-11-13", "2026-11-13")),
    )

    @Test
    fun inLessonWithProgressAndNext() {
        val s = BriefLogic.lessonState(feed, LocalDateTime.parse("2026-11-11T08:47:30"))
        s as LessonState.InLesson
        assertEquals("Slovakisch", s.current.subject)
        assertEquals("Deutsch", s.next?.subject)
        assertEquals(0.5f, s.progress, 0.01f)
    }

    @Test
    fun breakShowsUpcoming() {
        val s = BriefLogic.lessonState(feed, LocalDateTime.parse("2026-11-11T09:40"))
        assertEquals(LessonState.Upcoming(Lesson("09:55", "11:30", "Deutsch")), s)
    }

    @Test
    fun afterSchoolShowsTomorrowsFirstLesson() {
        val s = BriefLogic.lessonState(feed, LocalDateTime.parse("2026-11-11T12:00"))
        assertEquals(LessonState.DayOver(NextSchoolDay(LocalDate.parse("2026-11-12"), Lesson("08:00", "09:35", "Sport"))), s)
    }

    @Test
    fun beforeHolidayAndWeekendTheNextSchoolDaySkipsBoth() {
        // Thu after school; Fri 13 is Ferien; Sat/Sun weekend; Mon has no lessons in this feed; Wed 18 does.
        val s = BriefLogic.lessonState(feed, LocalDateTime.parse("2026-11-12T12:00"))
        assertEquals(LessonState.DayOver(NextSchoolDay(LocalDate.parse("2026-11-18"), Lesson("08:00", "09:35", "Slovakisch"))), s)
    }

    @Test
    fun holidayAndWeekendShowTheNextSchoolDay() {
        val next = NextSchoolDay(LocalDate.parse("2026-11-18"), Lesson("08:00", "09:35", "Slovakisch"))
        assertEquals(LessonState.DayOff("Ferien", next), BriefLogic.lessonState(feed, LocalDateTime.parse("2026-11-13T09:00")))
        assertEquals(LessonState.Weekend(next), BriefLogic.lessonState(feed, LocalDateTime.parse("2026-11-14T09:00")))
        assertEquals(null, BriefLogic.nextSchoolDay(feed.copy(timetable = emptyMap()), LocalDate.parse("2026-11-14")))
    }

    @Test
    fun staleAfter12Hours() {
        assertFalse(BriefLogic.isStale(feed, OffsetDateTime.parse("2026-11-11T18:30:00+01:00")))
        assertTrue(BriefLogic.isStale(feed, OffsetDateTime.parse("2026-11-11T18:31:00+01:00")))
        assertTrue(BriefLogic.isStale(feed.copy(updated = "garbage"), OffsetDateTime.parse("2026-11-11T07:00:00+01:00")))
    }

    @Test
    fun sampleFeedHasEveryTaskKindVisible() {
        val f = SampleFeed.create(today, OffsetDateTime.parse("2026-11-11T13:30:00+01:00"))
        val kinds = BriefLogic.visibleTasks(f.tasks, today).map { it.task.kind }.toSet()
        assertEquals(setOf("hausaufgabe", "test", "projekt", "termin"), kinds)
    }
}
