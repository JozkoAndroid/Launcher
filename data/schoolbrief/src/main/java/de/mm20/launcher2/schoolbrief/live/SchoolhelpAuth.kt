package de.mm20.launcher2.schoolbrief.live

import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import kotlinx.coroutines.withTimeout
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.buildJsonObject
import kotlinx.serialization.json.contentOrNull
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive
import kotlinx.serialization.json.longOrNull
import kotlinx.serialization.json.put
import kotlinx.serialization.json.putJsonArray
import java.net.InetAddress
import java.net.ServerSocket
import java.net.URLDecoder
import java.net.URLEncoder
import java.security.MessageDigest
import java.security.SecureRandom
import android.util.Base64

/**
 * OAuth 2.1 for the Schoolhelp MCP server: dynamic client registration, PKCE, loopback redirect
 * (RFC 8252), refresh tokens. The 30-minute access tokens renew silently from the refresh token.
 */
internal class SchoolhelpAuth(private val secrets: SecretStore) {

    private var accessToken: String? = null
    private var expiresAt: Long = 0

    val isLoggedIn: Boolean get() = secrets.get(SecretStore.RefreshToken) != null

    class LoginRequest internal constructor(
        val authorizeUrl: String,
        internal val server: ServerSocket,
        internal val verifier: String,
        internal val state: String,
        internal val redirectUri: String,
        internal val clientId: String,
        internal val clientSecret: String?,
    )

    /** Step 1: register this app and open a loopback listener. Open [LoginRequest.authorizeUrl] in the browser. */
    suspend fun begin(): LoginRequest = withContext(Dispatchers.IO) {
        val server = ServerSocket(0, 1, InetAddress.getByName("127.0.0.1"))
        val redirect = "http://127.0.0.1:${server.localPort}/callback"
        val reg = Http.request(
            "$Base/register", "POST",
            body = Json.encodeToString(JsonObject.serializer(), buildJsonObject {
                put("client_name", "Launcher Brief")
                putJsonArray("redirect_uris") { add(kotlinx.serialization.json.JsonPrimitive(redirect)) }
                putJsonArray("grant_types") {
                    add(kotlinx.serialization.json.JsonPrimitive("authorization_code"))
                    add(kotlinx.serialization.json.JsonPrimitive("refresh_token"))
                }
                putJsonArray("response_types") { add(kotlinx.serialization.json.JsonPrimitive("code")) }
                put("token_endpoint_auth_method", "client_secret_post")
                put("scope", Scope)
            }),
            headers = mapOf("Content-Type" to "application/json", "Accept" to "application/json"),
        )
        if (!reg.ok) {
            server.close()
            throw HttpException(reg.code, "Schoolhelp odmietol registráciu aplikácie (${reg.code}): ${reg.body.take(200)}")
        }
        val r = Json.parseToJsonElement(reg.body).jsonObject
        val clientId = r["client_id"]!!.jsonPrimitive.content
        val clientSecret = r["client_secret"]?.jsonPrimitive?.contentOrNull
        val verifier = random(48)
        val challenge = Base64.encodeToString(
            MessageDigest.getInstance("SHA-256").digest(verifier.toByteArray()),
            Base64.URL_SAFE or Base64.NO_PADDING or Base64.NO_WRAP,
        )
        val state = random(16)
        val url = "$Base/authorize?" + listOf(
            "response_type" to "code",
            "client_id" to clientId,
            "redirect_uri" to redirect,
            "code_challenge" to challenge,
            "code_challenge_method" to "S256",
            "state" to state,
            "scope" to Scope,
            "resource" to Resource,
        ).joinToString("&") { (k, v) -> k + "=" + URLEncoder.encode(v, "UTF-8") }
        LoginRequest(url, server, verifier, state, redirect, clientId, clientSecret)
    }

    /** Step 2: wait for the browser to come back (max 5 minutes), then exchange the code. */
    suspend fun complete(req: LoginRequest) = withContext(Dispatchers.IO) {
        val params = try {
            withTimeout(5 * 60_000L) { awaitCallback(req.server) }
        } finally {
            req.server.close()
        }
        params["error"]?.let { throw IllegalStateException("Prihlásenie zrušené ($it).") }
        if (params["state"] != req.state) throw IllegalStateException("Prihlásenie sa nepodarilo overiť. Skús to znova.")
        val code = params["code"] ?: throw IllegalStateException("Schoolhelp nevrátil prihlasovací kód.")
        val tok = Http.request(
            "$Base/token", "POST",
            body = Http.form(
                "grant_type" to "authorization_code",
                "code" to code,
                "redirect_uri" to req.redirectUri,
                "client_id" to req.clientId,
                "client_secret" to (req.clientSecret ?: ""),
                "code_verifier" to req.verifier,
                "resource" to Resource,
            ),
            headers = mapOf("Content-Type" to "application/x-www-form-urlencoded", "Accept" to "application/json"),
        )
        if (!tok.ok) throw HttpException(tok.code, "Prihlásenie zlyhalo (${tok.code}).")
        secrets.put(SecretStore.ClientId, req.clientId)
        secrets.put(SecretStore.ClientSecret, req.clientSecret)
        secrets.put(SecretStore.RedirectUri, req.redirectUri)
        store(Json.parseToJsonElement(tok.body).jsonObject)
    }

    /** A valid access token, refreshed when it is about to expire. Null = logged out. */
    suspend fun token(): String? {
        accessToken?.let { if (System.currentTimeMillis() < expiresAt - 60_000) return it }
        val refresh = secrets.get(SecretStore.RefreshToken) ?: return null
        val res = Http.request(
            "$Base/token", "POST",
            body = Http.form(
                "grant_type" to "refresh_token",
                "refresh_token" to refresh,
                "client_id" to (secrets.get(SecretStore.ClientId) ?: ""),
                "client_secret" to (secrets.get(SecretStore.ClientSecret) ?: ""),
                "resource" to Resource,
            ),
            headers = mapOf("Content-Type" to "application/x-www-form-urlencoded", "Accept" to "application/json"),
        )
        if (res.code == 400 || res.code == 401) {
            logout(); return null // refresh token expired or revoked: ask the user to log in again
        }
        if (!res.ok) throw HttpException(res.code, "Schoolhelp token refresh failed (${res.code})")
        store(Json.parseToJsonElement(res.body).jsonObject)
        return accessToken
    }

    fun invalidateAccessToken() {
        accessToken = null
    }

    fun logout() {
        accessToken = null
        secrets.put(SecretStore.RefreshToken, null)
    }

    private fun store(t: JsonObject) {
        accessToken = t["access_token"]?.jsonPrimitive?.content
        val ttl = t["expires_in"]?.jsonPrimitive?.longOrNull ?: 1800
        expiresAt = System.currentTimeMillis() + ttl * 1000
        // Servers may rotate the refresh token; keep the newest.
        t["refresh_token"]?.jsonPrimitive?.contentOrNull?.let { secrets.put(SecretStore.RefreshToken, it) }
    }

    private fun awaitCallback(server: ServerSocket): Map<String, String> {
        while (true) {
            server.accept().use { s ->
                val line = s.getInputStream().bufferedReader().readLine().orEmpty() // GET /callback?code=..&state=.. HTTP/1.1
                val path = line.split(' ').getOrNull(1).orEmpty()
                if (!path.startsWith("/callback")) {
                    s.getOutputStream().write("HTTP/1.1 404 Not Found\r\nContent-Length: 0\r\n\r\n".toByteArray())
                    return@use
                }
                val html = "<!doctype html><meta charset=utf-8><meta name=viewport content='width=device-width'>" +
                    "<body style='font:16px sans-serif;padding:24px'>Hotovo. Vráť sa na domovskú obrazovku.</body>"
                val bytes = html.toByteArray(Charsets.UTF_8)
                s.getOutputStream().write(
                    ("HTTP/1.1 200 OK\r\nContent-Type: text/html; charset=utf-8\r\nContent-Length: ${bytes.size}\r\nConnection: close\r\n\r\n").toByteArray() + bytes
                )
                return path.substringAfter('?', "").split('&').filter { '=' in it }.associate {
                    val (k, v) = it.split('=', limit = 2)
                    URLDecoder.decode(k, "UTF-8") to URLDecoder.decode(v, "UTF-8")
                }
            }
        }
    }

    private fun random(bytes: Int): String {
        val b = ByteArray(bytes).also { SecureRandom().nextBytes(it) }
        return Base64.encodeToString(b, Base64.URL_SAFE or Base64.NO_PADDING or Base64.NO_WRAP)
    }

    companion object {
        const val Base = "https://lessons.labeltranslator.com"
        const val Resource = "$Base/mcp"
        const val Scope = "schoolhelp"
    }
}
