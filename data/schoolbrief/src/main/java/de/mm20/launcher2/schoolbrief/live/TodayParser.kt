package de.mm20.launcher2.schoolbrief.live

import de.mm20.launcher2.schoolbrief.PlanStep
import de.mm20.launcher2.schoolbrief.TodayData
import de.mm20.launcher2.schoolbrief.Todo
import kotlinx.serialization.json.JsonArray
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.booleanOrNull
import kotlinx.serialization.json.contentOrNull
import kotlinx.serialization.json.intOrNull
import java.time.LocalDate

/** today.read -> today's plan steps and the personal tasks (done ones older than today are dropped). */
internal object TodayParser {
    fun parse(root: JsonObject, today: LocalDate): TodayData {
        val r = (root["result"] as? JsonObject) ?: root
        val day = today.toString()
        val plan = (r["plan"] as? JsonObject)?.takeIf { str(it, "day") == null || str(it, "day") == day }
            ?: (r["plans"] as? JsonArray)?.mapNotNull { it as? JsonObject }?.firstOrNull { str(it, "day") == day }
        val steps = (plan?.get("steps") as? JsonArray)?.mapIndexedNotNull { i, e ->
            val o = e as? JsonObject ?: return@mapIndexedNotNull null
            PlanStep(
                index = i,
                text = str(o, "step") ?: return@mapIndexedNotNull null,
                minutes = (o["minutes"] as? JsonPrimitive)?.intOrNull,
                subject = str(o, "subject").orEmpty(),
                done = (o["done"] as? JsonPrimitive)?.booleanOrNull == true,
            )
        }.orEmpty()
        val todos = (r["todos"] as? JsonArray)?.mapNotNull { e ->
            val o = e as? JsonObject ?: return@mapNotNull null
            Todo(str(o, "id") ?: return@mapNotNull null, str(o, "text") ?: return@mapNotNull null, (o["done"] as? JsonPrimitive)?.booleanOrNull == true)
        }.orEmpty()
        return TodayData(day, steps, todos)
    }

    private fun str(o: JsonObject, k: String) = (o[k] as? JsonPrimitive)?.contentOrNull?.takeIf { it.isNotBlank() }
}
