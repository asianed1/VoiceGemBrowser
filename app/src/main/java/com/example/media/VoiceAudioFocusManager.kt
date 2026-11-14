package com.example.media

import android.content.Context
import android.media.AudioAttributes
import android.media.AudioFocusRequest
import android.media.AudioManager
import android.os.Build
import android.util.Log

class VoiceAudioFocusManager(
    context: Context,
    private val onDuckChange: (shouldDuck: Boolean) -> Unit,
    private val onPauseRequested: () -> Unit,
    private val onResumeRequested: () -> Unit
) {
    private val audioManager = context.applicationContext.getSystemService(Context.AUDIO_SERVICE) as AudioManager
    private var focusRequest: AudioFocusRequest? = null
    var hasFocus: Boolean = false
        private set

    private val focusChangeListener = AudioManager.OnAudioFocusChangeListener { focusChange ->
        when (focusChange) {
            AudioManager.AUDIOFOCUS_LOSS_TRANSIENT_CAN_DUCK -> {
                Log.d("VoiceAudioFocus", "Ducking audio to background volume")
                onDuckChange(true)
            }
            AudioManager.AUDIOFOCUS_GAIN -> {
                Log.d("VoiceAudioFocus", "Regained full audio focus")
                onDuckChange(false)
                onResumeRequested()
            }
            AudioManager.AUDIOFOCUS_LOSS_TRANSIENT -> {
                Log.d("VoiceAudioFocus", "Transient audio focus loss, pausing")
                onPauseRequested()
            }
            AudioManager.AUDIOFOCUS_LOSS -> {
                Log.d("VoiceAudioFocus", "Permanent audio focus loss, stopping")
                onDuckChange(false)
                onPauseRequested()
                abandonFocus()
            }
        }
    }

    /**
     * Request priority audio focus when VoiceGem wants to speak or listen
     */
    fun requestVoicePriorityFocus(): Boolean {
        return if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            val playbackAttributes = AudioAttributes.Builder()
                .setUsage(AudioAttributes.USAGE_ASSISTANT)
                .setContentType(AudioAttributes.CONTENT_TYPE_SPEECH)
                .build()

            val request = AudioFocusRequest.Builder(AudioManager.AUDIOFOCUS_GAIN_TRANSIENT_MAY_DUCK)
                .setAudioAttributes(playbackAttributes)
                .setAcceptsDelayedFocusGain(false)
                .setOnAudioFocusChangeListener(focusChangeListener)
                .build()

            focusRequest = request
            val result = audioManager.requestAudioFocus(request)
            hasFocus = (result == AudioManager.AUDIOFOCUS_REQUEST_GRANTED)
            hasFocus
        } else {
            @Suppress("DEPRECATION")
            val result = audioManager.requestAudioFocus(
                focusChangeListener,
                AudioManager.STREAM_MUSIC,
                AudioManager.AUDIOFOCUS_GAIN_TRANSIENT_MAY_DUCK
            )
            hasFocus = (result == AudioManager.AUDIOFOCUS_REQUEST_GRANTED)
            hasFocus
        }
    }

    fun abandonFocus() {
        if (!hasFocus) return
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            focusRequest?.let { audioManager.abandonAudioFocusRequest(it) }
        } else {
            @Suppress("DEPRECATION")
            audioManager.abandonAudioFocus(focusChangeListener)
        }
        hasFocus = false
        onDuckChange(false)
    }
}
