package de.mm20.launcher2.ui.launcher.widgets.school

import android.content.Context
import de.mm20.launcher2.schoolbrief.ChatMessage
import org.json.JSONArray
import org.json.JSONObject

/** One saved conversation: when it started, its first question as the title, the messages. */
data class SavedChat(val id: Long, val title: String, val messages: List<ChatMessage>)

/**
 * Recent chats (round 22, user: "a button for recent chats"): the last [Max] conversations, on the phone
 * only (preferences), saved after every answer. The model prompts behind quick buttons are kept too, so a
 * restored chat continues with the same context.
 */
class ChatHistory(context: Context) {
    private val prefs = context.getSharedPreferences("school_chats", Context.MODE_PRIVATE)

    fun all(): List<SavedChat> = runCatching {
        val arr = JSONArray(prefs.getString("chats", "[]"))
        (0 until arr.length()).map { i ->
            val o = arr.getJSONObject(i)
            val ms = o.getJSONArray("m")
            SavedChat(o.getLong("id"), o.getString("t"), (0 until ms.length()).map { j ->
                val m = ms.getJSONObject(j)
                ChatMessage(m.getBoolean("u"), m.getString("x"), m.optString("p").takeIf { it.isNotEmpty() }, failed = m.optBoolean("f"))
            })
        }
    }.getOrDefault(emptyList())

    fun save(id: Long, messages: List<ChatMessage>) {
        if (messages.isEmpty()) return
        val title = messages.firstOrNull { it.fromUser }?.text?.take(80) ?: return
        val chats = listOf(SavedChat(id, title, messages)) + all().filter { it.id != id }
        val arr = JSONArray()
        for (c in chats.take(Max)) {
            arr.put(JSONObject().put("id", c.id).put("t", c.title).put("m", JSONArray().apply {
                for (m in c.messages) put(JSONObject().put("u", m.fromUser).put("x", m.text).put("p", m.modelText ?: "").put("f", m.failed))
            }))
        }
        prefs.edit().putString("chats", arr.toString()).apply()
    }

    companion object { const val Max = 10 }
}
