package org.futo.inputmethod.latin.themestore.network

import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import kotlinx.serialization.encodeToString
import kotlinx.serialization.json.Json
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.RequestBody.Companion.toRequestBody
import org.futo.inputmethod.latin.themestore.model.AccountEntitlementsResponse
import org.futo.inputmethod.latin.themestore.model.ApiErrorWrapper
import org.futo.inputmethod.latin.themestore.model.CatalogResponse
import org.futo.inputmethod.latin.themestore.model.CreateOrderRequest
import org.futo.inputmethod.latin.themestore.model.CreateOrderResponse
import org.futo.inputmethod.latin.themestore.model.DeactivateRequest
import org.futo.inputmethod.latin.themestore.model.FreeThemeResponse
import org.futo.inputmethod.latin.themestore.model.PaidDownloadRequest
import org.futo.inputmethod.latin.themestore.model.RecoverRequest
import org.futo.inputmethod.latin.themestore.model.RecoverResponse
import org.futo.inputmethod.latin.themestore.model.RedeemKeyRequest
import org.futo.inputmethod.latin.themestore.model.RedeemKeyResponse
import org.futo.inputmethod.latin.themestore.model.SignedEntitlementReceipt
import org.futo.inputmethod.latin.themestore.model.SignedRevocationList
import org.futo.inputmethod.latin.themestore.model.SubmitTrxRequest
import org.futo.inputmethod.latin.themestore.model.SubmitTrxResponse
import org.futo.inputmethod.latin.themestore.security.ThemeLicenseGuard
import java.io.IOException
import java.util.concurrent.TimeUnit

/**
 * Result of a theme download: either the inline JSON manifest (existing inline themes) or
 * the raw `.iplustheme` ZIP bytes plus the `X-Bundle-Sha256` header (B2/private-bucket packs).
 */
data class DownloadedBundle(
    val manifestJson: String? = null,
    val bundleSha256: String = "",
    val zipBytes: ByteArray? = null
) {
    val isInline: Boolean get() = manifestJson != null
}

/**
 * Room 1/4/5 Network Client: runs strictly from SettingsActivity (never from LatinIME typing loop).
 */
class ThemeStoreApiClient(
    private val baseUrl: String = DEFAULT_BASE_URL
) {
    private val json = Json {
        ignoreUnknownKeys = true
        encodeDefaults = true
    }

    private val client = OkHttpClient.Builder()
        .connectTimeout(8, TimeUnit.SECONDS)
        .readTimeout(15, TimeUnit.SECONDS)
        .writeTimeout(15, TimeUnit.SECONDS)
        .build()

    suspend fun fetchCatalog(appId: String = ThemeLicenseGuard.APP_ID): Result<CatalogResponse> =
        getJson("/v1/catalog?app_id=$appId")

    suspend fun fetchFreeTheme(
        themeId: String,
        appId: String = ThemeLicenseGuard.APP_ID
    ): Result<FreeThemeResponse> =
        getJson("/v1/themes/$themeId/free?app_id=$appId")

    /**
     * Phase 2: fetches a free theme bundle. Inline themes return the JSON manifest;
     * `.iplustheme` packs return raw ZIP bytes (with `X-Bundle-Sha256` when not redirected).
     */
    suspend fun downloadFreeBundle(
        themeId: String,
        appId: String = ThemeLicenseGuard.APP_ID
    ): Result<DownloadedBundle> =
        fetchBundle("/v1/themes/$themeId/free?app_id=$appId", method = "GET")

    /** Phase 2: fetches a paid theme bundle under a verified entitlement. */
    suspend fun downloadPaidBundle(
        themeId: String,
        deviceHash: String,
        receipt: SignedEntitlementReceipt,
        appId: String = ThemeLicenseGuard.APP_ID
    ): Result<DownloadedBundle> =
        fetchBundle(
            "/v1/entitlements/download",
            method = "POST",
            payload = PaidDownloadRequest(
                appId = appId,
                themeId = themeId,
                deviceHash = deviceHash,
                receipt = receipt
            )
        )

    suspend fun createOrder(
        req: CreateOrderRequest,
        accessToken: String? = null
    ): Result<CreateOrderResponse> =
        postJson("/v1/orders/create", req, accessToken)

    suspend fun submitTrx(req: SubmitTrxRequest): Result<SubmitTrxResponse> =
        postJson("/v1/orders/submit-trx", req)

    suspend fun redeemKey(
        req: RedeemKeyRequest,
        accessToken: String? = null
    ): Result<RedeemKeyResponse> =
        postJson("/v1/keys/redeem", req, accessToken)

    suspend fun downloadPaidTheme(
        themeId: String,
        deviceHash: String,
        receipt: SignedEntitlementReceipt,
        appId: String = ThemeLicenseGuard.APP_ID
    ): Result<FreeThemeResponse> =
        postJson(
            "/v1/entitlements/download",
            PaidDownloadRequest(
                appId = appId,
                themeId = themeId,
                deviceHash = deviceHash,
                receipt = receipt
            )
        )

    suspend fun deactivateDevice(req: DeactivateRequest): Result<Unit> = withContext(Dispatchers.IO) {
        runCatching {
            val bodyStr = json.encodeToString(req)
            val request = Request.Builder()
                .url("${baseUrl.trimEnd('/')}/v1/entitlements/deactivate")
                .post(bodyStr.toRequestBody(JSON_MEDIA))
                .build()
            client.newCall(request).execute().use { res ->
                val text = res.body?.string().orEmpty()
                if (!res.isSuccessful) throw IOException(parseErrorMessage(text, res.code))
            }
        }
    }

    suspend fun recoverEntitlements(
        req: RecoverRequest,
        accessToken: String? = null
    ): Result<RecoverResponse> =
        postJson("/v1/entitlements/recover", req, accessToken)

    suspend fun fetchAccountEntitlements(
        deviceHash: String,
        accessToken: String,
        appId: String = ThemeLicenseGuard.APP_ID
    ): Result<AccountEntitlementsResponse> =
        getJson("/v1/account/entitlements?app_id=$appId&device_hash=$deviceHash", accessToken)

    suspend fun fetchRevocations(appId: String = ThemeLicenseGuard.APP_ID): Result<SignedRevocationList> =
        getJson("/v1/revocations?app_id=$appId")

    private suspend fun fetchBundle(
        path: String,
        method: String,
        payload: Any? = null
    ): Result<DownloadedBundle> = withContext(Dispatchers.IO) {
        runCatching {
            val builder = Request.Builder().url("${baseUrl.trimEnd('/')}$path")
            val request = if (method == "GET") {
                builder.get().build()
            } else {
                builder.post(json.encodeToString(payload).toRequestBody(JSON_MEDIA)).build()
            }
            client.newCall(request).execute().use { res ->
                val bytes = res.body?.bytes() ?: throw IOException("Empty bundle response")
                if (!res.isSuccessful) {
                    throw IOException(parseErrorMessage(bytes.toString(Charsets.UTF_8), res.code))
                }
                val headerSha = res.headers["X-Bundle-Sha256"].orEmpty()
                if (bytes.isNotEmpty() && bytes[0] == '{'.code.toByte()) {
                    val resp = json.decodeFromString<FreeThemeResponse>(bytes.toString(Charsets.UTF_8))
                    val manifest = resp.manifest ?: error("Theme bundle manifest missing")
                    DownloadedBundle(
                        manifestJson = json.encodeToString(manifest),
                        bundleSha256 = resp.bundleSha256
                    )
                } else {
                    DownloadedBundle(zipBytes = bytes, bundleSha256 = headerSha)
                }
            }
        }
    }

    private suspend inline fun <reified Res> getJson(
        path: String,
        accessToken: String? = null
    ): Result<Res> =
        withContext(Dispatchers.IO) {
            runCatching {
                val builder = Request.Builder()
                    .url("${baseUrl.trimEnd('/')}$path")
                if (!accessToken.isNullOrBlank()) {
                    builder.header("Authorization", "Bearer $accessToken")
                }
                val request = builder.get().build()
                client.newCall(request).execute().use { res ->
                    val text = res.body?.string().orEmpty()
                    if (!res.isSuccessful) throw IOException(parseErrorMessage(text, res.code))
                    json.decodeFromString<Res>(text)
                }
            }
        }

    private suspend inline fun <reified Req, reified Res> postJson(
        path: String,
        payload: Req,
        accessToken: String? = null
    ): Result<Res> = withContext(Dispatchers.IO) {
        runCatching {
            val bodyStr = json.encodeToString(payload)
            val builder = Request.Builder()
                .url("${baseUrl.trimEnd('/')}$path")
            if (!accessToken.isNullOrBlank()) {
                builder.header("Authorization", "Bearer $accessToken")
            }
            val request = builder
                .post(bodyStr.toRequestBody(JSON_MEDIA))
                .build()
            client.newCall(request).execute().use { res ->
                val text = res.body?.string().orEmpty()
                if (!res.isSuccessful) throw IOException(parseErrorMessage(text, res.code))
                json.decodeFromString<Res>(text)
            }
        }
    }

    private fun parseErrorMessage(body: String, code: Int): String {
        val parsed = runCatching { json.decodeFromString<ApiErrorWrapper>(body) }.getOrNull()
        val msg = parsed?.error?.message
        return if (!msg.isNullOrBlank()) msg else "Request failed ($code)"
    }

    companion object {
        const val DEFAULT_BASE_URL = "https://iplus-theme-hub.diagnostic-fanatic.workers.dev"
        private val JSON_MEDIA = "application/json; charset=utf-8".toMediaType()
    }
}
