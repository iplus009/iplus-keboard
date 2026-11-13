package org.futo.inputmethod.keyboard.internal

import android.util.Log
import android.util.LruCache
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.withContext
import org.json.JSONArray
import java.io.IOException
import java.net.ConnectException
import java.net.HttpURLConnection
import java.net.SocketTimeoutException
import java.net.URL
import java.net.URLEncoder
import java.net.UnknownHostException

object CloudTranslationHandler {
    private const val TAG = "CloudTranslationHandler"
    private const val CACHE_SIZE = 64
    private const val MAX_ATTEMPTS = 2
    private const val RETRY_BACKOFF_MS = 350L

    private val translationCache = LruCache<String, String>(CACHE_SIZE)

    internal fun buildCacheKey(source: String, target: String, text: String): String =
        "$source|$target|$text"

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

    suspend fun translate(source: String, target: String, text: String): Result<String> = withContext(Dispatchers.IO) {
        if (text.isBlank()) return@withContext Result.success("")

        val cacheKey = buildCacheKey(source, target, text)
        translationCache.get(cacheKey)?.let { cached ->
            return@withContext Result.success(cached)
        }

        val encodedSource = URLEncoder.encode(source, "UTF-8")
        val encodedTarget = URLEncoder.encode(target, "UTF-8")
        val encodedText = URLEncoder.encode(text, "UTF-8")
        val urlString =
            "https://translate.googleapis.com/translate_a/single?client=gtx&sl=$encodedSource&tl=$encodedTarget&dt=t&q=$encodedText"

        var lastException: Exception? = null
        for (attempt in 0 until MAX_ATTEMPTS) {
            var connection: HttpURLConnection? = null
            try {
                val url = URL(urlString)
                connection = (url.openConnection() as HttpURLConnection).apply {
                    requestMethod = "GET"
                    setRequestProperty(
                        "User-Agent",
                        "Mozilla/5.0 (Android; Mobile; rv:100.0) Gecko/100.0 Firefox/100.0"
                    )
                    connectTimeout = 8000
                    readTimeout = 8000
                }

                val responseCode = connection.responseCode
                if (responseCode == HttpURLConnection.HTTP_OK) {
                    val response = connection.inputStream.bufferedReader().use { it.readText() }
                    val translatedText = parseTranslationResponse(response)
                    translationCache.put(cacheKey, translatedText)
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
            } finally {
                connection?.disconnect()
            }
        }

        Result.failure(
            TranslationException(formatTranslationError(lastException), lastException)
        )
    }

    class TranslationException(message: String, cause: Throwable? = null) : Exception(message, cause)
}