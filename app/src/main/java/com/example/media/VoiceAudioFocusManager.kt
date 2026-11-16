package com.example.media

import android.content.Context
import android.media.AudioAttributes
import android.media.AudioFocusRequest
import android.media.AudioManager
import android.os.Build
import android.util.Log

/**
 * Pure state coordinator for podcast/radio media continuity across VoiceGem voice turns
 * and transient system interruptions (phone calls, Google Assistant, SpeechRecognizer).
 */
class MediaFocusCoordinator {
    var wantsMediaPlayback: Boolean = false
        private set
    var pausedByTransientLoss: Boolean = false
        private set
    var isVoiceTurnActive: Boolean = false
        private set

    fun onMediaStarted() {
        wantsMediaPlayback = true
        pausedByTransientLoss = false
    }

    fun onMediaManuallyPaused() {
        wantsMediaPlayback = false
        pausedByTransientLoss = false
    }

    fun onMediaStopped() {
        wantsMediaPlayback = false
        pausedByTransientLoss = false
    }

    fun onVoiceTurnStarted() {
        isVoiceTurnActive = true
    }

    /**
     * Evaluates an OS audio focus callback for the long-running media channel.
     * Returns true if media playback should now resume, false otherwise.
     */
    fun onMediaFocusLostTransient(canDuck: Boolean): FocusAction {
        if (!wantsMediaPlayback) return FocusAction.NONE
        return if (canDuck) {
            FocusAction.DUCK
        } else {
            pausedByTransientLoss = true
            FocusAction.PAUSE_TRANSIENT
        }
    }

    fun onMediaFocusLostPermanent(): FocusAction {
        val hadMedia = wantsMediaPlayback
        wantsMediaPlayback = false
        pausedByTransientLoss = false
        return if (hadMedia) FocusAction.PAUSE_PERMANENT else FocusAction.NONE
    }

    fun onMediaFocusRegained(): FocusAction {
        if (!wantsMediaPlayback) {
            pausedByTransientLoss = false
            return FocusAction.NONE
        }
        // Keep ducked/paused while VoiceGem's own voice turn is still active;
        // onVoiceTurnEnded() will cleanly restore playback immediately after.
        if (isVoiceTurnActive) {
            return FocusAction.NONE
        }
        val shouldResume = pausedByTransientLoss
        pausedByTransientLoss = false
        return if (shouldResume) FocusAction.RESUME_AND_UNDUCK else FocusAction.UNDUCK
    }

    /**
     * Called when VoiceGem finishes speaking/listening.
     * Ensures podcasts automatically recover even if SpeechRecognizer stole transient focus.
     */
    fun onVoiceTurnEnded(): FocusAction {
        isVoiceTurnActive = false
        if (!wantsMediaPlayback) {
            pausedByTransientLoss = false
            return FocusAction.NONE
        }
        val shouldResume = pausedByTransientLoss
        pausedByTransientLoss = false
        return if (shouldResume) FocusAction.RESUME_AND_UNDUCK else FocusAction.UNDUCK
    }

    enum class FocusAction {
        NONE,
        DUCK,
        UNDUCK,
        PAUSE_TRANSIENT,
        PAUSE_PERMANENT,
        RESUME_AND_UNDUCK
    }
}

class VoiceAudioFocusManager(
    context: Context,
    private val onDuckChange: (shouldDuck: Boolean) -> Unit,
    private val onPauseRequested: (isPermanent: Boolean) -> Unit,
    private val onResumeRequested: () -> Unit
) {
    private val audioManager = context.applicationContext.getSystemService(Context.AUDIO_SERVICE) as AudioManager
    private var mediaFocusRequest: AudioFocusRequest? = null
    private var voiceFocusRequest: AudioFocusRequest? = null

    val coordinator = MediaFocusCoordinator()

    var hasMediaFocus: Boolean = false
        private set
    var hasVoiceFocus: Boolean = false
        private set

    private val mediaFocusListener = AudioManager.OnAudioFocusChangeListener { focusChange ->
        when (focusChange) {
            AudioManager.AUDIOFOCUS_LOSS_TRANSIENT_CAN_DUCK -> {
                if (coordinator.onMediaFocusLostTransient(canDuck = true) == MediaFocusCoordinator.FocusAction.DUCK) {
                    Log.d("VoiceAudioFocus", "Media focus ducked (transient)")
                    onDuckChange(true)
                }
            }
            AudioManager.AUDIOFOCUS_LOSS_TRANSIENT -> {
                if (coordinator.onMediaFocusLostTransient(canDuck = false) == MediaFocusCoordinator.FocusAction.PAUSE_TRANSIENT) {
                    Log.d("VoiceAudioFocus", "Media focus lost transiently -> pausing (will auto-resume)")
                    onPauseRequested(false)
                }
            }
            AudioManager.AUDIOFOCUS_GAIN -> {
                when (coordinator.onMediaFocusRegained()) {
                    MediaFocusCoordinator.FocusAction.RESUME_AND_UNDUCK -> {
                        Log.d("VoiceAudioFocus", "Media focus regained -> auto-resuming podcast")
                        onDuckChange(false)
                        onResumeRequested()
                    }
                    MediaFocusCoordinator.FocusAction.UNDUCK -> {
                        Log.d("VoiceAudioFocus", "Media focus regained -> restoring volume")
                        onDuckChange(false)
                    }
                    else -> Unit
                }
            }
            AudioManager.AUDIOFOCUS_LOSS -> {
                if (coordinator.onMediaFocusLostPermanent() == MediaFocusCoordinator.FocusAction.PAUSE_PERMANENT) {
                    Log.d("VoiceAudioFocus", "Permanent media focus loss -> stopping auto-resume")
                    onDuckChange(false)
                    onPauseRequested(true)
                }
                abandonMediaPlaybackFocus()
            }
        }
    }

    private val voiceFocusListener = AudioManager.OnAudioFocusChangeListener { focusChange ->
        if (focusChange == AudioManager.AUDIOFOCUS_LOSS || focusChange == AudioManager.AUDIOFOCUS_LOSS_TRANSIENT) {
            Log.d("VoiceAudioFocus", "Assistant voice focus interrupted ($focusChange)")
        }
    }

    fun requestMediaPlaybackFocus(): Boolean {
        coordinator.onMediaStarted()
        val granted = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            val attrs = AudioAttributes.Builder()
                .setUsage(AudioAttributes.USAGE_MEDIA)
                .setContentType(AudioAttributes.CONTENT_TYPE_MUSIC)
                .build()
            val req = AudioFocusRequest.Builder(AudioManager.AUDIOFOCUS_GAIN)
                .setAudioAttributes(attrs)
                .setWillPauseWhenDucked(false)
                .setAcceptsDelayedFocusGain(true)
                .setOnAudioFocusChangeListener(mediaFocusListener)
                .build()
            mediaFocusRequest = req
            audioManager.requestAudioFocus(req) == AudioManager.AUDIOFOCUS_REQUEST_GRANTED
        } else {
            @Suppress("DEPRECATION")
            audioManager.requestAudioFocus(
                mediaFocusListener,
                AudioManager.STREAM_MUSIC,
                AudioManager.AUDIOFOCUS_GAIN
            ) == AudioManager.AUDIOFOCUS_REQUEST_GRANTED
        }
        hasMediaFocus = granted
        return granted
    }

    fun onMediaManuallyPaused() {
        coordinator.onMediaManuallyPaused()
        abandonMediaPlaybackFocus()
    }

    fun abandonMediaPlaybackFocus() {
        if (!hasMediaFocus) return
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            mediaFocusRequest?.let { audioManager.abandonAudioFocusRequest(it) }
        } else {
            @Suppress("DEPRECATION")
            audioManager.abandonAudioFocus(mediaFocusListener)
        }
        hasMediaFocus = false
    }

    /**
     * Request short transient priority focus when VoiceGem speaks TTS or opens the microphone.
     * Preserves media playback focus intent so podcasts auto-resume right afterward.
     */
    fun requestVoicePriorityFocus(): Boolean {
        coordinator.onVoiceTurnStarted()
        val granted = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            val playbackAttributes = AudioAttributes.Builder()
                .setUsage(AudioAttributes.USAGE_ASSISTANT)
                .setContentType(AudioAttributes.CONTENT_TYPE_SPEECH)
                .build()

            val request = AudioFocusRequest.Builder(AudioManager.AUDIOFOCUS_GAIN_TRANSIENT_MAY_DUCK)
                .setAudioAttributes(playbackAttributes)
                .setAcceptsDelayedFocusGain(false)
                .setOnAudioFocusChangeListener(voiceFocusListener)
                .build()

            voiceFocusRequest = request
            audioManager.requestAudioFocus(request) == AudioManager.AUDIOFOCUS_REQUEST_GRANTED
        } else {
            @Suppress("DEPRECATION")
            audioManager.requestAudioFocus(
                voiceFocusListener,
                AudioManager.STREAM_MUSIC,
                AudioManager.AUDIOFOCUS_GAIN_TRANSIENT_MAY_DUCK
            ) == AudioManager.AUDIOFOCUS_REQUEST_GRANTED
        }
        hasVoiceFocus = granted
        return granted
    }

    /**
     * Releases assistant voice focus and reliably restores any active podcast stream.
     */
    fun abandonVoiceFocus() {
        if (hasVoiceFocus) {
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
                voiceFocusRequest?.let { audioManager.abandonAudioFocusRequest(it) }
            } else {
                @Suppress("DEPRECATION")
                audioManager.abandonAudioFocus(voiceFocusListener)
            }
            hasVoiceFocus = false
        }

        when (coordinator.onVoiceTurnEnded()) {
            MediaFocusCoordinator.FocusAction.RESUME_AND_UNDUCK -> {
                Log.d("VoiceAudioFocus", "Voice turn ended -> re-acquiring media focus & resuming")
                requestMediaPlaybackFocus()
                onDuckChange(false)
                onResumeRequested()
            }
            MediaFocusCoordinator.FocusAction.UNDUCK -> {
                onDuckChange(false)
            }
            else -> {
                onDuckChange(false)
            }
        }
    }

    fun abandonAll() {
        coordinator.onMediaStopped()
        abandonVoiceFocus()
        abandonMediaPlaybackFocus()
    }
}
