package com.example.voice

import android.content.Context
import android.content.Intent
import android.os.Bundle
import android.os.Handler
import android.os.Looper
import android.speech.RecognitionListener
import android.speech.RecognizerIntent
import android.speech.SpeechRecognizer
import android.util.Log

class VoiceRecognizer(
    private val context: Context,
    private val listener: Callback
) {
    interface Callback {
        fun onReady()
        fun onSpeechStarted()
        fun onRmsChanged(rmsDb: Float)
        fun onPartialText(partial: String)
        fun onFinalResult(transcription: String)
        fun onError(errorCode: Int, message: String)
    }

    private var speechRecognizer: SpeechRecognizer? = null
    private val mainHandler = Handler(Looper.getMainLooper())
    var isListening: Boolean = false
        private set

    init {
        mainHandler.post {
            initRecognizer()
        }
    }

    private fun initRecognizer() {
        if (SpeechRecognizer.isRecognitionAvailable(context)) {
            try {
                speechRecognizer = SpeechRecognizer.createSpeechRecognizer(context).apply {
                    setRecognitionListener(createRecognitionListener())
                }
            } catch (e: Exception) {
                Log.e("VoiceRecognizer", "Failed to create SpeechRecognizer", e)
            }
        } else {
            Log.w("VoiceRecognizer", "SpeechRecognizer is not available on this device")
        }
    }

    private fun createRecognitionListener(): RecognitionListener {
        return object : RecognitionListener {
            override fun onReadyForSpeech(params: Bundle?) {
                isListening = true
                listener.onReady()
            }

            override fun onBeginningOfSpeech() {
                listener.onSpeechStarted()
            }

            override fun onRmsChanged(rmsdB: Float) {
                // RMS dB typically ranges from -2 to 10. Normalize roughly between 0.0 and 1.0
                val normalized = ((rmsdB + 2f) / 12f).coerceIn(0f, 1f)
                listener.onRmsChanged(normalized)
            }

            override fun onBufferReceived(buffer: ByteArray?) {}

            override fun onEndOfSpeech() {
                // Speech ended, recognizer is finalizing results
            }

            override fun onError(error: Int) {
                isListening = false
                val message = when (error) {
                    SpeechRecognizer.ERROR_NO_MATCH -> "No speech detected. Please speak clearly."
                    SpeechRecognizer.ERROR_SPEECH_TIMEOUT -> "Silence timeout. Tap the Gem to speak."
                    SpeechRecognizer.ERROR_AUDIO -> "Audio recording error."
                    SpeechRecognizer.ERROR_CLIENT -> "Speech recognition client error."
                    SpeechRecognizer.ERROR_NETWORK -> "Network error during speech recognition."
                    SpeechRecognizer.ERROR_NETWORK_TIMEOUT -> "Network timeout."
                    SpeechRecognizer.ERROR_INSUFFICIENT_PERMISSIONS -> "Microphone permission required."
                    SpeechRecognizer.ERROR_RECOGNIZER_BUSY -> "Speech service is busy."
                    SpeechRecognizer.ERROR_SERVER -> "Speech server error."
                    else -> "Speech recognition error ($error)"
                }
                Log.w("VoiceRecognizer", "Speech error code $error: $message")
                listener.onError(error, message)
            }

            override fun onResults(results: Bundle?) {
                isListening = false
                val matches = results?.getStringArrayList(SpeechRecognizer.RESULTS_RECOGNITION)
                val text = matches?.firstOrNull()?.trim()
                if (!text.isNullOrBlank()) {
                    listener.onFinalResult(text)
                } else {
                    listener.onError(SpeechRecognizer.ERROR_NO_MATCH, "No words recognized.")
                }
            }

            override fun onPartialResults(partialResults: Bundle?) {
                val matches = partialResults?.getStringArrayList(SpeechRecognizer.RESULTS_RECOGNITION)
                val text = matches?.firstOrNull()?.trim()
                if (!text.isNullOrBlank()) {
                    listener.onPartialText(text)
                }
            }

            override fun onEvent(eventType: Int, params: Bundle?) {}
        }
    }

    fun startListening(dictationMode: Boolean = false) {
        mainHandler.post {
            try {
                if (speechRecognizer == null) {
                    initRecognizer()
                }

                val completeSilenceMs = if (dictationMode) 3800L else 2500L
                val possibleSilenceMs = if (dictationMode) 2800L else 2000L

                val intent = Intent(RecognizerIntent.ACTION_RECOGNIZE_SPEECH).apply {
                    putExtra(
                        RecognizerIntent.EXTRA_LANGUAGE_MODEL,
                        RecognizerIntent.LANGUAGE_MODEL_FREE_FORM
                    )
                    putExtra(RecognizerIntent.EXTRA_PARTIAL_RESULTS, true)
                    putExtra(RecognizerIntent.EXTRA_MAX_RESULTS, 3)
                    putExtra(RecognizerIntent.EXTRA_CALLING_PACKAGE, context.packageName)
                    putExtra("android.speech.extra.SPEECH_INPUT_COMPLETE_SILENCE_LENGTH_MILLIS", completeSilenceMs)
                    putExtra("android.speech.extra.SPEECH_INPUT_POSSIBLY_COMPLETE_SILENCE_LENGTH_MILLIS", possibleSilenceMs)
                }

                speechRecognizer?.startListening(intent)
                isListening = true
            } catch (e: Exception) {
                Log.e("VoiceRecognizer", "Error starting listening", e)
                isListening = false
                listener.onError(-1, "Unable to start microphone: ${e.message}")
            }
        }
    }

    fun stopListening() {
        mainHandler.post {
            try {
                speechRecognizer?.stopListening()
            } catch (e: Exception) {
                Log.w("VoiceRecognizer", "Error stopping listening", e)
            } finally {
                isListening = false
            }
        }
    }

    fun cancel() {
        mainHandler.post {
            try {
                speechRecognizer?.cancel()
            } catch (e: Exception) {
                Log.w("VoiceRecognizer", "Error canceling recognizer", e)
            } finally {
                isListening = false
            }
        }
    }

    fun destroy() {
        mainHandler.post {
            try {
                speechRecognizer?.destroy()
                speechRecognizer = null
            } catch (e: Exception) {
                Log.w("VoiceRecognizer", "Error destroying recognizer", e)
            } finally {
                isListening = false
            }
        }
    }
}
