package com.example.voice

import android.content.Context
import android.media.AudioManager
import android.media.ToneGenerator
import android.os.Build
import android.os.VibrationEffect
import android.os.Vibrator
import android.os.VibratorManager
import android.util.Log

class SoundFxManager(context: Context) {

    private val appContext = context.applicationContext
    private var toneGenerator: ToneGenerator? = null

    private val vibrator: Vibrator? = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
        val vibratorManager = appContext.getSystemService(Context.VIBRATOR_MANAGER_SERVICE) as? VibratorManager
        vibratorManager?.defaultVibrator
    } else {
        @Suppress("DEPRECATION")
        appContext.getSystemService(Context.VIBRATOR_SERVICE) as? Vibrator
    }

    init {
        try {
            toneGenerator = ToneGenerator(AudioManager.STREAM_MUSIC, 85)
        } catch (e: Exception) {
            Log.e("SoundFxManager", "Failed to init ToneGenerator", e)
        }
    }

    /**
     * Startup beep before greeting: clear, pleasant high-tech tone
     */
    fun playStartupBeep() {
        try {
            toneGenerator?.startTone(ToneGenerator.TONE_PROP_BEEP, 180)
            vibrate(40)
        } catch (e: Exception) {
            Log.w("SoundFxManager", "Tone error", e)
        }
    }

    /**
     * Listening activation cue: crisp acknowledgment beep
     */
    fun playListeningCue() {
        try {
            toneGenerator?.startTone(ToneGenerator.TONE_PROP_ACK, 120)
            vibrate(30)
        } catch (e: Exception) {
            Log.w("SoundFxManager", "Tone error", e)
        }
    }

    /**
     * Question received / processing cue
     */
    fun playProcessingCue() {
        try {
            toneGenerator?.startTone(ToneGenerator.TONE_PROP_PROMPT, 100)
            vibrate(25)
        } catch (e: Exception) {
            Log.w("SoundFxManager", "Tone error", e)
        }
    }

    fun playErrorCue() {
        try {
            toneGenerator?.startTone(ToneGenerator.TONE_PROP_NACK, 150)
            vibrate(60)
        } catch (e: Exception) {
            Log.w("SoundFxManager", "Tone error", e)
        }
    }

    /**
     * Chat exit / termination cue
     */
    fun playShutdownCue() {
        try {
            toneGenerator?.startTone(ToneGenerator.TONE_PROP_PROMPT, 160)
            vibrate(50)
        } catch (e: Exception) {
            Log.w("SoundFxManager", "Tone error", e)
        }
    }

    private fun vibrate(milliseconds: Long) {
        try {
            if (vibrator?.hasVibrator() == true) {
                if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
                    vibrator.vibrate(
                        VibrationEffect.createOneShot(milliseconds, VibrationEffect.DEFAULT_AMPLITUDE)
                    )
                } else {
                    @Suppress("DEPRECATION")
                    vibrator.vibrate(milliseconds)
                }
            }
        } catch (_: Exception) {}
    }

    fun release() {
        toneGenerator?.release()
        toneGenerator = null
    }
}
