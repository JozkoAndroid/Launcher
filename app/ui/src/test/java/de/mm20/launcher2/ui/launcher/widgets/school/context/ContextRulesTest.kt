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
        val events = listOf(
            day + 5 * min to false,       // session from before midnight: 5 min
            day + 60 * min to true, day + 80 * min to false, // 20 min
            day + 100 * min to true,      // still open: until now
        )
        assertEquals(5 + 20 + 22, ContextRules.foregroundMinutes(events, day + 122 * min, day))
    }

    @Test
    fun musicFirstThenUrgencyMaxThree() {
        val cards = listOf(
            ContextCard.Instagram(47), ContextCard.Steps(3120, 8000), ContextCard.Birthday("Jana", 1),
            ContextCard.MobileData(3_000_000_000L, 3_500_000_000L), ContextCard.Music("Song", null, true),
        )
        assertEquals(listOf(0, 1, 2), ContextRules.pick(cards).map { it.rank })
    }
}
