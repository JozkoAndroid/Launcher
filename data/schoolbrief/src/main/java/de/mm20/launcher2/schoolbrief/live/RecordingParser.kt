package de.mm20.launcher2.schoolbrief.live

import de.mm20.launcher2.schoolbrief.Recording
import kotlinx.serialization.json.JsonArray
import kotlinx.serialization.json.JsonElement
import kotlinx.serialization.json.JsonNull
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.booleanOrNull
import kotlinx.serialization.json.contentOrNull
import kotlinx.serialization.json.doubleOrNull
import java.time.Duration
import java.time.LocalDateTime

/**
 * lessons.status -> the running recording. The launcher has its own Schoolhelp workspace, so a recording
 * started on another device may show only in "alive", not in "lesson" (device test: recording never showed).
 */
internal object RecordingParser {

    fun parse(status: JsonObject, now: LocalDateTime): Recording? {
        val root = (status["result"] as? JsonObject) ?: status
        val codes = (root["subjects"] as? JsonObject).orEmpty()
        // "alive" also lists Schoolhelp's own services (seen live, Nov 2026: ["browser"] next to a running
        // lesson). Only an entry that names a lesson counts: a "Folder/date" id, a subject code or folder.
        val folders = codes.values.mapNotNull { (it as? JsonObject)?.let { o -> str(o, "folder") } }.toSet()
        fun isLesson(e: JsonElement): Boolean {
            val o = e as? JsonObject
            val id = o?.let { str(it, "id") ?: str(it, "folder") ?: str(it, "subject") } ?: (e as? JsonPrimitive)?.contentOrNull ?: return false
            val head = id.substringBefore('/')
            return '/' in id || head in codes || head in folders || (o != null && (o["audio_seconds"] != null || o["paused"] != null))
        }
        val entry: JsonElement = root["lesson"].takeUnless { it == null || it is JsonNull }
            ?: (root["alive"] as? JsonArray)?.firstOrNull { isLesson(it) }
            // Round 25 (device test: a recording on another device never showed): a running lesson can sit
            // elsewhere in the status (per device, per workspace). Any object that looks like a recording
            // lesson (audio seconds or paused, plus a lesson id or folder) counts, wherever it is.
            ?: deepFind(root)
            ?: return null
        val o = entry as? JsonObject
        val raw = o?.let { str(it, "folder") ?: str(it, "subject") ?: str(it, "fach") ?: str(it, "name") ?: str(it, "id") }
            ?: (entry as? JsonPrimitive)?.contentOrNull
            ?: ""
        // "Physik/2026-11-10-0800" or a code like "Ph" -> folder name.
        val name = raw.substringBefore('/').let { c -> (codes[c] as? JsonObject)?.let { str(it, "folder") } ?: c }
        val paused = o?.let { bool(it, "paused") } ?: false
        val seconds = o?.let { num(it, "audio_seconds") ?: num(it, "seconds") ?: num(it, "duration") }?.toLong()
            ?: o?.let { str(it, "started") ?: str(it, "start") }?.let { s ->
                runCatching { Duration.between(LocalDateTime.parse(s.take(19)), now).seconds.coerceAtLeast(0) }.getOrNull()
            }
            ?: 0
        return Recording(name, paused, seconds)
    }

    private fun deepFind(e: JsonElement, depth: Int = 0): JsonObject? {
        if (depth > 4) return null
        return when (e) {
            is JsonObject -> {
                val looks = (e["audio_seconds"] != null || e["paused"] != null) &&
                    (str(e, "folder") != null || str(e, "id")?.contains('/') == true)
                if (looks) e else e.entries.filter { it.key != "subjects" }.firstNotNullOfOrNull { deepFind(it.value, depth + 1) }
            }
            is JsonArray -> e.firstNotNullOfOrNull { deepFind(it, depth + 1) }
            else -> null
        }
    }

    /** Folder -> language ("de", "en", "sk") from the "subjects" map. */
    fun subjectLangs(status: JsonObject): Map<String, String> {
        val root = (status["result"] as? JsonObject) ?: status
        return (root["subjects"] as? JsonObject).orEmpty().values.mapNotNull { v ->
            val o = v as? JsonObject ?: return@mapNotNull null
            val folder = str(o, "folder") ?: return@mapNotNull null
            str(o, "lang")?.let { folder to it }
        }.toMap()
    }

    private fun str(o: JsonObject, k: String) = (o[k] as? JsonPrimitive)?.contentOrNull?.takeIf { it.isNotBlank() }
    private fun bool(o: JsonObject, k: String) = (o[k] as? JsonPrimitive)?.booleanOrNull
    private fun num(o: JsonObject, k: String) = (o[k] as? JsonPrimitive)?.doubleOrNull
}
