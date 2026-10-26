package ch.alpenlan.kalender.home

import android.content.Context
import android.content.Intent
import android.net.Uri
import android.security.keystore.KeyGenParameterSpec
import android.security.keystore.KeyProperties
import android.util.Base64
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.withContext
import org.json.JSONObject
import java.net.HttpURLConnection
import java.net.URL
import java.net.URLEncoder
import java.security.KeyStore
import java.security.MessageDigest
import java.security.SecureRandom
import javax.crypto.Cipher
import javax.crypto.KeyGenerator
import javax.crypto.SecretKey
import javax.crypto.spec.GCMParameterSpec

/** Verschlüsselt Tokens mit einem Schlüssel im Android-Keystore (verlässt das Gerät nie). */
private object Vault {
    private const val ALIAS = "alpenlan_tokens"

    private fun key(): SecretKey {
        val ks = KeyStore.getInstance("AndroidKeyStore").apply { load(null) }
        (ks.getKey(ALIAS, null) as? SecretKey)?.let { return it }
        val gen = KeyGenerator.getInstance(KeyProperties.KEY_ALGORITHM_AES, "AndroidKeyStore")
        gen.init(
            KeyGenParameterSpec.Builder(ALIAS, KeyProperties.PURPOSE_ENCRYPT or KeyProperties.PURPOSE_DECRYPT)
                .setBlockModes(KeyProperties.BLOCK_MODE_GCM)
                .setEncryptionPaddings(KeyProperties.ENCRYPTION_PADDING_NONE)
                .build()
        )
        return gen.generateKey()
    }

    fun seal(plain: String): String {
        val c = Cipher.getInstance("AES/GCM/NoPadding")
        c.init(Cipher.ENCRYPT_MODE, key())
        val out = c.iv + c.doFinal(plain.toByteArray())
        return Base64.encodeToString(out, Base64.NO_WRAP)
    }

    fun open(sealed: String?): String? = runCatching {
        if (sealed.isNullOrBlank()) return null
        val all = Base64.decode(sealed, Base64.NO_WRAP)
        val c = Cipher.getInstance("AES/GCM/NoPadding")
        c.init(Cipher.DECRYPT_MODE, key(), GCMParameterSpec(128, all.copyOfRange(0, 12)))
        String(c.doFinal(all.copyOfRange(12, all.size)))
    }.getOrNull()
}

class AuthError(msg: String) : Exception(msg)

/**
 * Login über Authentik (OAuth2 Authorization Code + PKCE, öffentlicher Client).
 * Das Access-Token wird an control.alpenlan.com mitgeschickt; der Authentik-Proxy
 * akzeptiert es dank "Federated OIDC Providers".
 */
class Auth(ctx: Context) {
    private val prefs = ctx.getSharedPreferences("alpenlan_auth", Context.MODE_PRIVATE)
    private val mutex = Mutex()

    val loggedIn: Boolean get() = prefs.contains("refresh") || prefs.contains("access")
    val userName: String? get() = prefs.getString("user", null)

    private fun rnd(bytes: Int): String {
        val b = ByteArray(bytes).also { SecureRandom().nextBytes(it) }
        return Base64.encodeToString(b, Base64.URL_SAFE or Base64.NO_WRAP or Base64.NO_PADDING)
    }

    /** Öffnet den Authentik-Login im Browser. */
    fun loginIntent(): Intent {
        val verifier = rnd(48)
        val state = rnd(16)
        val challenge = Base64.encodeToString(
            MessageDigest.getInstance("SHA-256").digest(verifier.toByteArray()),
            Base64.URL_SAFE or Base64.NO_WRAP or Base64.NO_PADDING,
        )
        prefs.edit().putString("pkce", verifier).putString("state", state).apply()
        val uri = Uri.parse(AUTHORIZE).buildUpon()
            .appendQueryParameter("response_type", "code")
            .appendQueryParameter("client_id", CLIENT_ID)
            .appendQueryParameter("redirect_uri", REDIRECT)
            .appendQueryParameter("scope", "openid profile email offline_access")
            .appendQueryParameter("state", state)
            .appendQueryParameter("code_challenge", challenge)
            .appendQueryParameter("code_challenge_method", "S256")
            .build()
        return Intent(Intent.ACTION_VIEW, uri).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
    }

    /** Antwort vom Browser verarbeiten (ch.alpenlan.app:/oauth2redirect?code=…). */
    suspend fun handleRedirect(uri: Uri) {
        uri.getQueryParameter("error")?.let { throw AuthError(uri.getQueryParameter("error_description") ?: it) }
        val code = uri.getQueryParameter("code") ?: throw AuthError("Keine Antwort von Authentik")
        if (uri.getQueryParameter("state") != prefs.getString("state", null)) throw AuthError("Anmeldung abgelaufen – bitte nochmals versuchen")
        val verifier = prefs.getString("pkce", null) ?: throw AuthError("Anmeldung abgelaufen – bitte nochmals versuchen")
        val res = tokenRequest(
            mapOf(
                "grant_type" to "authorization_code", "code" to code, "redirect_uri" to REDIRECT,
                "client_id" to CLIENT_ID, "code_verifier" to verifier,
            )
        )
        save(res)
        prefs.edit().remove("pkce").remove("state").apply()
    }

    /** Gültiges Access-Token, bei Bedarf mit dem Refresh-Token erneuert. */
    suspend fun accessToken(forceRefresh: Boolean = false): String = mutex.withLock {
        val access = Vault.open(prefs.getString("access", null))
        val exp = prefs.getLong("exp", 0)
        if (!forceRefresh && access != null && System.currentTimeMillis() < exp - 60_000) return access
        val refresh = Vault.open(prefs.getString("refresh", null)) ?: throw AuthError("Bitte anmelden")
        val res = try {
            tokenRequest(mapOf("grant_type" to "refresh_token", "refresh_token" to refresh, "client_id" to CLIENT_ID))
        } catch (e: AuthError) {
            logout(); throw AuthError("Sitzung abgelaufen – bitte neu anmelden")
        }
        save(res)
        return Vault.open(prefs.getString("access", null)) ?: throw AuthError("Bitte anmelden")
    }

    fun logout() {
        prefs.edit().clear().apply()
    }

    private fun save(res: JSONObject) {
        val e = prefs.edit()
        res.optString("access_token").takeIf { it.isNotBlank() }?.let { e.putString("access", Vault.seal(it)) }
        res.optString("refresh_token").takeIf { it.isNotBlank() }?.let { e.putString("refresh", Vault.seal(it)) }
        e.putLong("exp", System.currentTimeMillis() + res.optLong("expires_in", 3600) * 1000)
        res.optString("id_token").takeIf { it.isNotBlank() }?.let { idt ->
            runCatching {
                val payload = JSONObject(String(Base64.decode(idt.split(".")[1], Base64.URL_SAFE or Base64.NO_WRAP or Base64.NO_PADDING)))
                val name = payload.optString("name").ifBlank { payload.optString("preferred_username") }
                if (name.isNotBlank()) e.putString("user", name)
            }
        }
        e.apply()
    }

    private suspend fun tokenRequest(form: Map<String, String>): JSONObject = withContext(Dispatchers.IO) {
        val body = form.entries.joinToString("&") { "${it.key}=${URLEncoder.encode(it.value, "UTF-8")}" }
        val c = (URL(TOKEN).openConnection() as HttpURLConnection).apply {
            requestMethod = "POST"
            doOutput = true
            connectTimeout = 10_000
            readTimeout = 15_000
            setRequestProperty("Content-Type", "application/x-www-form-urlencoded")
            setRequestProperty("Accept", "application/json")
        }
        try {
            c.outputStream.use { it.write(body.toByteArray()) }
            val code = c.responseCode
            val text = (if (code in 200..299) c.inputStream else c.errorStream)?.bufferedReader()?.use { it.readText() } ?: ""
            val json = runCatching { JSONObject(text) }.getOrNull()
            if (code !in 200..299 || json == null) {
                throw AuthError(json?.optString("error_description")?.ifBlank { null } ?: json?.optString("error")?.ifBlank { null } ?: "Authentik antwortet nicht ($code)")
            }
            json
        } finally {
            c.disconnect()
        }
    }

    companion object {
        const val CLIENT_ID = "alpenlan-app"
        const val REDIRECT = "ch.alpenlan.app:/oauth2redirect"
        const val AUTHORIZE = "https://auth.alpenlan.com/application/o/authorize/"
        const val TOKEN = "https://auth.alpenlan.com/application/o/token/"
    }
}
