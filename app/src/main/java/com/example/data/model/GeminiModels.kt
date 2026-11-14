package com.example.data.model

import com.squareup.moshi.Json
import com.squareup.moshi.JsonClass

@JsonClass(generateAdapter = true)
data class GeminiRequest(
    @property:Json(name = "contents") val contents: List<GeminiContent>,
    @property:Json(name = "systemInstruction") val systemInstruction: GeminiContent? = null,
    @property:Json(name = "generationConfig") val generationConfig: GeminiGenerationConfig? = null
)

@JsonClass(generateAdapter = true)
data class GeminiContent(
    @property:Json(name = "role") val role: String? = null,
    @property:Json(name = "parts") val parts: List<GeminiPart>
)

@JsonClass(generateAdapter = true)
data class GeminiPart(
    @property:Json(name = "text") val text: String? = null
)

@JsonClass(generateAdapter = true)
data class GeminiGenerationConfig(
    @property:Json(name = "temperature") val temperature: Float? = 0.7f,
    @property:Json(name = "topP") val topP: Float? = 0.95f,
    @property:Json(name = "topK") val topK: Int? = 40,
    @property:Json(name = "maxOutputTokens") val maxOutputTokens: Int? = 1000
)

@JsonClass(generateAdapter = true)
data class GeminiResponse(
    @property:Json(name = "candidates") val candidates: List<GeminiCandidate>? = null,
    @property:Json(name = "error") val error: GeminiError? = null
)

@JsonClass(generateAdapter = true)
data class GeminiCandidate(
    @property:Json(name = "content") val content: GeminiContent? = null,
    @property:Json(name = "finishReason") val finishReason: String? = null
)

@JsonClass(generateAdapter = true)
data class GeminiError(
    @property:Json(name = "code") val code: Int? = null,
    @property:Json(name = "message") val message: String? = null,
    @property:Json(name = "status") val status: String? = null
)

data class ChatMessage(
    val id: String = java.util.UUID.randomUUID().toString(),
    val sender: MessageSender,
    val text: String,
    val timestamp: Long = System.currentTimeMillis()
)

enum class MessageSender {
    USER,
    VOICE_GEM
}
