package com.example.media

import android.util.Log
import com.example.security.UrlSecurityGuard
import com.squareup.moshi.JsonClass
import com.squareup.moshi.Moshi
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import okhttp3.OkHttpClient
import okhttp3.Request
import java.net.URLEncoder
import java.util.concurrent.TimeUnit

data class PodcastSearchResult(
    val collectionId: Long,
    val title: String,
    val author: String,
    val feedUrl: String,
    val genre: String
)

@JsonClass(generateAdapter = true)
internal data class ItunesSearchResponse(
    val results: List<ItunesPodcastItem> = emptyList()
)

@JsonClass(generateAdapter = true)
internal data class ItunesPodcastItem(
    val collectionId: Long = 0L,
    val collectionName: String = "",
    val artistName: String = "",
    val primaryGenreName: String = "",
    val feedUrl: String = ""
)

/**
 * Free, zero-API-key podcast directory search powered by the Apple iTunes Search API.
 * Strictly non-Gemini, validates every returned feed URL with UrlSecurityGuard (HTTPS-only).
 */
object PodcastSearchService {

    private const val TAG = "PodcastSearchService"
    private const val MAX_JSON_BYTES = 512 * 1024 // 512 KB

    private val moshi: Moshi by lazy { Moshi.Builder().build() }
    private val responseAdapter by lazy { moshi.adapter(ItunesSearchResponse::class.java) }

    private val client: OkHttpClient by lazy {
        OkHttpClient.Builder()
            .connectTimeout(8, TimeUnit.SECONDS)
            .readTimeout(10, TimeUnit.SECONDS)
            .followRedirects(true)
            .build()
    }

    suspend fun searchPodcasts(query: String): Result<List<PodcastSearchResult>> = withContext(Dispatchers.IO) {
        val cleaned = query.trim()
        if (cleaned.length < 2) {
            return@withContext Result.success(emptyList())
        }

        return@withContext try {
            val encoded = URLEncoder.encode(cleaned, "UTF-8")
            val searchUrl = "https://itunes.apple.com/search?media=podcast&entity=podcast&limit=12&term=$encoded"
            val request = Request.Builder()
                .url(searchUrl)
                .header("User-Agent", "VoiceGemBrowser/11.0 (Android)")
                .header("Accept", "application/json")
                .build()

            client.newCall(request).execute().use { response ->
                if (!response.isSuccessful) {
                    return@withContext Result.failure(IllegalStateException("Search returned HTTP ${response.code}"))
                }
                val body = response.body ?: return@withContext Result.success(emptyList())
                val rawJson = body.byteStream().use { stream ->
                    val buffer = ByteArray(8192)
                    val out = java.io.ByteArrayOutputStream()
                    var total = 0
                    while (true) {
                        val read = stream.read(buffer)
                        if (read <= 0) break
                        val allowed = minOf(read, MAX_JSON_BYTES - total)
                        if (allowed <= 0) break
                        out.write(buffer, 0, allowed)
                        total += allowed
                        if (total >= MAX_JSON_BYTES) break
                    }
                    out.toString("UTF-8")
                }
                Result.success(parseItunesJson(rawJson))
            }
        } catch (e: Exception) {
            try { Log.w(TAG, "Podcast search failed", e) } catch (_: Throwable) {}
            Result.failure(e)
        }
    }

    internal fun parseItunesJson(rawJson: String): List<PodcastSearchResult> {
        if (rawJson.isBlank()) return emptyList()
        val parsed = responseAdapter.fromJson(rawJson) ?: return emptyList()
        val results = mutableListOf<PodcastSearchResult>()

        for (item in parsed.results) {
            val safeFeed = UrlSecurityGuard.sanitizeHttpsUrlOrNull(item.feedUrl) ?: continue
            val title = item.collectionName.trim()
            if (title.isBlank()) continue
            val author = item.artistName.trim()
            val genre = item.primaryGenreName.trim().ifBlank { "Podcast" }

            results.add(
                PodcastSearchResult(
                    collectionId = item.collectionId,
                    title = title,
                    author = author,
                    feedUrl = safeFeed,
                    genre = genre
                )
            )
        }
        return results
    }
}
