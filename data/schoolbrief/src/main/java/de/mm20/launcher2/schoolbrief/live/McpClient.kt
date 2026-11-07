package de.mm20.launcher2.schoolbrief.live

import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonElement
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.buildJsonObject
import kotlinx.serialization.json.contentOrNull
import kotlinx.serialization.json.jsonArray
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive
import kotlinx.serialization.json.put

/** Minimal MCP client over Streamable HTTP: initialize, then tools/call. Read-only use. */
internal class McpClient(private val auth: SchoolhelpAuth) {

    private var sessionId: String? = null
    private var nextId = 1

    class LoggedOut : Exception("Nie si prihlásený do Schoolhelp.")

    /** Calls Schoolhelp's app_call tool, e.g. action "timetable.read", and returns its JSON result. */
    suspend fun appCall(action: String, arguments: JsonObject = JsonObject(emptyMap())): JsonObject {
        if (sessionId == null) initialize()
        val result = rpc("tools/call", buildJsonObject {
            put("name", "app_call")
            put("arguments", buildJsonObject {
                put("name", action)
                put("arguments", arguments)
            })
        })
        return toolJson(result)
    }

    private suspend fun initialize() {
        rpc("initialize", buildJsonObject {
            put("protocolVersion", "2025-06-18")
            put("capabilities", JsonObject(emptyMap()))
            put("clientInfo", buildJsonObject { put("name", "launcher-brief"); put("version", "1.0") })
        })
        post(buildJsonObject { put("jsonrpc", "2.0"); put("method", "notifications/initialized") }, retried = true)
    }

    private suspend fun rpc(method: String, params: JsonObject): JsonObject {
        val msg = buildJsonObject {
            put("jsonrpc", "2.0"); put("id", nextId++); put("method", method); put("params", params)
        }
        val res = post(msg, retried = false)
        val obj = parseResponse(res.body, res.header("Content-Type"))
        obj["error"]?.let { throw IllegalStateException("Schoolhelp: ${it.jsonObject["message"]?.jsonPrimitive?.contentOrNull}") }
        return obj["result"]?.jsonObject ?: JsonObject(emptyMap())
    }

    private suspend fun post(msg: JsonObject, retried: Boolean): HttpResponse {
        val token = auth.token() ?: throw LoggedOut()
        val headers = buildMap {
            put("Content-Type", "application/json")
            put("Accept", "application/json, text/event-stream")
            put("Authorization", "Bearer $token")
            put("MCP-Protocol-Version", "2025-06-18")
            sessionId?.let { put("Mcp-Session-Id", it) }
        }
        val res = Http.request(SchoolhelpAuth.Resource, "POST", Json.encodeToString(JsonObject.serializer(), msg), headers, timeoutMs = 45_000)
        res.header("Mcp-Session-Id")?.let { sessionId = it }
        if (res.code == 401 && !retried) {
            auth.invalidateAccessToken(); return post(msg, retried = true)
        }
        if (res.code == 404 && sessionId != null && !retried) {
            sessionId = null; initialize(); return post(msg, retried = true) // session expired
        }
        if (res.code == 401) throw LoggedOut()
        if (!res.ok) throw HttpException(res.code, "Schoolhelp error ${res.code}")
        return res
    }

    companion object {
        /** A JSON-RPC message from either a plain JSON body or an SSE stream ("data: {...}" lines). */
        fun parseResponse(body: String, contentType: String?): JsonObject {
            if (contentType?.contains("text/event-stream") == true || body.trimStart().startsWith("event:") || body.trimStart().startsWith("data:")) {
                val messages = body.split(Regex("\r?\n\r?\n")).mapNotNull { block ->
                    val data = block.lines().filter { it.startsWith("data:") }.joinToString("\n") { it.removePrefix("data:").trimStart() }
                    data.takeIf { it.isNotBlank() }?.let { runCatching { Json.parseToJsonElement(it).jsonObject }.getOrNull() }
                }
                return messages.lastOrNull { "result" in it || "error" in it } ?: JsonObject(emptyMap())
            }
            if (body.isBlank()) return JsonObject(emptyMap())
            return Json.parseToJsonElement(body).jsonObject
        }

        /** Tool results carry JSON either as structuredContent or as the text of the first content item. */
        fun toolJson(result: JsonObject): JsonObject {
            result["structuredContent"]?.let { if (it is JsonObject) return it }
            if ((result["isError"] as? JsonPrimitive)?.contentOrNull == "true") {
                val msg = result["content"]?.jsonArray?.firstOrNull()?.jsonObject?.get("text")?.jsonPrimitive?.contentOrNull
                throw IllegalStateException("Schoolhelp: ${msg ?: "tool error"}")
            }
            val text = result["content"]?.jsonArray
                ?.firstOrNull { it.jsonObject["type"]?.jsonPrimitive?.contentOrNull == "text" }
                ?.jsonObject?.get("text")?.jsonPrimitive?.contentOrNull
                ?: return JsonObject(emptyMap())
            val el: JsonElement = Json.parseToJsonElement(text)
            return el.jsonObject
        }
    }
}
