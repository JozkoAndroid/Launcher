package de.mm20.launcher2.schoolbrief

import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.jsonArray
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test
import java.time.LocalDateTime

/** Round 31: the Claude chat's request, Schoolhelp tool results, errors and spend. */
class ClaudeChatTest {
    private fun obj(s: String) = Json.parseToJsonElement(s).jsonObject

    @Test
    fun conversationAlternatesAndStartsWithUser() {
        val turns = ClaudeSchoolChat.conversation(
            listOf(
                ChatMessage(false, "stará odpoveď"),
                ChatMessage(true, "Opakovací list", modelText = "Celý prompt"),
                ChatMessage(true, "ešte"),
                ChatMessage(false, "odpoveď"),
            )
        )
        assertEquals(listOf("user", "assistant"), turns.map { it["role"]!!.jsonPrimitive.content })
        val text = turns[0]["content"]!!.jsonArray[0].jsonObject["text"]!!.jsonPrimitive.content
        assertEquals("Celý prompt\n\nešte", text)
    }

    @Test
    fun requestMapsToolsAndCachesTheLastBlock() {
        val tools = Json.parseToJsonElement(
            """[{"name":"app_call","description":"one action","inputSchema":{"type":"object","properties":{"name":{"type":"string"}}}}]"""
        ).jsonArray
        val system = ClaudeSchoolChat.system(SampleFeed.create(), LocalDateTime.parse("2026-11-10T07:30"), schoolhelp = true)
        val body = ClaudeSchoolChat.request(system, tools, ClaudeSchoolChat.conversation(listOf(ChatMessage(true, "Čo mám zajtra?"))))
        assertEquals(ClaudeSchoolChat.Model, body["model"]!!.jsonPrimitive.content)
        assertFalse("2026-10-21 rejects sampling parameters", "temperature" in body)
        val tool = body["tools"]!!.jsonArray[0].jsonObject
        assertEquals("app_call", tool["name"]!!.jsonPrimitive.content)
        assertTrue("input_schema" in tool && "inputSchema" !in tool)
        assertTrue("cache_control" in body["system"]!!.jsonArray[0].jsonObject)
        assertFalse("today's data changes every minute", "cache_control" in body["system"]!!.jsonArray[1].jsonObject)
        val last = body["messages"]!!.jsonArray.last().jsonObject["content"]!!.jsonArray.last().jsonObject
        assertTrue("cache_control" in last)
    }

    @Test
    fun toolResultKeepsTextAndPicturesAndMarksErrors() {
        val r = ClaudeSchoolChat.toolResult(
            "toolu_1",
            obj("""{"content":[{"type":"text","text":"{\"ok\":true}"},{"type":"image","data":"iVBOR","mimeType":"image/png"}]}"""),
            null,
        )
        val blocks = r["content"]!!.jsonArray
        assertEquals("text", blocks[0].jsonObject["type"]!!.jsonPrimitive.content)
        assertEquals("base64", blocks[1].jsonObject["source"]!!.jsonObject["type"]!!.jsonPrimitive.content)
        assertFalse("is_error" in r)

        val failed = ClaudeSchoolChat.toolResult("toolu_2", null, "Nie si prihlásený do Schoolhelp.")
        assertEquals("true", failed["is_error"]!!.jsonPrimitive.content)

        val huge = ClaudeSchoolChat.toolResult("toolu_3", obj("""{"content":[{"type":"text","text":"${"x".repeat(50_000)}"}]}"""), null)
        assertTrue(huge["content"]!!.jsonArray[0].jsonObject["text"]!!.jsonPrimitive.content.length < 41_000)
    }

    @Test
    fun usedUpCreditsHandOverToGemini() {
        val e = ClaudeSchoolChat.failure(400, """{"type":"error","error":{"type":"invalid_request_error","message":"Your credit balance is too low to access the Anthropic API."}}""")
        assertTrue(e is ClaudeSchoolChat.Unavailable)
        assertTrue(ClaudeSchoolChat.failure(529, """{"type":"error","error":{"type":"overloaded_error","message":"Overloaded"}}""") is ClaudeSchoolChat.Unavailable)
        val bad = ClaudeSchoolChat.failure(401, """{"type":"error","error":{"type":"authentication_error","message":"invalid x-api-key"}}""")
        assertFalse(bad is ClaudeSchoolChat.Unavailable)
        assertTrue(bad.message!!.contains("neplatný"))
    }

    @Test
    fun spendInMicroDollars() {
        // 10k input ($0.02) + 1k output ($0.01) + 20k cache read ($0.004) + 4k cache write ($0.01)
        val u = obj("""{"input_tokens":10000,"output_tokens":1000,"cache_read_input_tokens":20000,"cache_creation_input_tokens":4000}""")
        assertEquals(44_000L, ClaudeSchoolChat.costMicros(u))
        assertEquals(0L, ClaudeSchoolChat.costMicros(JsonObject(emptyMap())))
    }

    @Test
    fun replyTextSkipsThinkingAndTools() {
        val content = Json.parseToJsonElement(
            """[{"type":"thinking","thinking":"","signature":"x"},{"type":"text","text":"Zajtra máš "},{"type":"text","text":"Bio."},{"type":"tool_use","id":"t","name":"app_call","input":{}}]"""
        ).jsonArray
        assertEquals("Zajtra máš Bio.", ClaudeSchoolChat.text(content))
    }
}
