package de.mm20.launcher2.schoolbrief.live

import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.buildJsonObject
import kotlinx.serialization.json.contentOrNull
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive
import kotlinx.serialization.json.longOrNull
import kotlinx.serialization.json.put
import kotlinx.serialization.json.putJsonArray
import java.net.URLDecoder
import java.net.URLEncoder
import java.security.MessageDigest
import java.security.SecureRandom
import android.util.Base64

/**
 * OAuth 2.1 for the Schoolhelp MCP server: dynamic client registration, PKCE, an https bounce page
 * as redirect (Schoolhelp accepts only plain https redirects), refresh tokens. The 30-minute access tokens renew silently from the refresh token.
 */
internal class SchoolhelpAuth(private val secrets: SecretStore) {

    private var accessToken: String? = null
    private var expiresAt: Long = 0

    val isLoggedIn: Boolean get() = secrets.get(SecretStore.RefreshToken) != null

    /**
     * Step 1: register this app once (client reused afterwards) and build the login URL.
     * The pending PKCE secret and state are kept encrypted, so the login survives the launcher
     * process being killed while the browser is open.
     */
    suspend fun begin(): String = withContext(Dispatchers.IO) {
        var clientId = secrets.get(SecretStore.ClientId)
        var clientSecret = secrets.get(SecretStore.ClientSecret)
        if (clientId == null || secrets.get(SecretStore.RedirectUri) != RedirectUri) {
            val reg = Http.request(
                "$Base/register", "POST",
                body = Json.encodeToString(JsonObject.serializer(), buildJsonObject {
                    put("client_name", "Launcher Brief")
                    putJsonArray("redirect_uris") { add(kotlinx.serialization.json.JsonPrimitive(RedirectUri)) }
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
            if (!reg.ok) throw HttpException(reg.code, "Schoolhelp odmietol registráciu aplikácie (${reg.code}): ${reg.body.take(200)}")
            val r = Json.parseToJsonElement(reg.body).jsonObject
            clientId = r["client_id"]!!.jsonPrimitive.content
            clientSecret = r["client_secret"]?.jsonPrimitive?.contentOrNull
            secrets.put(SecretStore.ClientId, clientId)
            secrets.put(SecretStore.ClientSecret, clientSecret)
            secrets.put(SecretStore.RedirectUri, RedirectUri)
        }
        val verifier = random(48)
        val challenge = Base64.encodeToString(
            MessageDigest.getInstance("SHA-256").digest(verifier.toByteArray()),
            Base64.URL_SAFE or Base64.NO_PADDING or Base64.NO_WRAP,
        )
        val state = random(16)
        secrets.put(SecretStore.PendingVerifier, verifier)
        secrets.put(SecretStore.PendingState, state)
        "$Base/authorize?" + listOf(
            "response_type" to "code",
            "client_id" to clientId,
            "redirect_uri" to RedirectUri,
            "code_challenge" to challenge,
            "code_challenge_method" to "S256",
            "state" to state,
            "scope" to Scope,
            "resource" to Resource,
        ).joinToString("&") { (k, v) -> k + "=" + URLEncoder.encode(v, "UTF-8") }
    }

    /** Step 2: [callbackUri] = launcherbrief://oauth?code=…&state=… from the bounce page. */
    suspend fun complete(callbackUri: String) = withContext(Dispatchers.IO) {
        val params = parseQuery(callbackUri)
        params["error"]?.let { throw IllegalStateException("Prihlásenie zrušené ($it).") }
        val expected = secrets.get(SecretStore.PendingState)
        val verifier = secrets.get(SecretStore.PendingVerifier)
        if (expected == null || verifier == null || params["state"] != expected) {
            throw IllegalStateException("Prihlásenie sa nepodarilo overiť. Skús to znova.")
        }
        val code = params["code"] ?: throw IllegalStateException("Schoolhelp nevrátil prihlasovací kód.")
        val tok = Http.request(
            "$Base/token", "POST",
            body = Http.form(
                "grant_type" to "authorization_code",
                "code" to code,
                "redirect_uri" to RedirectUri,
                "client_id" to (secrets.get(SecretStore.ClientId) ?: ""),
                "client_secret" to (secrets.get(SecretStore.ClientSecret) ?: ""),
                "code_verifier" to verifier,
                "resource" to Resource,
            ),
            headers = mapOf("Content-Type" to "application/x-www-form-urlencoded", "Accept" to "application/json"),
        )
        secrets.put(SecretStore.PendingState, null)
        secrets.put(SecretStore.PendingVerifier, null)
        if (!tok.ok) throw HttpException(tok.code, "Prihlásenie zlyhalo (${tok.code}): ${tok.body.take(200)}")
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

    private fun parseQuery(uri: String): Map<String, String> =
        uri.substringAfter('?', "").substringBefore('#').split('&').filter { '=' in it }.associate {
            val (k, v) = it.split('=', limit = 2)
            URLDecoder.decode(k, "UTF-8") to URLDecoder.decode(v, "UTF-8")
        }

    private fun random(bytes: Int): String {
        val b = ByteArray(bytes).also { SecureRandom().nextBytes(it) }
        return Base64.encodeToString(b, Base64.URL_SAFE or Base64.NO_PADDING or Base64.NO_WRAP)
    }

    companion object {
        const val Base = "https://lessons.labeltranslator.com"
        const val Resource = "$Base/mcp"
        const val Scope = "schoolhelp"
        /** Static bounce page on GitHub Pages (docs/oauth in the Launcher repo): passes the code to launcherbrief://oauth. */
        const val RedirectUri = "https://jozkoandroid.github.io/Launcher/oauth/"
    }
}
