package org.futo.inputmethod.keyboard.internal

import android.util.Log
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.suspendCancellableCoroutine
import kotlinx.coroutines.withContext
import okhttp3.Call
import okhttp3.Callback
import okhttp3.ConnectionPool
import okhttp3.Dns
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.Response
import org.json.JSONArray
import java.io.IOException
import java.net.ConnectException
import java.net.Inet4Address
import java.net.InetAddress
import java.net.SocketTimeoutException
import java.net.URLEncoder
import java.net.UnknownHostException
import java.util.Collections
import java.util.concurrent.TimeUnit
import kotlin.coroutines.resume
import kotlin.coroutines.resumeWithException

object CloudTranslationHandler {
    private const val TAG = "CloudTranslationHandler"
    private const val CACHE_SIZE = 256
    private const val MAX_ATTEMPTS = 2
    private const val RETRY_BACKOFF_MS = 200L
    private const val PREWARM_COOLDOWN_MS = 60_000L

    private val translationCache: MutableMap<String, String> = Collections.synchronizedMap(
        object : LinkedHashMap<String, String>(CACHE_SIZE, 0.75f, true) {
            override fun removeEldestEntry(eldest: MutableMap.MutableEntry<String, String>?): Boolean {
                return size > CACHE_SIZE
            }
        }
    )

    private val preferIpv4Dns = object : Dns {
        override fun lookup(hostname: String): List<InetAddress> {
            val all = Dns.SYSTEM.lookup(hostname)
            val ipv4 = all.filterIsInstance<Inet4Address>()
            return if (ipv4.isNotEmpty()) ipv4 + all.filterNot { it is Inet4Address } else all
        }
    }

    private val httpClient: OkHttpClient by lazy {
        OkHttpClient.Builder()
            .connectionPool(ConnectionPool(4, 2, TimeUnit.MINUTES))
            .dns(preferIpv4Dns)
            .connectTimeout(4, TimeUnit.SECONDS)
            .readTimeout(5, TimeUnit.SECONDS)
            .callTimeout(8, TimeUnit.SECONDS)
            .retryOnConnectionFailure(true)
            .build()
    }

    @Volatile
    private var lastPrewarmMs = 0L

    internal fun buildCacheKey(source: String, target: String, text: String): String =
        "$source|$target|${text.trim()}"

    internal fun buildTranslateUrl(source: String, target: String, text: String): String {
        val encodedSource = URLEncoder.encode(source, "UTF-8")
        val encodedTarget = URLEncoder.encode(target, "UTF-8")
        val encodedText = URLEncoder.encode(text.trim(), "UTF-8")
        return "https://translate.googleapis.com/translate_a/single?client=gtx&sl=$encodedSource&tl=$encodedTarget&dt=t&q=$encodedText"
    }

    fun getCached(source: String, target: String, text: String): String? {
        val trimmed = text.trim()
        if (trimmed.isEmpty()) return ""
        if (source != "auto" && source == target) return trimmed
        return translationCache[buildCacheKey(source, target, trimmed)]
    }

    internal fun putCachedForTest(source: String, target: String, text: String, translated: String) {
        translationCache[buildCacheKey(source, target, text)] = translated
    }

    internal fun clearCacheForTest() {
        translationCache.clear()
    }

    fun prewarm() {
        val now = System.currentTimeMillis()
        if (now - lastPrewarmMs < PREWARM_COOLDOWN_MS) return
        lastPrewarmMs = now
        runCatching {
            val request = Request.Builder()
                .url("https://translate.googleapis.com/translate_a/single?client=gtx&sl=en&tl=bn&dt=t&q=hi")
                .head()
                .header("User-Agent", "Mozilla/5.0 (Android; Mobile; rv:100.0) Gecko/100.0 Firefox/100.0")
                .build()
            httpClient.newCall(request).enqueue(object : Callback {
                override fun onFailure(call: Call, e: IOException) {}
                override fun onResponse(call: Call, response: Response) {
                    response.close()
                }
            })
        }
    }

    internal fun parseTranslationResponse(response: String): String {
        val outerArray = JSONArray(response)
        val sentences = outerArray.getJSONArray(0)
        val sb = StringBuilder()
        for (i in 0 until sentences.length()) {
            val part = sentences.getJSONArray(i)
            sb.append(part.getString(0))
        }
        return sb.toString()
    }

    internal fun isTransientIoError(e: Throwable): Boolean =
        e is SocketTimeoutException ||
            e is UnknownHostException ||
            e is ConnectException ||
            e is IOException

    internal fun formatTranslationError(e: Throwable?, responseCode: Int? = null): String {
        if (responseCode != null) {
            return when (responseCode) {
                429 -> "Too many translation requests. Please wait a moment."
                in 500..599 -> "Translation service temporarily unavailable (HTTP $responseCode)."
                else -> "Translation request failed (HTTP $responseCode)."
            }
        }
        return when (e) {
            is SocketTimeoutException -> "Translation timed out. Check your connection and try again."
            is UnknownHostException, is ConnectException -> "You appear to be offline. Check your internet connection."
            is IOException -> "Network error while translating. Please try again."
            else -> "Could not translate right now. Please try again."
        }
    }

    private suspend fun executeCancellable(request: Request): Pair<Int, String> =
        suspendCancellableCoroutine { cont ->
            val call = httpClient.newCall(request)
            cont.invokeOnCancellation { call.cancel() }
            call.enqueue(object : Callback {
                override fun onFailure(call: Call, e: IOException) {
                    if (cont.isActive) {
                        cont.resumeWithException(e)
                    }
                }

                override fun onResponse(call: Call, response: Response) {
                    response.use { res ->
                        val bodyStr = runCatching { res.body?.string().orEmpty() }.getOrDefault("")
                        if (cont.isActive) {
                            cont.resume(res.code to bodyStr)
                        }
                    }
                }
            })
        }

    suspend fun translate(source: String, target: String, text: String): Result<String> = withContext(Dispatchers.IO) {
        val trimmed = text.trim()
        if (trimmed.isEmpty()) return@withContext Result.success("")
        if (source != "auto" && source == target) return@withContext Result.success(trimmed)

        val cacheKey = buildCacheKey(source, target, trimmed)
        translationCache[cacheKey]?.let { cached ->
            return@withContext Result.success(cached)
        }

        val urlString = buildTranslateUrl(source, target, trimmed)
        val request = Request.Builder()
            .url(urlString)
            .get()
            .header("User-Agent", "Mozilla/5.0 (Android; Mobile; rv:100.0) Gecko/100.0 Firefox/100.0")
            .build()

        var lastException: Exception? = null
        for (attempt in 0 until MAX_ATTEMPTS) {
            try {
                val (responseCode, responseBody) = executeCancellable(request)
                if (responseCode == 200) {
                    val translatedText = parseTranslationResponse(responseBody)
                    translationCache[cacheKey] = translatedText
                    return@withContext Result.success(translatedText)
                } else {
                    val errorMsg = formatTranslationError(null, responseCode)
                    Log.e(TAG, "Translation HTTP error: $responseCode")
                    if (responseCode in 500..599 && attempt + 1 < MAX_ATTEMPTS) {
                        delay(RETRY_BACKOFF_MS)
                        continue
                    }
                    return@withContext Result.failure(TranslationException(errorMsg))
                }
            } catch (e: CancellationException) {
                throw e
            } catch (e: Exception) {
                lastException = e
                Log.e(TAG, "Translation exception (attempt ${attempt + 1})", e)
                if (isTransientIoError(e) && attempt + 1 < MAX_ATTEMPTS) {
                    delay(RETRY_BACKOFF_MS)
                    continue
                }
                return@withContext Result.failure(
                    TranslationException(formatTranslationError(e), e)
                )
            }
        }

        Result.failure(
            TranslationException(formatTranslationError(lastException), lastException)
        )
    }

    class TranslationException(message: String, cause: Throwable? = null) : Exception(message, cause)
}