package de.mm20.launcher2.schoolbrief.live

import kotlinx.serialization.Serializable
import kotlinx.serialization.builtins.ListSerializer
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonObject
import java.io.File

/** A Schoolhelp change made on the phone; [localId] = the temporary id of a task added offline. */
@Serializable
internal data class PendingOp(val action: String, val args: JsonObject, val localId: String? = null)

/**
 * Changes waiting for Schoolhelp (device test: Schoolhelp was down and new tasks were lost). Kept in a
 * small file, oldest first, so nothing is lost when the app is closed; sent in order once Schoolhelp answers.
 */
internal class PendingQueue(private val file: File) {
    private val serializer = ListSerializer(PendingOp.serializer())

    @Synchronized
    // Unknown keys ignored: round 25 saved an extra field; tasks still waiting from then must not be lost.
    fun all(): List<PendingOp> = runCatching { lenient.decodeFromString(serializer, file.readText()) }.getOrDefault(emptyList())

    private val lenient = Json { ignoreUnknownKeys = true }

    @Synchronized
    fun add(op: PendingOp) = write(all() + op)

    @Synchronized
    fun remove(op: PendingOp) = write(all().let { list -> list.toMutableList().apply { remove(op) } })

    fun isEmpty() = all().isEmpty()

    private fun write(list: List<PendingOp>) {
        file.parentFile?.mkdirs()
        val tmp = File(file.parentFile, file.name + ".tmp")
        tmp.writeText(Json.encodeToString(serializer, list))
        tmp.renameTo(file)
    }
}
