package de.mm20.launcher2.schoolbrief.live

import kotlinx.serialization.Serializable
import kotlinx.serialization.builtins.ListSerializer
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonObject
import java.io.File

/** A Schoolhelp change made on the phone; [localId] = the temporary id of a task added offline. */
@Serializable
internal data class PendingOp(val action: String, val args: JsonObject, val localId: String? = null, val refusals: Int = 0)

/**
 * Changes waiting for Schoolhelp (device test: Schoolhelp was down and new tasks were lost). Kept in a
 * small file, oldest first, so nothing is lost when the app is closed; sent in order once Schoolhelp answers.
 */
internal class PendingQueue(private val file: File) {
    private val serializer = ListSerializer(PendingOp.serializer())

    @Synchronized
    fun all(): List<PendingOp> = runCatching { Json.decodeFromString(serializer, file.readText()) }.getOrDefault(emptyList())

    @Synchronized
    fun add(op: PendingOp) = write(all() + op)

    @Synchronized
    fun remove(op: PendingOp) = write(all().let { list -> list.toMutableList().apply { remove(op) } })

    @Synchronized
    fun replace(old: PendingOp, new: PendingOp) = write(all().map { if (it == old) new else it })

    fun isEmpty() = all().isEmpty()

    private fun write(list: List<PendingOp>) {
        file.parentFile?.mkdirs()
        val tmp = File(file.parentFile, file.name + ".tmp")
        tmp.writeText(Json.encodeToString(serializer, list))
        tmp.renameTo(file)
    }
}
