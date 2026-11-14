package com.example.media

import android.util.Log
import android.util.Xml
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import okhttp3.OkHttpClient
import okhttp3.Request
import org.xmlpull.v1.XmlPullParser
import org.xmlpull.v1.XmlPullParserFactory
import java.io.StringReader
import java.util.concurrent.TimeUnit

object RssPodcastResolver {

    private const val TAG = "RssPodcastResolver"

    private val httpClient: OkHttpClient by lazy {
        OkHttpClient.Builder()
            .connectTimeout(12, TimeUnit.SECONDS)
            .readTimeout(15, TimeUnit.SECONDS)
            .followRedirects(true)
            .followSslRedirects(true)
            .build()
    }

    /**
     * Resolves a podcast stream URL.
     * If the URL is an RSS feed (.xml, /feed, or returns XML), it parses the feed
     * and extracts the latest episode's audio enclosure URL (.mp3 / .aac).
     * Otherwise, returns the direct audio URL.
     */
    suspend fun resolveAudioUrl(url: String): String = withContext(Dispatchers.IO) {
        val trimmedUrl = url.trim()
        if (trimmedUrl.isBlank()) return@withContext trimmedUrl

        // Fast path: already a direct audio file
        val lower = trimmedUrl.lowercase()
        if (lower.endsWith(".mp3") || lower.endsWith(".aac") || lower.endsWith(".m4a") ||
            lower.endsWith(".ogg") || lower.endsWith(".m3u8")
        ) {
            return@withContext trimmedUrl
        }

        try {
            val request = Request.Builder()
                .url(trimmedUrl)
                .header("User-Agent", "Mozilla/5.0 (Linux; Android 14) VoiceGemBrowser/6.0 PodcastResolver")
                .header("Accept", "application/rss+xml, application/xml, text/xml, audio/*, */*")
                .build()

            httpClient.newCall(request).execute().use { response ->
                if (!response.isSuccessful) {
                    Log.w(TAG, "HTTP error ${response.code} resolving $trimmedUrl")
                    return@withContext trimmedUrl
                }

                val contentType = response.header("Content-Type")?.lowercase() ?: ""
                val finalUrl = response.request.url.toString()

                // If server responded directly with an audio MIME type, use final URL
                if (contentType.startsWith("audio/")) {
                    Log.i(TAG, "Resolved direct audio stream: $finalUrl")
                    return@withContext finalUrl
                }

                val bodyString = response.body?.string() ?: return@withContext finalUrl

                // Check if response is XML/RSS
                val isXml = contentType.contains("xml") || contentType.contains("rss") ||
                        bodyString.contains("<rss", ignoreCase = true) ||
                        bodyString.contains("<channel", ignoreCase = true) ||
                        bodyString.contains("<?xml", ignoreCase = true)

                if (isXml) {
                    val enclosureUrl = parseLatestEnclosure(bodyString)
                    if (!enclosureUrl.isNullOrBlank()) {
                        Log.i(TAG, "Extracted latest episode audio URL: $enclosureUrl")
                        return@withContext enclosureUrl
                    }
                }

                return@withContext finalUrl
            }
        } catch (e: Exception) {
            Log.w(TAG, "Exception resolving podcast URL: $trimmedUrl, falling back to original", e)
            return@withContext trimmedUrl
        }
    }

    /**
     * Parses an RSS XML string and extracts the audio URL from the first <item>'s <enclosure>
     */
    fun parseLatestEnclosure(xmlContent: String): String? {
        return try {
            val factory = XmlPullParserFactory.newInstance().apply {
                isNamespaceAware = false
            }
            val parser = factory.newPullParser().apply {
                setInput(StringReader(xmlContent))
            }

            var eventType = parser.eventType
            var insideItem = false

            while (eventType != XmlPullParser.END_DOCUMENT) {
                val tagName = parser.name
                when (eventType) {
                    XmlPullParser.START_TAG -> {
                        if ("item".equals(tagName, ignoreCase = true)) {
                            insideItem = true
                        } else if (insideItem && "enclosure".equals(tagName, ignoreCase = true)) {
                            val audioUrl = parser.getAttributeValue(null, "url")
                            val type = parser.getAttributeValue(null, "type") ?: ""
                            if (!audioUrl.isNullOrBlank() && (type.isBlank() || type.startsWith("audio") || audioUrl.contains(".mp3") || audioUrl.contains(".m4a"))) {
                                return audioUrl.trim()
                            }
                        } else if (insideItem && "media:content".equals(tagName, ignoreCase = true)) {
                            val mediaUrl = parser.getAttributeValue(null, "url")
                            val type = parser.getAttributeValue(null, "type") ?: ""
                            if (!mediaUrl.isNullOrBlank() && (type.isBlank() || type.startsWith("audio"))) {
                                return mediaUrl.trim()
                            }
                        }
                    }
                    XmlPullParser.END_TAG -> {
                        if ("item".equals(tagName, ignoreCase = true)) {
                            // Finished first item; if we found nothing in first item, continue to next
                        }
                    }
                }
                eventType = parser.next()
            }
            null
        } catch (e: Exception) {
            Log.w(TAG, "Failed to parse RSS XML enclosure", e)
            null
        }
    }
}
