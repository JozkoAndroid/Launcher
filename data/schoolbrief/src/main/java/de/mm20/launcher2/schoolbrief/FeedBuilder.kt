package de.mm20.launcher2.schoolbrief

import kotlinx.serialization.json.JsonArray
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.booleanOrNull
import kotlinx.serialization.json.contentOrNull
import kotlinx.serialization.json.jsonArray
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive
import java.time.LocalDate

/**
 * Turns raw Schoolhelp results (list_events, timetable.read) into feed parts.
 * Kotlin port of the earlier build_feed.py, same rules.
 */
object FeedBuilder {

    private val kinds = setOf(SchoolTask.Homework, SchoolTask.Test, SchoolTask.Project, SchoolTask.Appointment)
    private val days = mapOf("Mon" to "mon", "Tue" to "tue", "Wed" to "wed", "Thu" to "thu", "Fri" to "fri")

    data class Parts(val tasks: List<SchoolTask>, val daysOff: List<DayOff>)

    /** [items] = the "items" array of list_events, all pages concatenated. */
    fun tasks(items: List<JsonObject>, today: LocalDate): Parts {
        val tasks = mutableListOf<SchoolTask>()
        val daysOff = mutableListOf<DayOff>()
        val horizon = today.plusDays(BriefLogic.AheadDays)
        for (e in items) {
            val kind = e.str("kind") ?: continue
            if (kind !in kinds) continue
            val startRaw = e.str("start") ?: continue
            val due = runCatching { LocalDate.parse(startRaw.substringBefore('T')) }.getOrNull() ?: continue
            val time = startRaw.substringAfter('T', "").take(5).ifEmpty { null }
            val endRaw = e.str("end").orEmpty()
            // A multi-day all-day Termin (Ferien etc.) means no school, not a task.
            if (kind == SchoolTask.Appointment && time == null && endRaw.isNotEmpty() && 'T' !in endRaw) {
                val end = runCatching { LocalDate.parse(endRaw) }.getOrNull()
                if (end != null && end.isAfter(due)) {
                    if (!end.isBefore(today)) daysOff += DayOff(e.str("title").orEmpty(), due.toString(), end.toString())
                    continue
                }
            }
            if (due.isAfter(horizon)) continue
            val done = e["done"]?.jsonPrimitive?.booleanOrNull == true
            val dated = kind == SchoolTask.Test || kind == SchoolTask.Appointment
            if (dated && due.isBefore(today)) continue
            if (done) continue
            val folder = e.str("folder").orEmpty()
            tasks += SchoolTask(
                id = e.str("id") ?: continue,
                kind = kind,
                title = e.str("title") ?: continue, // never translated or edited
                subject = if (folder == "Unsorted") "" else folder,
                due = due.toString(),
                time = time,
                done = done,
                estimated = e.str("note").orEmpty().contains("Datum geschätzt"),
                note = e.str("note")?.takeIf { kind == SchoolTask.Test && it.isNotBlank() }?.take(600),
            )
        }
        return Parts(tasks, daysOff)
    }

    /** timetable.read → per weekday lessons with subject names; double lessons merged. */
    fun timetable(tt: JsonObject): Map<String, List<Lesson>> {
        val periods = tt["periods"]?.jsonObject ?: return emptyMap()
        val subjects = tt["subjects"]?.jsonObject
        val week = tt["week"]?.jsonObject ?: return emptyMap()
        val out = linkedMapOf<String, List<Lesson>>()
        for ((day, slotsEl) in week) {
            val key = days[day] ?: continue
            val slots = slotsEl.jsonObject
            val lessons = mutableListOf<Lesson>()
            var lastPeriod = -10
            for (p in slots.keys.sortedBy { it.toIntOrNull() ?: 99 }) {
                val n = p.toIntOrNull() ?: 99
                val code = slots[p]?.jsonPrimitive?.contentOrNull ?: continue
                val range = periods[p]?.jsonArray ?: continue
                val start = range.getOrNull(0)?.jsonPrimitive?.contentOrNull ?: continue
                val end = range.getOrNull(1)?.jsonPrimitive?.contentOrNull ?: continue
                val name = subjects?.get(code)?.jsonObject?.str("folder") ?: code
                val last = lessons.lastOrNull()
                // Merge only directly consecutive periods (a double lesson), never across a gap or lunch.
                if (last != null && last.subject == name && n == lastPeriod + 1) {
                    lessons[lessons.lastIndex] = last.copy(end = end)
                } else {
                    lessons += Lesson(start, end, name)
                }
                lastPeriod = n
            }
            out[key] = lessons
        }
        return out
    }

    private fun JsonObject.str(k: String): String? = (this[k] as? JsonPrimitive)?.contentOrNull

    @Suppress("unused")
    private fun JsonArray.objects() = map { it.jsonObject }
}
