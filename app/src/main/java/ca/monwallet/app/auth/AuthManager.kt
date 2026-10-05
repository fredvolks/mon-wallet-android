package ca.monwallet.app.auth

import android.net.Uri
import android.content.Context
import android.util.Base64
import android.util.Log
import ca.monwallet.app.BuildConfig
import ca.monwallet.app.data.*
import ca.monwallet.app.marketdata.*
import androidx.credentials.CredentialManager
import androidx.credentials.ClearCredentialStateRequest
import androidx.credentials.CustomCredential
import androidx.credentials.GetCredentialRequest
import com.google.android.libraries.identity.googleid.GetSignInWithGoogleOption
import com.google.android.libraries.identity.googleid.GoogleIdTokenCredential
import java.security.MessageDigest
import java.security.SecureRandom
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import okhttp3.*
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.RequestBody.Companion.toRequestBody
import org.json.JSONObject

class AuthManager(val secure: SecureSettings, private val repo: Repository) {
    val email = MutableStateFlow(secure.get("email"))
    val user = MutableStateFlow(secure.get("user"))
    private val mutex = Mutex()
    private val media = "application/json".toMediaType()
    private fun log(step: String) { if (BuildConfig.DEBUG) Log.d("MonWalletAuth", step) }

    private fun random(): String =
        ByteArray(32)
            .also { SecureRandom().nextBytes(it) }
            .let {
                Base64.encodeToString(it, Base64.URL_SAFE or Base64.NO_PADDING or Base64.NO_WRAP)
            }

    suspend fun initialize() {
        user.value?.let { repo.switch(it) }
    }

    suspend fun request(path: String, body: JSONObject, token: String? = null): JSONObject {
        check(secure.configured) { "Connexion temporairement indisponible." }
        return Http.json(
            Request.Builder()
                .url(secure.url + path)
                .header("apikey", secure.apiKey)
                .apply { token?.let { header("Authorization", "Bearer $it") } }
                .post(body.toString().toRequestBody(media))
                .build()
        )
    }

    private suspend fun accept(j: JSONObject, migrate: Boolean) {
        log("OAuth session received, migrate=$migrate")
        val uid = j.getJSONObject("user").getString("id")
        secure.put("access", j.getString("access_token"))
        secure.put("refresh", j.getString("refresh_token"))
        secure.put(
            "expiry",
            (System.currentTimeMillis() + j.optLong("expires_in", 3600) * 1000).toString(),
        )
        secure.put("email", j.getJSONObject("user").text("email"))
        secure.put("user", uid)
        user.value = uid
        email.value = secure.get("email")
        if (migrate) repo.migrateGuest(uid) else repo.switch(uid)
    }

    suspend fun token(): String =
        mutex.withLock {
            check(user.value != null) { "Connexion requise." }
            if ((secure.get("expiry")?.toLongOrNull() ?: 0) < System.currentTimeMillis() + 60000) {
                val j =
                    request(
                        "/auth/v1/token?grant_type=refresh_token",
                        JSONObject().put("refresh_token", secure.get("refresh")),
                    )
                accept(j, false)
            }
            secure.get("access") ?: error("Session expirée.")
        }

    fun oauth(provider: String, migrate: Boolean): Uri {
        check(secure.configured) { "Connexion temporairement indisponible." }
        log("OAuth start provider=$provider")
        val verifier = random()
        val state = random()
        secure.put("pkce", verifier)
        secure.put("oauth_state", state)
        secure.put("oauth_migrate", migrate.toString())
        val challenge =
            Base64.encodeToString(
                MessageDigest.getInstance("SHA-256").digest(verifier.toByteArray()),
                Base64.URL_SAFE or Base64.NO_PADDING or Base64.NO_WRAP,
            )
        return Uri.parse(secure.url + "/auth/v1/authorize")
            .buildUpon()
            .appendQueryParameter("provider", provider)
            .appendQueryParameter("redirect_to", "monwallet://auth${BuildConfig.AUTH_CALLBACK_PATH}?state=$state")
            .appendQueryParameter("code_challenge", challenge)
            .appendQueryParameter("code_challenge_method", "s256")
            .apply { if (provider == "azure") appendQueryParameter("scopes", "email") }
            .build()
    }

    /** Native Google account picker; Supabase verifies the token and issues its own session. */
    suspend fun google(context: Context, migrate: Boolean) {
        check(secure.configured && BuildConfig.GOOGLE_WEB_CLIENT_ID.isNotBlank()) {
            "Connexion Google temporairement indisponible."
        }
        val nonce = random()
        val hashedNonce = MessageDigest.getInstance("SHA-256").digest(nonce.toByteArray())
            .joinToString("") { "%02x".format(it) }
        val option = GetSignInWithGoogleOption.Builder(BuildConfig.GOOGLE_WEB_CLIENT_ID)
            .setNonce(hashedNonce).build()
        val credential = CredentialManager.create(context).getCredential(
            context, GetCredentialRequest.Builder().addCredentialOption(option).build()
        ).credential
        require(credential is CustomCredential && credential.type == GoogleIdTokenCredential.TYPE_GOOGLE_ID_TOKEN_CREDENTIAL) {
            "Compte Google non reconnu."
        }
        val idToken = GoogleIdTokenCredential.createFrom(credential.data).idToken
        log("Google credential selected; exchanging with Supabase")
        accept(request("/auth/v1/token?grant_type=id_token",
            JSONObject().put("provider", "google").put("id_token", idToken).put("nonce", nonce)), migrate)
    }

    suspend fun callback(uri: Uri) {
        log("OAuth callback received")
        require(uri.scheme == "monwallet" && uri.host == "auth" && uri.path == BuildConfig.AUTH_CALLBACK_PATH)
        require(uri.getQueryParameter("state") == secure.get("oauth_state")) {
            "Retour de connexion non reconnu."
        }
        val code = uri.getQueryParameter("code") ?: error("Connexion annulée ou refusée.")
        accept(
            request(
                "/auth/v1/token?grant_type=pkce",
                JSONObject().put("auth_code", code).put("code_verifier", secure.get("pkce")),
            ),
            secure.get("oauth_migrate") == "true",
        )
        secure.put("pkce", null)
        secure.put("oauth_state", null)
        log("OAuth callback session stored")
    }

    suspend fun sendCode(address: String) {
        require(android.util.Patterns.EMAIL_ADDRESS.matcher(address).matches()) {
            "Courriel invalide."
        }
        request("/auth/v1/otp", JSONObject().put("email", address).put("create_user", true))
    }

    suspend fun verify(address: String, code: String, migrate: Boolean) {
        require(code.length in 6..8 && code.all { it.isDigit() }) { "Code invalide." }
        accept(
            request(
                "/auth/v1/verify",
                JSONObject().put("email", address).put("token", code).put("type", "email"),
            ),
            migrate,
        )
    }

    suspend fun signOut(context: Context) {
        val access = runCatching { token() }.getOrNull()
        if (access != null)
            runCatching {
                Http.raw(
                    Request.Builder()
                        .url(secure.url + "/auth/v1/logout?scope=local")
                        .header("apikey", secure.apiKey)
                        .header("Authorization", "Bearer $access")
                        .post("{}".toRequestBody(media))
                        .build()
                )
            }
        clearSession()
        runCatching { CredentialManager.create(context).clearCredentialState(ClearCredentialStateRequest()) }
        log("local session cleared")
    }

    suspend fun clearSession() {
        listOf("access", "refresh", "expiry", "email", "user", "pkce", "oauth_state").forEach {
            secure.put(it, null)
        }
        email.value = null
        user.value = null
        repo.switch("guest")
    }

    suspend fun deleteAccount() {
        val uid = user.value ?: error("Connexion requise.")
        request("/functions/v1/delete-account", JSONObject(), token())
        repo.dao.clear(uid)
        clearSession()
    }
}
