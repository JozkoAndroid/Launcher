package de.mm20.launcher2.ui.launcher.widgets.school.context

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test
import java.time.LocalDate
import java.time.LocalTime

class ContextRulesTest {

    private val s = ContextSettings()

    @Test
    fun rollingThirtyDayCycleAnchoredOn18November() {
        assertEquals(LocalDate.parse("2026-10-19"), ContextRules.cycleStart(LocalDate.parse("2026-11-07"), s))
        assertEquals(LocalDate.parse("2026-11-18"), ContextRules.cycleStart(LocalDate.parse("2026-11-18"), s))
        assertEquals(LocalDate.parse("2026-11-18"), ContextRules.cycleStart(LocalDate.parse("2026-12-17"), s))
        assertEquals(LocalDate.parse("2026-12-18"), ContextRules.cycleStart(LocalDate.parse("2026-12-18"), s))
    }

    @Test
    fun dataCardFromEightyPercentOfThreePointFiveGb() {
        assertFalse(ContextRules.showData(2_799_999_999L, s))
        assertTrue(ContextRules.showData(2_800_000_000L, s))
    }

    @Test
    fun stepsOnlyAfterThreeAndBelowGoal() {
        assertFalse(ContextRules.showSteps(LocalTime.of(14, 59), 100, s))
        assertTrue(ContextRules.showSteps(LocalTime.of(15, 0), 7999, s))
        assertFalse(ContextRules.showSteps(LocalTime.of(18, 0), 8000, s))
    }

    @Test
    fun birthdaysWithAndWithoutYearAndLeapDay() {
        val d = LocalDate.parse("2026-11-07")
        assertTrue(ContextRules.isBirthdayToday("--11-07", d))
        assertTrue(ContextRules.isBirthdayToday("2009-11-07", d))
        assertFalse(ContextRules.isBirthdayToday("2009-11-08", d))
        assertTrue(ContextRules.isBirthdayToday("2008-02-29", LocalDate.parse("2027-02-28")))
        assertFalse(ContextRules.isBirthdayToday("2008-02-29", LocalDate.parse("2028-02-28")))
        assertFalse(ContextRules.isBirthdayToday("7.11.", d))
    }

    @Test
    fun foregroundMinutesCountsOpenAndOvernightSessions() {
        val day = 1_000_000_000L
        val min = 60_000L
        fun ev(m: Long, screen: String, resumed: Boolean) = ContextRules.AppEvent(day + m * min, screen, resumed)
        val events = listOf(
            ev(5, "Main", false),                      // open since before midnight: 5 min
            ev(60, "Main", true), ev(70, "Story", true), // switching screens is one session
            ev(71, "Main", false), ev(80, "Story", false), // 20 min
            ev(100, "Main", true),                     // still open: until now (22 min)
        )
        assertEquals(5 + 20 + 22, ContextRules.foregroundMinutes(events, day + 122 * min, day))
    }

    /** Device test: thousands of minutes. Pauses of screens never resumed today must not add time again. */
    @Test
    fun foregroundMinutesIgnoresRepeatedOrphanPauses() {
        val day = 1_000_000_000L
        val min = 60_000L
        val events = listOf(
            ContextRules.AppEvent(day + 600 * min, "Main", true),
            ContextRules.AppEvent(day + 610 * min, "Main", false),
            ContextRules.AppEvent(day + 700 * min, "Other", false),
            ContextRules.AppEvent(day + 800 * min, "Other", false),
        )
        assertEquals(10, ContextRules.foregroundMinutes(events, day + 900 * min, day))
    }

    @Test
    fun musicFirstThenUrgencyMaxFour() {
        val cards = listOf(
            ContextCard.Instagram(47), ContextCard.Steps(3120, 8000), ContextCard.Birthday("Jana", 1),
            ContextCard.MobileData(3_000_000_000L, 3_500_000_000L), ContextCard.Music("Song", null, true),
        )
        assertEquals(
            listOf(ContextCard.Music::class, ContextCard.MobileData::class, ContextCard.Birthday::class, ContextCard.Steps::class),
            ContextRules.pick(cards).map { it::class },
        )
    }

    @Test
    fun testCountdownPicksNearestOpenTestWithinTheWeek() {
        val d = LocalDate.parse("2026-11-10")
        fun t(id: String, due: String, done: Boolean = false, kind: String = "test") =
            de.mm20.launcher2.schoolbrief.SchoolTask(id, kind, "T$id", "Bio", due, done = done)
        val tasks = listOf(t("far", "2026-11-14"), t("done", "2026-11-11", done = true), t("hw", "2026-11-11", kind = "hausaufgabe"), t("soon", "2026-11-12"))
        assertEquals(ContextCard.TestSoon("Tsoon", "Bio", 2), ContextRules.testSoon(tasks, d))
        // Round 35: a test 4 days ahead (Thursday to Monday) has its countdown; "Práve teraz" waits until 3 days.
        val monday = ContextRules.testSoon(listOf(t("far", "2026-11-14")), d)
        assertEquals(ContextCard.TestSoon("Tfar", "Bio", 4), monday)
        assertFalse(ContextRules.urgent(monday!!, java.time.LocalTime.NOON, ContextSettings()))
        assertEquals(null, ContextRules.testSoon(listOf(t("later", "2026-11-18")), d))
    }

    @Test
    fun lunchOutranksBirthdayAndNameDay() {
        val cards = listOf(ContextCard.NameDay(listOf("René"), null, null), ContextCard.Lunch("Rizoto", "x"), ContextCard.Birthday("Jana", 1))
        assertEquals(ContextCard.Lunch::class, ContextRules.pick(cards).first()::class)
    }

    @Test
    fun lunchAppAndTextDetection() {
        // EduPage never sends the menu (device test): never a lunch source.
        assertFalse(ContextRules.looksLikeLunchApp("EduPage Jedálny lístok"))
        assertTrue(ContextRules.isEduPage("sk.edupage.android"))
        assertTrue(ContextRules.looksLikeLunchApp("School Food"))
        assertTrue(ContextRules.looksLikeLunchApp("Lunch Bell"))
        assertEquals("2", ContextRules.menuNumber("2"))
        assertEquals("Z2", ContextRules.menuNumber("Z2"))
        assertEquals("3", ContextRules.menuNumber(" 3 "))
        assertTrue(ContextRules.lunchVisible(LocalDate.parse("2026-11-10"), java.time.LocalDateTime.parse("2026-11-09T20:00")))
        assertTrue(ContextRules.lunchVisible(LocalDate.parse("2026-11-10"), java.time.LocalDateTime.parse("2026-11-10T14:59")))
        assertFalse(ContextRules.lunchVisible(LocalDate.parse("2026-11-10"), java.time.LocalDateTime.parse("2026-11-10T15:00")))
        assertFalse(ContextRules.lunchVisible(LocalDate.parse("2026-11-12"), java.time.LocalDateTime.parse("2026-11-09T20:00")))
        assertEquals(null, ContextRules.menuNumber("Rizoto s kuracím mäsom"))
        assertEquals(LocalDate.parse("2026-11-10"), ContextRules.lunchDay(java.time.LocalDateTime.parse("2026-11-09T18:30")))
        assertEquals(LocalDate.parse("2026-11-10"), ContextRules.lunchDay(java.time.LocalDateTime.parse("2026-11-10T07:00")))
        assertFalse(ContextRules.looksLikeLunchApp("Instagram"))
        assertEquals("Polievka, rizoto", ContextRules.lunchText("Obed", "Polievka,\n  rizoto", null))
        assertEquals("Obed dnes", ContextRules.lunchText("Obed dnes", " ", null))
        // Round 35: Lunch Bell puts the menu in the title.
        assertEquals("Z2", ContextRules.lunchText("Z2", "Your lunch is ready", null))
        assertEquals("Z1", ContextRules.lunchText("Lunch Bell", "Your lunch is ready", "Z1"))
    }

    @Test
    fun nameDaysAndContactMatch() {
        val cal = mapOf("11-11" to listOf("Martin", "Maroš"))
        assertEquals(listOf("Martin", "Maroš"), ContextRules.nameDay(cal, LocalDate.parse("2026-11-11")))
        assertTrue(ContextRules.firstNameMatches("Martin Novák", listOf("Martin")))
        assertFalse(ContextRules.firstNameMatches("Martina Nová", listOf("Martin")))
    }
}
