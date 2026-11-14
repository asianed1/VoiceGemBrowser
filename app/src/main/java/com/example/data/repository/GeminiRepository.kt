package com.example.data.repository

import com.example.BuildConfig
import com.example.data.api.RetrofitClient
import com.example.data.model.ChatMessage
import com.example.data.model.GeminiContent
import com.example.data.model.GeminiGenerationConfig
import com.example.data.model.GeminiPart
import com.example.data.model.GeminiRequest
import com.example.data.model.MessageSender
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.withContext
import retrofit2.HttpException
import java.io.IOException
import java.net.SocketTimeoutException
import kotlin.random.Random

class GeminiApiException(
    message: String,
    val httpCode: Int? = null,
    val isRateLimit: Boolean = false,
    val isDailyQuotaExhausted: Boolean = false,
    val isNetworkError: Boolean = false,
    cause: Throwable? = null
) : Exception(message, cause)

class GeminiRepository {

    private val apiService = RetrofitClient.service

    private val systemInstruction = GeminiContent(
        parts = listOf(
            GeminiPart(
                text = """
                    You are VoiceGem, an intelligent voice-first conversational AI and spoken browser assistant.
                    The user interacts with you EXCLUSIVELY by voice, and all your output is spoken directly aloud to their ears through Text-to-Speech.

                    MANDATORY VOICE FORMATTING RULES:
                    1. Absolutely NEVER output markdown formatting such as asterisks (* or **), hashes (#), underscores (_), bullet points, or markdown headers.
                    2. NEVER output URLs, hyperlinks, website addresses, or phrases like 'click here' or 'visit this link'.
                    3. Do not output code blocks, markdown tables, or raw data dumps.
                    4. Keep your spoken language natural, engaging, clear, lively, and conversational.
                    5. Explain concepts directly in complete, well-paced spoken sentences.
                    6. If the answer is multi-faceted, explain it conversationally (e.g., 'First... Additionally... And lastly...').
                    7. If appropriate for an open-ended topic, conclude with a brief conversational invite for their next question.
                """.trimIndent()
            )
        )
    )

    suspend fun getConversationalAnswer(
        userPrompt: String,
        history: List<ChatMessage>,
        apiKeyOverride: String? = null
    ): Result<String> = withContext(Dispatchers.IO) {
        try {
            val apiKey = if (!apiKeyOverride.isNullOrBlank()) apiKeyOverride else BuildConfig.GEMINI_API_KEY
            if (apiKey.isBlank() || apiKey == "MY_GEMINI_API_KEY") {
                return@withContext Result.failure(
                    GeminiApiException(
                        message = "Gemini API access requires a key. Set your key in Settings.",
                        isDailyQuotaExhausted = false
                    )
                )
            }

            // Build multi-turn context (last 8 messages for focused context)
            val contents = mutableListOf<GeminiContent>()
            val recentHistory = history.takeLast(8)
            for (message in recentHistory) {
                val role = if (message.sender == MessageSender.USER) "user" else "model"
                contents.add(
                    GeminiContent(
                        role = role,
                        parts = listOf(GeminiPart(text = message.text))
                    )
                )
            }

            // Add current question
            contents.add(
                GeminiContent(
                    role = "user",
                    parts = listOf(GeminiPart(text = userPrompt))
                )
            )

            val request = GeminiRequest(
                contents = contents,
                systemInstruction = systemInstruction,
                generationConfig = GeminiGenerationConfig(
                    temperature = 0.7f,
                    topP = 0.95f,
                    maxOutputTokens = 1000
                )
            )

            val response = executeWithFastFailRetry {
                apiService.generateContent(apiKey = apiKey, request = request)
            }

            if (response.error != null) {
                val errCode = response.error.code
                val errMsg = response.error.message ?: "Gemini API error $errCode"
                val isDaily = isDailyLimitMessage(errMsg)
                return@withContext Result.failure(
                    GeminiApiException(
                        message = errMsg,
                        httpCode = errCode,
                        isRateLimit = errCode == 429,
                        isDailyQuotaExhausted = isDaily
                    )
                )
            }

            val rawText = response.candidates
                ?.firstOrNull()
                ?.content
                ?.parts
                ?.firstOrNull()
                ?.text

            if (rawText.isNullOrBlank()) {
                return@withContext Result.failure(
                    GeminiApiException("VoiceGem received an empty response.")
                )
            }

            val sanitizedText = cleanTextForSpeech(rawText)
            Result.success(sanitizedText)
        } catch (e: CancellationException) {
            throw e
        } catch (e: GeminiApiException) {
            Result.failure(e)
        } catch (e: Exception) {
            Result.failure(classifyException(e))
        }
    }

    /**
     * Voice-optimized fast-fail truncated exponential backoff with randomized jitter.
     * Retries at most 2 times (total wait capped under ~4.5s) ONLY for transient errors
     * (temporary HTTP 429 rate bursts, 5xx server hiccups, or brief socket timeouts)
     * so drivers never endure long awkward silence.
     */
    internal suspend fun <T> executeWithFastFailRetry(
        maxRetries: Int = 2,
        baseDelayMs: Long = 1100L,
        maxJitterMs: Long = 450L,
        delayFn: suspend (Long) -> Unit = { delay(it) },
        block: suspend () -> T
    ): T {
        var attempt = 0
        while (true) {
            try {
                return block()
            } catch (e: CancellationException) {
                throw e
            } catch (e: Exception) {
                val classified = classifyException(e)
                val shouldRetry = attempt < maxRetries && isRetryable(classified)
                if (!shouldRetry) {
                    throw classified
                }
                val exponentialDelay = baseDelayMs * (1L shl attempt)
                val jitter = if (maxJitterMs > 0) Random.nextLong(0, maxJitterMs + 1) else 0L
                val totalDelay = (exponentialDelay + jitter).coerceAtMost(2800L)
                attempt++
                delayFn(totalDelay)
            }
        }
    }

    internal fun isRetryable(exception: GeminiApiException): Boolean {
        if (exception.isDailyQuotaExhausted) return false
        return when (exception.httpCode) {
            429, 500, 502, 503, 504 -> true
            400, 401, 403, 404 -> false
            else -> exception.cause is SocketTimeoutException
        }
    }

    internal fun classifyException(throwable: Throwable): GeminiApiException {
        if (throwable is GeminiApiException) return throwable

        if (throwable is HttpException) {
            val code = throwable.code()
            val errorBody = try {
                throwable.response()?.errorBody()?.string() ?: ""
            } catch (_: Exception) {
                ""
            }
            val combined = "$errorBody ${throwable.message()}"
            val isDaily = code == 429 && isDailyLimitMessage(combined)
            val friendlyMessage = when (code) {
                429 -> if (isDaily) {
                    "Daily Gemini API quota exhausted."
                } else {
                    "Gemini is temporarily busy due to rate limits."
                }
                401, 403 -> "Gemini API key was rejected or unauthorized."
                in 500..599 -> "Gemini service is temporarily experiencing server issues."
                else -> "Gemini service returned error $code."
            }
            return GeminiApiException(
                message = friendlyMessage,
                httpCode = code,
                isRateLimit = code == 429,
                isDailyQuotaExhausted = isDaily,
                cause = throwable
            )
        }

        if (throwable is IOException) {
            return GeminiApiException(
                message = "No active internet connection to reach Gemini.",
                isNetworkError = true,
                cause = throwable
            )
        }

        return GeminiApiException(
            message = throwable.message ?: "Could not connect to Gemini.",
            cause = throwable
        )
    }

    private fun isDailyLimitMessage(text: String): Boolean {
        val lower = text.lowercase()
        return lower.contains("per day") ||
            lower.contains("daily") ||
            lower.contains("per_day") ||
            lower.contains("billing") ||
            lower.contains("quota exceeded for quota metric 'requests' and limit 'requests per day'")
    }

    /**
     * Strips any markdown remnants, bullet symbols, citations, or URLs so Text-To-Speech reads purely natural speech.
     */
    internal fun cleanTextForSpeech(text: String): String {
        return text
            .replace(Regex("""https?://\S+"""), "")
            .replace(Regex("""\[(.*?)\]\((.*?)\)"""), "$1") // markdown links -> just text
            .replace(Regex("""[*_`#~>]"""), "") // markdown symbols
            .replace(Regex("""^\s*[-*+]\s+""", RegexOption.MULTILINE), "") // bullet points
            .replace(Regex("""\n{2,}"""), " ")
            .replace(Regex("""\s{2,}"""), " ")
            .trim()
    }
}
