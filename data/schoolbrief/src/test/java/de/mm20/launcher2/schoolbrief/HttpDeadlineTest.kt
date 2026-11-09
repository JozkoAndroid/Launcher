package de.mm20.launcher2.schoolbrief

import com.sun.net.httpserver.HttpServer
import de.mm20.launcher2.schoolbrief.live.Http
import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertTrue
import org.junit.Test
import java.net.InetSocketAddress

/**
 * Round 26 (device test: after a Schoolhelp outage nothing synced, "try again" did nothing): a streamed
 * answer that keeps the connection open must not block, and no request may outlive its deadline.
 */
class HttpDeadlineTest {
    private fun server(handler: (com.sun.net.httpserver.HttpExchange) -> Unit): HttpServer =
        HttpServer.create(InetSocketAddress("127.0.0.1", 0), 0).apply { createContext("/") { handler(it) }; start() }

    @Test
    fun streamStopsAtTheAnswerEvenWhenKeptOpen() = runBlocking {
        val s = server { ex ->
            ex.responseHeaders.add("Content-Type", "text/event-stream")
            ex.sendResponseHeaders(200, 0)
            val out = ex.responseBody
            out.write("event: message\ndata: {\"jsonrpc\":\"2.0\",\"id\":7,\"result\":{\"ok\":true}}\n\n".toByteArray()); out.flush()
            // Keep-alive pings for a long time, never closing.
            repeat(60) { runCatching { out.write(": ping\n\n".toByteArray()); out.flush() }; Thread.sleep(500) }
            runCatching { out.close() }
        }
        val t = System.currentTimeMillis()
        val r = Http.request("http://127.0.0.1:${s.address.port}/", "POST", "{}", timeoutMs = 20_000, stopWhen = { it.contains("\"id\":7") })
        val took = System.currentTimeMillis() - t
        s.stop(0)
        assertTrue("took $took ms", took < 5_000)
        assertTrue(r.body.contains("\"ok\":true"))
    }

    @Test
    fun noAnswerEndsAtTheDeadline() = runBlocking {
        val s = server { ex ->
            ex.responseHeaders.add("Content-Type", "text/event-stream")
            ex.sendResponseHeaders(200, 0)
            val out = ex.responseBody
            // Pings that would reset a plain read timeout, but never an answer.
            repeat(40) { runCatching { out.write(": ping\n\n".toByteArray()); out.flush() }; Thread.sleep(250) }
            runCatching { out.close() }
        }
        val t = System.currentTimeMillis()
        val failed = runCatching {
            Http.request("http://127.0.0.1:${s.address.port}/", "POST", "{}", timeoutMs = 2_000, stopWhen = { false })
        }.isFailure
        val took = System.currentTimeMillis() - t
        s.stop(0)
        assertTrue("failed=$failed took=$took", failed && took < 6_000)
    }
}
