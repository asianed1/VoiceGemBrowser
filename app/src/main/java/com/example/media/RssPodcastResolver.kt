package com.example.media

import android.util.Log
import com.example.BuildConfig
import com.example.security.UrlSecurityGuard
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import okhttp3.OkHttpClient
import okhttp3.Request
import org.xml.sax.Attributes
import org.xml.sax.InputSource
import org.xml.sax.helpers.DefaultHandler
import java.io.ByteArrayOutputStream
import java.io.StringReader
import java.util.concurrent.TimeUnit
import javax.xml.parsers.SAXParserFactory

object RssPodcastResolver {

    private const val TAG = "RssPodcastResolver"
    internal const val MAX_RSS_BYTES = 2 * 1024 * 1024 // 2 MB cap prevents memory DoS

    private val httpClient: OkHttpClient by lazy {
        OkHttpClient.Builder()
            .connectTimeout(12, TimeUnit.SECONDS)
            .readTimeout(15, TimeUnit.SECONDS)
            .followRedirects(true)
            .followSslRedirects(true)
            .build()
    }

    private fun logWarn(msg: String, tr: Throwable? = null) {
        try {
            if (tr != null) Log.w(TAG, msg, tr) else Log.w(TAG, msg)
        } catch (_: Throwable) {
        }
    }

    private fun logInfo(msg: String) {
        try {
            Log.i(TAG, msg)
        } catch (_: Throwable) {
        }
    }

    /**
     * Resolves a podcast or radio stream URL to the latest playable audio stream URL.
     * Validates all input, redirect, and enclosure URLs against UrlSecurityGuard (HTTPS-only).
     */
    suspend fun resolveAudioUrl(url: String): String {
        val episodes = resolveFeedEpisodes(url, maxEpisodes = 1)
        return episodes.firstOrNull()?.audioUrl ?: ""
    }

    /**
     * Resolves up to [maxEpisodes] episodes from an RSS feed, or wraps a direct audio stream
     * into a single PodcastEpisode entry. Returns an empty list if the URL fails HTTPS validation.
     */
    suspend fun resolveFeedEpisodes(
        url: String,
        maxEpisodes: Int = 8
    ): List<PodcastEpisode> = withContext(Dispatchers.IO) {
        val safeUrl = UrlSecurityGuard.sanitizeHttpsUrlOrNull(url) ?: run {
            logWarn("Blocked unsafe or non-HTTPS stream URL")
            return@withContext emptyList()
        }

        // Fast path: direct audio file
        val lower = safeUrl.lowercase()
        if (lower.endsWith(".mp3") || lower.endsWith(".aac") || lower.endsWith(".m4a") ||
            lower.endsWith(".ogg") || lower.endsWith(".m3u8")
        ) {
            return@withContext listOf(
                PodcastEpisode(title = "Direct Audio Stream", audioUrl = safeUrl)
            )
        }

        try {
            val request = Request.Builder()
                .url(safeUrl)
                .header("User-Agent", "Mozilla/5.0 (Linux; Android 14) VoiceGemBrowser/${BuildConfig.VERSION_NAME} PodcastResolver")
                .header("Accept", "application/rss+xml, application/xml, text/xml, audio/*, */*")
                .build()

            httpClient.newCall(request).execute().use { response ->
                if (!response.isSuccessful) {
                    logWarn("HTTP error ${response.code} resolving stream")
                    return@withContext listOf(PodcastEpisode(title = "Audio Stream", audioUrl = safeUrl))
                }

                val contentType = response.header("Content-Type")?.lowercase() ?: ""
                val finalUrl = UrlSecurityGuard.sanitizeHttpsUrlOrNull(response.request.url.toString())
                    ?: return@withContext emptyList()

                if (contentType.startsWith("audio/")) {
                    logInfo("Resolved direct audio stream")
                    return@withContext listOf(
                        PodcastEpisode(title = "Live Audio Stream", audioUrl = finalUrl)
                    )
                }

                val body = response.body ?: return@withContext listOf(
                    PodcastEpisode(title = "Audio Stream", audioUrl = finalUrl)
                )

                val bodyString = body.byteStream().use { stream ->
                    val buffer = ByteArray(8192)
                    val out = ByteArrayOutputStream()
                    var total = 0
                    while (true) {
                        val read = stream.read(buffer)
                        if (read <= 0) break
                        val allowed = minOf(read, MAX_RSS_BYTES - total)
                        if (allowed <= 0) break
                        out.write(buffer, 0, allowed)
                        total += allowed
                        if (total >= MAX_RSS_BYTES) break
                    }
                    out.toString("UTF-8")
                }

                val isXml = contentType.contains("xml") || contentType.contains("rss") ||
                    bodyString.contains("<rss", ignoreCase = true) ||
                    bodyString.contains("<channel", ignoreCase = true) ||
                    bodyString.contains("<?xml", ignoreCase = true)

                if (isXml) {
                    val episodes = parseEpisodes(bodyString, maxEpisodes)
                    if (episodes.isNotEmpty()) {
                        return@withContext episodes
                    }
                }

                return@withContext listOf(PodcastEpisode(title = "Audio Stream", audioUrl = finalUrl))
            }
        } catch (e: Exception) {
            logWarn("Exception resolving podcast URL", e)
            return@withContext listOf(PodcastEpisode(title = "Audio Stream", audioUrl = safeUrl))
        }
    }

    /**
     * Parses the latest episode audio URL from an RSS XML string.
     */
    fun parseLatestEnclosure(xmlContent: String): String? {
        return parseEpisodes(xmlContent, maxEpisodes = 1).firstOrNull()?.audioUrl
    }

    /**
     * Parses up to [maxEpisodes] episodes safely from RSS XML (with XXE/DTD features disabled
     * and every enclosure URL validated via UrlSecurityGuard).
     */
    fun parseEpisodes(xmlContent: String, maxEpisodes: Int = 8): List<PodcastEpisode> {
        if (xmlContent.isBlank()) return emptyList()
        val boundedXml = if (xmlContent.length > MAX_RSS_BYTES) {
            xmlContent.take(MAX_RSS_BYTES)
        } else {
            xmlContent
        }

        val episodes = mutableListOf<PodcastEpisode>()
        try {
            val factory = SAXParserFactory.newInstance().apply {
                isNamespaceAware = false
                try { setFeature("http://apache.org/xml/features/disallow-doctype-decl", true) } catch (_: Exception) {}
                try { setFeature("http://xml.org/sax/features/external-general-entities", false) } catch (_: Exception) {}
                try { setFeature("http://xml.org/sax/features/external-parameter-entities", false) } catch (_: Exception) {}
            }
            val parser = factory.newSAXParser()
            val handler = object : DefaultHandler() {
                private var insideItem = false
                private var currentTag = ""
                private val titleBuf = StringBuilder()
                private val pubDateBuf = StringBuilder()
                private val durationBuf = StringBuilder()
                private var audioUrl = ""

                override fun startElement(uri: String?, localName: String?, qName: String?, attributes: Attributes?) {
                    if (episodes.size >= maxEpisodes) return
                    val name = (qName ?: localName ?: "").lowercase()
                    currentTag = name
                    if (name == "item") {
                        insideItem = true
                        titleBuf.clear()
                        pubDateBuf.clear()
                        durationBuf.clear()
                        audioUrl = ""
                    } else if (insideItem && name == "enclosure") {
                        val raw = attributes?.getValue("url")?.trim() ?: ""
                        val type = attributes?.getValue("type") ?: ""
                        if (raw.isNotBlank() && (type.isBlank() || type.startsWith("audio") || raw.contains(".mp3") || raw.contains(".m4a"))) {
                            val safe = UrlSecurityGuard.sanitizeHttpsUrlOrNull(raw)
                            if (safe != null && audioUrl.isBlank()) {
                                audioUrl = safe
                            }
                        }
                    } else if (insideItem && name == "media:content") {
                        val raw = attributes?.getValue("url")?.trim() ?: ""
                        val type = attributes?.getValue("type") ?: ""
                        if (raw.isNotBlank() && (type.isBlank() || type.startsWith("audio"))) {
                            val safe = UrlSecurityGuard.sanitizeHttpsUrlOrNull(raw)
                            if (safe != null && audioUrl.isBlank()) {
                                audioUrl = safe
                            }
                        }
                    }
                }

                override fun characters(ch: CharArray?, start: Int, length: Int) {
                    if (!insideItem || ch == null || episodes.size >= maxEpisodes) return
                    when (currentTag) {
                        "title" -> titleBuf.append(ch, start, length)
                        "pubdate" -> pubDateBuf.append(ch, start, length)
                        "itunes:duration", "duration" -> durationBuf.append(ch, start, length)
                    }
                }

                override fun endElement(uri: String?, localName: String?, qName: String?) {
                    val name = (qName ?: localName ?: "").lowercase()
                    if (name == "item" && insideItem) {
                        if (audioUrl.isNotBlank() && episodes.size < maxEpisodes) {
                            val cleanTitle = titleBuf.toString().trim().ifBlank { "Episode ${episodes.size + 1}" }
                            val shortDate = formatShortPubDate(pubDateBuf.toString())
                            val durationMs = parseDurationMs(durationBuf.toString())
                            episodes.add(
                                PodcastEpisode(
                                    title = cleanTitle,
                                    audioUrl = audioUrl,
                                    pubDate = shortDate,
                                    durationMs = durationMs
                                )
                            )
                        }
                        insideItem = false
                    }
                    currentTag = ""
                }
            }
            parser.parse(InputSource(StringReader(boundedXml)), handler)
        } catch (e: Exception) {
            logWarn("Failed to parse RSS XML episodes", e)
        }
        return episodes
    }

    internal fun parseDurationMs(raw: String): Long {
        val trimmed = raw.trim()
        if (trimmed.isEmpty()) return 0L
        val parts = trimmed.split(":")
        return try {
            when (parts.size) {
                1 -> (parts[0].toLongOrNull() ?: 0L) * 1000L
                2 -> {
                    val mins = parts[0].toLongOrNull() ?: 0L
                    val secs = parts[1].toLongOrNull() ?: 0L
                    (mins * 60L + secs) * 1000L
                }
                3 -> {
                    val hrs = parts[0].toLongOrNull() ?: 0L
                    val mins = parts[1].toLongOrNull() ?: 0L
                    val secs = parts[2].toLongOrNull() ?: 0L
                    (hrs * 3600L + mins * 60L + secs) * 1000L
                }
                else -> 0L
            }
        } catch (_: Exception) {
            0L
        }
    }

    private fun formatShortPubDate(raw: String): String {
        val cleaned = raw.trim()
        if (cleaned.isEmpty()) return ""
        val afterComma = cleaned.substringAfter(",", cleaned).trim()
        val tokens = afterComma.split(" ")
        return if (tokens.size >= 3) "${tokens[0]} ${tokens[1]} ${tokens[2]}" else cleaned.take(16)
    }
}
