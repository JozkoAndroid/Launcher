package de.mm20.launcher2.schoolbrief.live

import android.content.Context
import android.security.keystore.KeyGenParameterSpec
import android.security.keystore.KeyProperties
import android.util.Base64
import java.security.KeyStore
import javax.crypto.Cipher
import javax.crypto.KeyGenerator
import javax.crypto.SecretKey
import javax.crypto.spec.GCMParameterSpec

/**
 * Secrets (Gemini key, Schoolhelp refresh token and client secret) encrypted with an
 * Android Keystore AES key that never leaves the device. The prefs file is excluded from
 * backups (see app/app res/xml backup rules); a restored copy could not be decrypted anyway.
 */
internal class SecretStore(context: Context) {
    private val prefs = context.getSharedPreferences("schoolbrief_secrets", Context.MODE_PRIVATE)

    private fun key(): SecretKey {
        val ks = KeyStore.getInstance("AndroidKeyStore").apply { load(null) }
        (ks.getEntry(Alias, null) as? KeyStore.SecretKeyEntry)?.let { return it.secretKey }
        val gen = KeyGenerator.getInstance(KeyProperties.KEY_ALGORITHM_AES, "AndroidKeyStore")
        gen.init(
            KeyGenParameterSpec.Builder(Alias, KeyProperties.PURPOSE_ENCRYPT or KeyProperties.PURPOSE_DECRYPT)
                .setBlockModes(KeyProperties.BLOCK_MODE_GCM)
                .setEncryptionPaddings(KeyProperties.ENCRYPTION_PADDING_NONE)
                .build()
        )
        return gen.generateKey()
    }

    fun put(name: String, value: String?) {
        if (value == null) {
            prefs.edit().remove(name).apply(); return
        }
        val c = Cipher.getInstance("AES/GCM/NoPadding").apply { init(Cipher.ENCRYPT_MODE, key()) }
        val enc = c.doFinal(value.toByteArray(Charsets.UTF_8))
        val packed = Base64.encodeToString(c.iv, Base64.NO_WRAP) + ":" + Base64.encodeToString(enc, Base64.NO_WRAP)
        prefs.edit().putString(name, packed).apply()
    }

    fun get(name: String): String? {
        val packed = prefs.getString(name, null) ?: return null
        return runCatching {
            val (iv, enc) = packed.split(':', limit = 2).map { Base64.decode(it, Base64.NO_WRAP) }
            val c = Cipher.getInstance("AES/GCM/NoPadding").apply { init(Cipher.DECRYPT_MODE, key(), GCMParameterSpec(128, iv)) }
            c.doFinal(enc).toString(Charsets.UTF_8)
        }.getOrNull()
    }

    companion object {
        private const val Alias = "schoolbrief_secrets_v1"
        const val GeminiKey = "gemini_key"
        const val ClientId = "sh_client_id"
        const val ClientSecret = "sh_client_secret"
        const val RefreshToken = "sh_refresh_token"
        const val RedirectUri = "sh_redirect_uri"
        const val PendingVerifier = "sh_pending_verifier"
        const val PendingState = "sh_pending_state"
    }
}
