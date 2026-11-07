package de.mm20.launcher2.schoolbrief

import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable

/**
 * Everything the home screen shows about school and news.
 * Same shape as the former feed.json, so the format is documented in one place.
 */
@Serializable
data class Feed(
    /** ISO-8601 with offset, e.g. 2026-11-07T13:30:00+01:00 */
    val updated: String,
    /** 2-3 Slovak sentences; quotes task titles verbatim. */
    val brief: String,
    val news: List<NewsItem> = emptyList(),
    val tasks: List<SchoolTask> = emptyList(),
    @SerialName("days_off") val daysOff: List<DayOff> = emptyList(),
    /** Keys mon..fri. Double lessons are already merged. */
    val timetable: Map<String, List<Lesson>> = emptyMap(),
)

@Serializable
data class NewsItem(
    val topic: String,
    val title: String,
    val url: String,
)

@Serializable
data class SchoolTask(
    val id: String,
    /** hausaufgabe | test | projekt | termin */
    val kind: String,
    /** Never translated or edited. */
    val title: String,
    val subject: String = "",
    /** YYYY-MM-DD */
    val due: String,
    /** HH:MM, only for Termine with a time */
    val time: String? = null,
    val done: Boolean = false,
    /** Schoolhelp guessed the date ("Datum geschätzt"). */
    val estimated: Boolean = false,
) {
    companion object {
        const val Homework = "hausaufgabe"
        const val Test = "test"
        const val Project = "projekt"
        const val Appointment = "termin"
    }
}

@Serializable
data class DayOff(
    val title: String,
    /** YYYY-MM-DD, inclusive */
    val start: String,
    /** YYYY-MM-DD, inclusive */
    val end: String,
)

@Serializable
data class Lesson(
    /** HH:MM */
    val start: String,
    /** HH:MM */
    val end: String,
    val subject: String,
)
