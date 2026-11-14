package com.example.data.repository

import android.util.Log
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import okhttp3.OkHttpClient
import okhttp3.Request
import org.json.JSONArray
import org.json.JSONObject
import java.net.URLEncoder
import java.util.concurrent.TimeUnit

/**
 * Approach A: Free, zero-API-key spoken instant-answer fallback using Wikipedia & DuckDuckGo
 * when Gemini is temporarily unavailable or out of quota. Keeps the user inside VoiceGemBrowser.
 */
class FreeKnowledgeFallbackRepository {

    private val client = OkHttpClient.Builder()
        .connectTimeout(8, TimeUnit.SECONDS)
        .readTimeout(10, TimeUnit.SECONDS)
        .followRedirects(true)
        .build()

    data class FallbackAnswer(
        val source: String,
        val spokenText: String
    )

    suspend fun getInstantSpokenAnswer(rawQuery: String): FallbackAnswer? = withContext(Dispatchers.IO) {
        val cleanedTopic = extractSearchTopic(rawQuery)
        if (cleanedTopic.isBlank()) return@withContext null

        // 1. Try DuckDuckGo Instant Answer API first (great for quick definitions, facts, conversions)
        val ddgAnswer = queryDuckDuckGo(rawQuery) ?: queryDuckDuckGo(cleanedTopic)
        if (ddgAnswer != null) {
            return@withContext ddgAnswer
        }

        // 2. Fallback to Wikipedia OpenSearch -> REST Summary API
        val wikiAnswer = queryWikipedia(cleanedTopic)
        if (wikiAnswer != null) {
            return@withContext wikiAnswer
        }

        null
    }

    private fun queryDuckDuckGo(query: String): FallbackAnswer? {
        return try {
            val encoded = URLEncoder.encode(query, "UTF-8")
            val url = "https://api.duckduckgo.com/?q=$encoded&format=json&no_html=1&skip_disambig=1"
            val request = Request.Builder()
                .url(url)
                .header("User-Agent", "VoiceGemBrowser/7.0 (Android)")
                .build()

            client.newCall(request).execute().use { response ->
                if (!response.isSuccessful) return null
                val body = response.body?.string() ?: return null
                val json = JSONObject(body)

                val answer = json.optString("Answer").trim()
                if (answer.isNotBlank()) {
                    return FallbackAnswer("DuckDuckGo", trimToSpokenSentences(answer))
                }

                val abstractText = json.optString("AbstractText").trim()
                if (abstractText.isNotBlank()) {
                    val source = json.optString("AbstractSource").ifBlank { "Instant Answer" }
                    return FallbackAnswer(source, trimToSpokenSentences(abstractText))
                }

                val definition = json.optString("Definition").trim()
                if (definition.isNotBlank()) {
                    return FallbackAnswer("Dictionary", trimToSpokenSentences(definition))
                }
                null
            }
        } catch (e: Exception) {
            Log.w("FreeKnowledgeFallback", "DuckDuckGo query failed", e)
            null
        }
    }

    private fun queryWikipedia(topic: String): FallbackAnswer? {
        return try {
            val encodedTopic = URLEncoder.encode(topic, "UTF-8")
            val searchUrl = "https://en.wikipedia.org/w/api.php?action=opensearch&search=$encodedTopic&limit=1&namespace=0&format=json"
            val searchReq = Request.Builder()
                .url(searchUrl)
                .header("User-Agent", "VoiceGemBrowser/7.0 (Android)")
                .build()

            var bestTitle: String? = null
            client.newCall(searchReq).execute().use { response ->
                if (!response.isSuccessful) return null
                val body = response.body?.string() ?: return null
                val array = JSONArray(body)
                if (array.length() >= 2) {
                    val titles = array.getJSONArray(1)
                    if (titles.length() > 0) {
                        bestTitle = titles.getString(0)
                    }
                }
            }

            val resolvedTitle = bestTitle?.takeIf { it.isNotBlank() } ?: return null
            val summaryTitle = URLEncoder.encode(resolvedTitle.replace(" ", "_"), "UTF-8").replace("+", "%20")
            val summaryUrl = "https://en.wikipedia.org/api/rest_v1/page/summary/$summaryTitle"

            val summaryReq = Request.Builder()
                .url(summaryUrl)
                .header("User-Agent", "VoiceGemBrowser/7.0 (Android)")
                .build()

            client.newCall(summaryReq).execute().use { response ->
                if (!response.isSuccessful) return null
                val body = response.body?.string() ?: return null
                val json = JSONObject(body)
                val type = json.optString("type")
                if (type == "disambiguation") return null

                val extract = json.optString("extract").trim()
                if (extract.isNotBlank()) {
                    return FallbackAnswer("Wikipedia", trimToSpokenSentences(extract))
                }
                null
            }
        } catch (e: Exception) {
            Log.w("FreeKnowledgeFallback", "Wikipedia query failed", e)
            null
        }
    }

    internal fun extractSearchTopic(rawQuery: String): String {
        val trimmed = rawQuery.trim().trimEnd('?', '.', '!')
        val prefixes = listOf(
            "who is ", "who was ", "who are ", "who were ",
            "what is a ", "what is an ", "what is the ", "what is ",
            "what was ", "what are ", "tell me about ", "explain ",
            "define ", "can you tell me about ", "do you know about "
        )
        val lower = trimmed.lowercase()
        for (prefix in prefixes) {
            if (lower.startsWith(prefix)) {
                return trimmed.substring(prefix.length).trim()
            }
        }
        return trimmed
    }

    internal fun trimToSpokenSentences(text: String): String {
        val cleaned = text
            .replace(Regex("""\[[0-9]+\]"""), "")
            .replace(Regex("""\([^)]*listen[^)]*\)""", RegexOption.IGNORE_CASE), "")
            .replace(Regex("""\s+"""), " ")
            .trim()

        // Take up to 3 sentences or ~360 characters for comfortable hands-free listening
        val sentences = cleaned.split(Regex("""(?<=[.!?])\s+"""))
        val selected = StringBuilder()
        for (sentence in sentences.take(3)) {
            if (selected.length + sentence.length > 380 && selected.isNotEmpty()) break
            if (selected.isNotEmpty()) selected.append(" ")
            selected.append(sentence)
        }
        return selected.toString().ifBlank { cleaned.take(350) }
    }
}
