package com.example.voice

import android.content.Context
import android.content.SharedPreferences
import android.os.Bundle
import android.speech.tts.TextToSpeech
import android.speech.tts.UtteranceProgressListener
import android.speech.tts.Voice
import android.util.Log
import java.util.Locale

enum class VoiceGenderPreference(val displayName: String) {
    FEMALE("American Female"),
    MALE("American Male")
}

data class VoiceSwitchResult(
    val selectedGender: VoiceGenderPreference,
    val foundDistinctVoice: Boolean
)

class VoiceSpeaker(
    context: Context,
    private val onInitReady: () -> Unit
) {
    private val appContext = context.applicationContext
    private val prefs: SharedPreferences =
        appContext.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
    private var tts: TextToSpeech? = null
    var isReady: Boolean = false
        private set

    var preferredGender: VoiceGenderPreference = loadSavedGender()
        private set

    var speechRate: Float = 1.05f
        set(value) {
            field = value
            tts?.setSpeechRate(value)
        }

    var pitch: Float = 1.0f
        set(value) {
            field = value
            tts?.setPitch(value)
        }

    private var currentOnDone: (() -> Unit)? = null
    private var currentOnStart: (() -> Unit)? = null
    private var currentOnError: (() -> Unit)? = null

    init {
        tts = TextToSpeech(appContext) { status ->
            if (status == TextToSpeech.SUCCESS) {
                val result = tts?.setLanguage(Locale.US)
                if (result == TextToSpeech.LANG_MISSING_DATA || result == TextToSpeech.LANG_NOT_SUPPORTED) {
                    tts?.setLanguage(Locale.getDefault())
                }
                applyPreferredVoice(preferredGender)
                tts?.setSpeechRate(speechRate)
                tts?.setPitch(pitch)
                val audioAttributes = android.media.AudioAttributes.Builder()
                    .setContentType(android.media.AudioAttributes.CONTENT_TYPE_SPEECH)
                    .setUsage(android.media.AudioAttributes.USAGE_MEDIA)
                    .build()
                tts?.setAudioAttributes(audioAttributes)
                isReady = true
                setupProgressListener()
                onInitReady()
            } else {
                Log.e("VoiceSpeaker", "TextToSpeech init failed with status: $status")
            }
        }
    }

    private fun loadSavedGender(): VoiceGenderPreference {
        val saved = prefs.getString(KEY_VOICE_GENDER, VoiceGenderPreference.FEMALE.name)
        return try {
            VoiceGenderPreference.valueOf(saved ?: VoiceGenderPreference.FEMALE.name)
        } catch (_: Exception) {
            VoiceGenderPreference.FEMALE
        }
    }

    fun setVoiceGender(gender: VoiceGenderPreference): VoiceSwitchResult {
        preferredGender = gender
        prefs.edit().putString(KEY_VOICE_GENDER, gender.name).apply()
        val distinctFound = applyPreferredVoice(gender)
        return VoiceSwitchResult(
            selectedGender = gender,
            foundDistinctVoice = distinctFound
        )
    }

    /**
     * Selects a natural, non-robotic American English (en-US) voice matching the chosen gender.
     * Avoids artificial pitch-warping; relies strictly on genuine high-quality TTS voices.
     */
    private fun applyPreferredVoice(gender: VoiceGenderPreference): Boolean {
        val engine = tts ?: return false
        return try {
            val allVoices = engine.voices?.toList().orEmpty()
            val usVoices = allVoices.filter { voice ->
                voice.locale.language.equals("en", ignoreCase = true) &&
                    voice.locale.country.equals("US", ignoreCase = true)
            }

            if (usVoices.isEmpty()) {
                engine.setLanguage(Locale.US)
                return false
            }

            // Rank voices: higher quality first, offline/local neural voices prioritized over network-only
            val rankedUsVoices = usVoices.sortedWith(
                compareByDescending<Voice> { it.quality }
                    .thenBy { it.isNetworkConnectionRequired }
                    .thenBy { it.name }
            )

            val matchedVoice = findVoiceForGender(rankedUsVoices, gender)
            if (matchedVoice != null) {
                engine.voice = matchedVoice
                Log.i("VoiceSpeaker", "Selected en-US ${gender.name} voice: ${matchedVoice.name} (quality=${matchedVoice.quality})")
                // Check if the other gender also has a distinct voice installed on this device
                val otherGender = if (gender == VoiceGenderPreference.FEMALE) VoiceGenderPreference.MALE else VoiceGenderPreference.FEMALE
                val otherVoice = findVoiceForGender(rankedUsVoices, otherGender)
                otherVoice == null || otherVoice.name != matchedVoice.name
            } else {
                engine.voice = rankedUsVoices.first()
                false
            }
        } catch (e: Exception) {
            Log.w("VoiceSpeaker", "Could not apply voice gender preference", e)
            false
        }
    }

    internal fun findVoiceForGender(
        rankedUsVoices: List<Voice>,
        gender: VoiceGenderPreference
    ): Voice? {
        if (rankedUsVoices.isEmpty()) return null

        // Known Google TTS natural en-US neural/local voice tokens
        val femaleTokens = listOf(
            "#female", "female", "woman",
            "-sfg-", "-tpf-", "-iob-", "-iog-", "-tpc-"
        )
        val maleTokens = listOf(
            "#male", "male", "man",
            "-iom-", "-iol-", "-tpd-", "-ioh-"
        )

        val targetTokens = if (gender == VoiceGenderPreference.FEMALE) femaleTokens else maleTokens
        val excludeTokens = if (gender == VoiceGenderPreference.FEMALE) maleTokens else femaleTokens

        // 1. Exact token match (avoiding substring collisions like "male" matching "female")
        val directMatch = rankedUsVoices.firstOrNull { voice ->
            val lowerName = voice.name.lowercase(Locale.US)
            val featureStr = voice.features?.joinToString(" ")?.lowercase(Locale.US) ?: ""
            val combined = "$lowerName $featureStr"
            val matchesTarget = targetTokens.any { token ->
                if (token == "male" || token == "man") {
                    Regex("""(^|[^a-z])$token([^a-z]|$)""").containsMatchIn(combined)
                } else {
                    combined.contains(token)
                }
            }
            val matchesOpposite = excludeTokens.any { token ->
                if (token == "male" || token == "man") {
                    Regex("""(^|[^a-z])$token([^a-z]|$)""").containsMatchIn(combined)
                } else {
                    combined.contains(token)
                }
            }
            matchesTarget && !matchesOpposite
        }
        if (directMatch != null) return directMatch

        // 2. Fallback: if multiple quality en-US voices exist, pick distinct slots deterministically
        return if (gender == VoiceGenderPreference.FEMALE) {
            rankedUsVoices.first()
        } else {
            rankedUsVoices.getOrNull(1) ?: rankedUsVoices.first()
        }
    }

    private fun setupProgressListener() {
        tts?.setOnUtteranceProgressListener(object : UtteranceProgressListener() {
            override fun onStart(utteranceId: String?) {
                currentOnStart?.invoke()
            }

            override fun onDone(utteranceId: String?) {
                val callback = currentOnDone
                currentOnDone = null
                currentOnStart = null
                currentOnError = null
                callback?.invoke()
            }

            @Deprecated("Deprecated in Java")
            override fun onError(utteranceId: String?) {
                val callback = currentOnError
                currentOnDone = null
                currentOnStart = null
                currentOnError = null
                callback?.invoke()
            }

            override fun onError(utteranceId: String?, errorCode: Int) {
                Log.w("VoiceSpeaker", "TTS error code $errorCode for utterance $utteranceId")
                val callback = currentOnError
                currentOnDone = null
                currentOnStart = null
                currentOnError = null
                callback?.invoke()
            }
        })
    }

    fun speak(
        text: String,
        utteranceId: String = "voice_gem_${System.currentTimeMillis()}",
        onStart: (() -> Unit)? = null,
        onDone: (() -> Unit)? = null,
        onError: (() -> Unit)? = null
    ) {
        if (!isReady || tts == null) {
            onError?.invoke()
            return
        }

        currentOnStart = onStart
        currentOnDone = onDone
        currentOnError = onError

        val params = Bundle().apply {
            putString(TextToSpeech.Engine.KEY_PARAM_UTTERANCE_ID, utteranceId)
            putFloat(TextToSpeech.Engine.KEY_PARAM_VOLUME, 1.0f)
        }

        tts?.speak(text, TextToSpeech.QUEUE_FLUSH, params, utteranceId)
    }

    fun stop() {
        try {
            tts?.stop()
            currentOnDone = null
            currentOnStart = null
            currentOnError = null
        } catch (e: Exception) {
            Log.w("VoiceSpeaker", "Error stopping TTS", e)
        }
    }

    fun isSpeaking(): Boolean {
        return tts?.isSpeaking == true
    }

    fun shutdown() {
        try {
            tts?.stop()
            tts?.shutdown()
            tts = null
            isReady = false
        } catch (e: Exception) {
            Log.w("VoiceSpeaker", "Error shutting down TTS", e)
        }
    }

    companion object {
        private const val PREFS_NAME = "voicegem_voice_prefs"
        private const val KEY_VOICE_GENDER = "preferred_voice_gender"
    }
}
