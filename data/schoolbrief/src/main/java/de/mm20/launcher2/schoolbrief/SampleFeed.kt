package de.mm20.launcher2.schoolbrief

import java.time.LocalDate
import java.time.OffsetDateTime
import java.time.temporal.ChronoUnit

/**
 * Invented but realistic data for build step 1, before the live Schoolhelp source exists.
 * Dates are relative to [today] so the screen always looks current.
 * Never put real Schoolhelp data here: this repository is public.
 */
object SampleFeed {

    fun create(today: LocalDate = LocalDate.now(), now: OffsetDateTime = OffsetDateTime.now()): Feed {
        fun d(days: Long) = today.plusDays(days).toString()
        return Feed(
            updated = now.truncatedTo(ChronoUnit.MINUTES).minusMinutes(25).toString(),
            brief = "Dnes ťa čaká hlavne „Mathe: Arbeitsblatt Vektoren Seite 2“, odovzdáva sa zajtra. " +
                "Vo štvrtok píšeš „Biologie Leistungsnachweis“ a v piatok je „Elternsprechtag“.",
            news = listOf(
                NewsItem("sk", "Bratislava otvára novú električkovú trať do Petržalky", "https://dennikn.sk/"),
                NewsItem("eu", "Europarlament schválil prísnejšie overovanie veku na sociálnych sieťach", "https://www.teraz.sk/"),
                NewsItem("tech", "Android 17 QPR1 brings desktop windowing to foldables", "https://www.theverge.com/"),
                NewsItem("aviation", "Airbus confirms A350F first delivery slips to next year", "https://simpleflying.com/"),
            ),
            tasks = listOf(
                SchoolTask("s1", SchoolTask.Homework, "Deutsch: Erörterung zum Thema Social Media gliedern", "Deutsch", d(-1)),
                SchoolTask("s2", SchoolTask.Homework, "Mathe: Arbeitsblatt Vektoren Seite 2", "Mathe", d(1)),
                SchoolTask("s3", SchoolTask.Homework, "Prečítať Hviezdoslav: Hájnikova žena, I. spev", "Slovakisch", d(2), estimated = true),
                SchoolTask("s4", SchoolTask.Test, "Biologie Leistungsnachweis", "Bio", d(3)),
                SchoolTask("s5", SchoolTask.Appointment, "Elternsprechtag", "", d(4), time = "16:00"),
                SchoolTask("s6", SchoolTask.Project, "Wirtschaft: Werbeplakat für die Schülerfirma", "Wirtschaft", d(6)),
                SchoolTask("s7", SchoolTask.Test, "English vocabulary test Unit 4", "English", d(9)),
                SchoolTask("s8", SchoolTask.Homework, "Chemie: Protokoll Titration", "Chemie", d(-3), done = true),
            ),
            daysOff = listOf(DayOff("Weihnachtsferien", "2026-12-21", "2027-01-06")),
            timetable = mapOf(
                "mon" to listOf(
                    Lesson("08:00", "09:35", "Mathe"), Lesson("09:55", "11:30", "Deutsch"),
                    Lesson("11:45", "13:20", "English"), Lesson("14:05", "15:35", "Slovakisch"),
                ),
                "tue" to listOf(
                    Lesson("08:00", "09:35", "Physik"), Lesson("09:55", "10:40", "Wirtschaft"),
                    Lesson("10:45", "11:30", "Deutsch"), Lesson("11:45", "12:30", "Bio"),
                    Lesson("12:35", "13:20", "English"), Lesson("14:05", "15:35", "Geschichte"),
                ),
                "wed" to listOf(
                    Lesson("08:00", "09:35", "Slovakisch"), Lesson("09:55", "11:30", "Deutsch"),
                    Lesson("11:45", "13:20", "English"), Lesson("14:05", "14:50", "Ethik"),
                    Lesson("14:50", "15:35", "Chemie"),
                ),
                "thu" to listOf(
                    Lesson("08:00", "09:35", "Sport"), Lesson("09:55", "11:30", "Bio"),
                    Lesson("11:45", "13:20", "Geschichte"), Lesson("14:05", "15:35", "Mathe"),
                ),
                "fri" to listOf(
                    Lesson("08:00", "09:35", "Deutsch"), Lesson("09:55", "10:40", "Mathe"),
                    Lesson("10:45", "11:30", "Wirtschaft"), Lesson("11:45", "13:20", "Chemie"),
                    Lesson("14:05", "15:35", "Kunst"),
                ),
            ),
        )
    }
}
