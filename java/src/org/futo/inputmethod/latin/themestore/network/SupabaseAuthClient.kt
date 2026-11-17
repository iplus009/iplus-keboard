package org.futo.inputmethod.latin.themestore.network

import android.content.Context
import android.content.Intent
import android.content.SharedPreferences
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.withContext
import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable
import kotlinx.serialization.encodeToString
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.contentOrNull
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.RequestBody.Companion.toRequestBody
import org.futo.inputmethod.latin.uix.isDirectBootUnlocked
import java.io.IOException
import java.net.URI
import java.net.URLDecoder
import java.net.URLEncoder
import java.security.MessageDigest
import java.security.SecureRandom
import java.util.concurrent.TimeUnit

@Serializable
data class SupabaseSession(
    @SerialName("user_id") val userId: String,
    val email: String,
    @SerialName("access_token") val accessToken: String,
    @SerialName("refresh_token") val refreshToken: String,
    @SerialName("expires_at_epoch_sec") val expiresAtEpochSec: Long
)

sealed class SignUpOutcome {
    data class SignedIn(val session: SupabaseSession) : SignUpOutcome()
    data class ConfirmationRequired(val email: String) : SignUpOutcome()
}

data class OAuthCallbackParams(
    val code: String? = null,
    val accessToken: String? = null,
    val refreshToken: String? = null,
    val expiresInSec: Long = 3600L,
    val errorDescription: String? = null
)

object SupabaseAuthClient {
    const val SUPABASE_URL = "https://yufwtqrhsmcrydumfrpp.supabase.co"
    const val SUPABASE_PUBLISHABLE_KEY = "sb_publishable_OTd885uJL8kAa49uy_3lWg_7sRg-qTv"
    const val REDIRECT_URI = "keybird://auth-callback"

    private const val PREFS_NAME = "keybird_supabase_auth"
    private const val KEY_SESSION_JSON = "session_json"
    private const val KEY_PKCE_VERIFIER = "pending_pkce_verifier"

    private val JSON_MEDIA = "application/json; charset=utf-8".toMediaType()
    private val json = Json { ignoreUnknownKeys = true; encodeDefaults = true }
    private val client = OkHttpClient.Builder()
        .connectTimeout(10, TimeUnit.SECONDS)
        .readTimeout(15, TimeUnit.SECONDS)
        .writeTimeout(15, TimeUnit.SECONDS)
        .build()

    private val _sessionFlow = MutableStateFlow<SupabaseSession?>(null)
    val sessionFlow: StateFlow<SupabaseSession?> = _sessionFlow.asStateFlow()

    @Volatile
    private var initialized = false

    fun currentSession(context: Context): SupabaseSession? {
        if (!context.isDirectBootUnlocked) return null
        if (!initialized) {
            synchronized(this) {
                if (!initialized) {
                    val saved = prefs(context)?.getString(KEY_SESSION_JSON, null)
                    _sessionFlow.value = saved?.let {
                        runCatching { json.decodeFromString<SupabaseSession>(it) }.getOrNull()
                    }
                    initialized = true
                }
            }
        }
        return _sessionFlow.value
    }

    fun signOut(context: Context) {
        prefs(context)?.edit()
            ?.remove(KEY_SESSION_JSON)
            ?.remove(KEY_PKCE_VERIFIER)
            ?.apply()
        _sessionFlow.value = null
        initialized = true
    }

    suspend fun signInWithEmail(
        context: Context,
        email: String,
        password: String
    ): Result<SupabaseSession> = withContext(Dispatchers.IO) {
        runCatching {
            val cleanEmail = email.trim().lowercase()
            require(cleanEmail.contains("@") && cleanEmail.contains(".")) { "Enter a valid email address" }
            require(password.length >= 6) { "Password must be at least 6 characters" }
            val body = json.encodeToString(mapOf("email" to cleanEmail, "password" to password))
            val obj = postAuth("/auth/v1/token?grant_type=password", body)
            val session = parseSessionFromJson(obj) ?: error("Login did not return a session")
            persistSession(context, session)
            session
        }
    }

    suspend fun signUpWithEmail(
        context: Context,
        email: String,
        password: String
    ): Result<SignUpOutcome> = withContext(Dispatchers.IO) {
        runCatching {
            val cleanEmail = email.trim().lowercase()
            require(cleanEmail.contains("@") && cleanEmail.contains(".")) { "Enter a valid email address" }
            require(password.length >= 6) { "Password must be at least 6 characters" }
            val body = json.encodeToString(mapOf("email" to cleanEmail, "password" to password))
            val obj = postAuth("/auth/v1/signup", body)
            val session = parseSessionFromJson(obj)
            if (session != null) {
                persistSession(context, session)
                SignUpOutcome.SignedIn(session)
            } else {
                SignUpOutcome.ConfirmationRequired(cleanEmail)
            }
        }
    }

    suspend fun sendPasswordReset(email: String): Result<Unit> = withContext(Dispatchers.IO) {
        runCatching {
            val cleanEmail = email.trim().lowercase()
            require(cleanEmail.contains("@") && cleanEmail.contains(".")) { "Enter a valid email address" }
            val body = json.encodeToString(mapOf("email" to cleanEmail))
            postAuth("/auth/v1/recover", body)
            Unit
        }
    }

    fun startGoogleOAuthUrl(context: Context): String {
        val verifier = generatePkceVerifier()
        prefs(context)?.edit()?.putString(KEY_PKCE_VERIFIER, verifier)?.apply()
        return buildGoogleAuthorizeUrl(computePkceS256Challenge(verifier))
    }

    fun buildGoogleAuthorizeUrl(codeChallenge: String): String {
        val encodedRedirect = URLEncoder.encode(REDIRECT_URI, "UTF-8")
        val encodedChallenge = URLEncoder.encode(codeChallenge, "UTF-8")
        return "$SUPABASE_URL/auth/v1/authorize?provider=google" +
            "&redirect_to=$encodedRedirect" +
            "&code_challenge=$encodedChallenge" +
            "&code_challenge_method=s256"
    }

    fun isAuthCallbackIntent(intent: Intent?): Boolean {
        val data = intent?.data ?: return false
        return data.scheme == "keybird" && data.host == "auth-callback"
    }

    suspend fun handleOAuthCallbackUri(
        context: Context,
        uriString: String
    ): Result<SupabaseSession> = withContext(Dispatchers.IO) {
        runCatching {
            val parsed = parseOAuthCallbackParams(uriString)
            if (!parsed.errorDescription.isNullOrBlank()) {
                error(parsed.errorDescription)
            }
            val code = parsed.code
            if (!code.isNullOrBlank()) {
                val verifier = prefs(context)?.getString(KEY_PKCE_VERIFIER, null).orEmpty()
                require(verifier.isNotBlank()) { "Login session expired. Tap Continue with Google again." }
                val payload = json.encodeToString(mapOf("auth_code" to code, "code_verifier" to verifier))
                val obj = postAuth("/auth/v1/token?grant_type=pkce", payload)
                prefs(context)?.edit()?.remove(KEY_PKCE_VERIFIER)?.apply()
                val session = parseSessionFromJson(obj) ?: error("Google login did not return a session")
                persistSession(context, session)
                return@runCatching session
            }
            val implicitAccess = parsed.accessToken
            if (!implicitAccess.isNullOrBlank()) {
                val userObj = fetchUser(implicitAccess)
                val uid = userObj["id"]?.jsonPrimitive?.contentOrNull.orEmpty()
                val mail = userObj["email"]?.jsonPrimitive?.contentOrNull.orEmpty()
                require(uid.isNotBlank()) { "Google login failed to resolve user ID" }
                val session = SupabaseSession(
                    userId = uid,
                    email = mail,
                    accessToken = implicitAccess,
                    refreshToken = parsed.refreshToken.orEmpty(),
                    expiresAtEpochSec = (System.currentTimeMillis() / 1000L) + parsed.expiresInSec
                )
                persistSession(context, session)
                return@runCatching session
            }
            error("Invalid Google login callback response")
        }
    }

    /** Returns a valid Bearer token, refreshing automatically when near expiry. */
    suspend fun getValidAccessToken(context: Context): String? = withContext(Dispatchers.IO) {
        val current = currentSession(context) ?: return@withContext null
        val nowSec = System.currentTimeMillis() / 1000L
        if (current.accessToken.isNotBlank() && nowSec < current.expiresAtEpochSec - 60L) {
            return@withContext current.accessToken
        }
        if (current.refreshToken.isBlank()) {
            return@withContext null
        }
        runCatching {
            val body = json.encodeToString(mapOf("refresh_token" to current.refreshToken))
            val obj = postAuth("/auth/v1/token?grant_type=refresh_token", body)
            val refreshed = parseSessionFromJson(obj) ?: error("Failed to refresh session")
            persistSession(context, refreshed)
            refreshed.accessToken
        }.getOrElse { err ->
            if (err.message?.contains("400") == true || err.message?.contains("401") == true) {
                signOut(context)
            }
            null
        }
    }

    private fun postAuth(path: String, jsonBody: String): JsonObject {
        val request = Request.Builder()
            .url("$SUPABASE_URL$path")
            .header("apikey", SUPABASE_PUBLISHABLE_KEY)
            .header("Content-Type", "application/json")
            .post(jsonBody.toRequestBody(JSON_MEDIA))
            .build()
        client.newCall(request).execute().use { res ->
            val raw = res.body?.string().orEmpty()
            val obj = runCatching { json.parseToJsonElement(raw).jsonObject }.getOrNull()
            if (!res.isSuccessful) {
                val msg = obj?.get("msg")?.jsonPrimitive?.contentOrNull
                    ?: obj?.get("error_description")?.jsonPrimitive?.contentOrNull
                    ?: obj?.get("message")?.jsonPrimitive?.contentOrNull
                    ?: obj?.get("error")?.jsonPrimitive?.contentOrNull
                    ?: "Auth request failed (${res.code})"
                throw IOException("$msg (${res.code})")
            }
            return obj ?: JsonObject(emptyMap())
        }
    }

    private fun fetchUser(accessToken: String): JsonObject {
        val request = Request.Builder()
            .url("$SUPABASE_URL/auth/v1/user")
            .header("apikey", SUPABASE_PUBLISHABLE_KEY)
            .header("Authorization", "Bearer $accessToken")
            .get()
            .build()
        client.newCall(request).execute().use { res ->
            val raw = res.body?.string().orEmpty()
            if (!res.isSuccessful) throw IOException("Unable to fetch user (${res.code})")
            return runCatching { json.parseToJsonElement(raw).jsonObject }.getOrDefault(JsonObject(emptyMap()))
        }
    }

    private fun parseSessionFromJson(obj: JsonObject): SupabaseSession? {
        val access = obj["access_token"]?.jsonPrimitive?.contentOrNull?.takeIf { it.isNotBlank() } ?: return null
        val refresh = obj["refresh_token"]?.jsonPrimitive?.contentOrNull.orEmpty()
        val expiresIn = obj["expires_in"]?.jsonPrimitive?.contentOrNull?.toLongOrNull() ?: 3600L
        val userObj = obj["user"]?.jsonObject
        val uid = userObj?.get("id")?.jsonPrimitive?.contentOrNull.orEmpty()
        val email = userObj?.get("email")?.jsonPrimitive?.contentOrNull.orEmpty()
        if (uid.isBlank()) return null
        val nowSec = System.currentTimeMillis() / 1000L
        return SupabaseSession(
            userId = uid,
            email = email,
            accessToken = access,
            refreshToken = refresh,
            expiresAtEpochSec = nowSec + expiresIn
        )
    }

    private fun persistSession(context: Context, session: SupabaseSession) {
        prefs(context)?.edit()?.putString(KEY_SESSION_JSON, json.encodeToString(session))?.apply()
        _sessionFlow.value = session
        initialized = true
    }

    private fun prefs(context: Context): SharedPreferences? =
        if (context.isDirectBootUnlocked) {
            context.applicationContext.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
        } else {
            null
        }

    internal fun generatePkceVerifier(): String {
        val bytes = ByteArray(32)
        SecureRandom().nextBytes(bytes)
        return base64UrlNoPad(bytes)
    }

    internal fun computePkceS256Challenge(verifier: String): String {
        val digest = MessageDigest.getInstance("SHA-256").digest(verifier.toByteArray(Charsets.US_ASCII))
        return base64UrlNoPad(digest)
    }

    internal fun parseOAuthCallbackParams(uriString: String): OAuthCallbackParams {
        val uri = URI(uriString)
        val map = mutableMapOf<String, String>()
        for (part in listOfNotNull(uri.rawQuery, uri.rawFragment)) {
            for (pair in part.split("&")) {
                if (pair.isBlank()) continue
                val idx = pair.indexOf('=')
                if (idx > 0) {
                    val k = URLDecoder.decode(pair.substring(0, idx), "UTF-8")
                    val v = URLDecoder.decode(pair.substring(idx + 1), "UTF-8")
                    map[k] = v
                }
            }
        }
        return OAuthCallbackParams(
            code = map["code"],
            accessToken = map["access_token"],
            refreshToken = map["refresh_token"],
            expiresInSec = map["expires_in"]?.toLongOrNull() ?: 3600L,
            errorDescription = map["error_description"] ?: map["error"]
        )
    }

    private fun base64UrlNoPad(bytes: ByteArray): String =
        java.util.Base64.getUrlEncoder().withoutPadding().encodeToString(bytes)
}
