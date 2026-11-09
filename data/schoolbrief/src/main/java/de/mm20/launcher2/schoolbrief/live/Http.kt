package de.mm20.launcher2.schoolbrief.live

import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.io.IOException
import java.net.HttpURLConnection
import java.net.URL

internal class HttpResponse(val code: Int, val body: String, val headers: Map<String, List<String>>) {
    fun header(name: String): String? = headers.entries.firstOrNull { it.key.equals(name, ignoreCase = true) }?.value?.firstOrNull()
    val ok get() = code in 200..299
}

internal class HttpException(val code: Int, message: String) : IOException(message)

/** Minimal blocking HTTP on the IO dispatcher; enough for a handful of requests per refresh. */
internal object Http {
    /**
     * One request with a hard deadline (round 26, device test: after a Schoolhelp outage nothing synced and
     * "try again" did nothing). [timeoutMs] bounds the whole request, not only the gap between two bytes:
     * a streamed answer (SSE) that kept the connection open with keep-alive pings used to block for ever,
     * holding the sync lock. [stopWhen] ends a stream as soon as one event carries the answer.
     */
    suspend fun request(
        url: String,
        method: String = "GET",
        body: String? = null,
        headers: Map<String, String> = emptyMap(),
        timeoutMs: Int = 20_000,
        stopWhen: ((String) -> Boolean)? = null,
    ): HttpResponse = kotlinx.coroutines.coroutineScope {
        val c = URL(url).openConnection() as HttpURLConnection
        // Cutting the connection is the only reliable way to stop a blocking read.
        val watchdog = launch(Dispatchers.IO) { kotlinx.coroutines.delay(timeoutMs.toLong()); c.disconnect() }
        try {
            withContext(Dispatchers.IO) {
                c.requestMethod = method
                c.connectTimeout = timeoutMs
                c.readTimeout = timeoutMs
                c.setRequestProperty("User-Agent", "LauncherBrief/1.0 (Android)")
                headers.forEach { (k, v) -> c.setRequestProperty(k, v) }
                if (body != null) {
                    c.doOutput = true
                    c.outputStream.use { it.write(body.toByteArray(Charsets.UTF_8)) }
                }
                val code = c.responseCode
                val stream = if (code in 200..399) c.inputStream else c.errorStream
                val sse = c.contentType?.contains("text/event-stream") == true
                val text = if (stream == null) "" else if (!sse || stopWhen == null) {
                    stream.use { it.readBytes().toString(Charsets.UTF_8) }
                } else {
                    // Read event by event; stop at the one that answers.
                    val out = StringBuilder()
                    val event = StringBuilder()
                    stream.bufferedReader(Charsets.UTF_8).use { r ->
                        while (true) {
                            val line = r.readLine() ?: break
                            out.append(line).append('\n')
                            if (line.startsWith("data:")) event.append(line.removePrefix("data:").trimStart()).append('\n')
                            if (line.isEmpty() && event.isNotEmpty()) {
                                if (stopWhen(event.toString())) break
                                event.clear()
                            }
                        }
                    }
                    out.toString()
                }
                HttpResponse(code, text, c.headerFields.filterKeys { it != null })
            }
        } catch (e: IOException) {
            if (!watchdog.isActive) throw IOException("Schoolhelp did not answer within ${timeoutMs / 1000} s", e)
            throw e
        } finally {
            watchdog.cancel()
            c.disconnect()
        }
    }

    fun form(vararg pairs: Pair<String, String>): String = pairs.joinToString("&") { (k, v) ->
        java.net.URLEncoder.encode(k, "UTF-8") + "=" + java.net.URLEncoder.encode(v, "UTF-8")
    }
}
