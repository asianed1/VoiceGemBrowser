package com.example.media

import android.content.Context
import android.media.AudioAttributes
import android.media.MediaPlayer
import android.net.Uri
import android.os.PowerManager
import android.util.Log
import com.example.BuildConfig
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch

/**
 * Pure recovery decision helper for cellular signal drops & MediaPlayer error states.
 */
object StreamRecoveryPolicy {
    const val MAX_AUTO_RECONNECT_ATTEMPTS = 3

    fun nextRetryDelayMs(attemptIndex: Int): Long = when (attemptIndex) {
        0 -> 2_000L
        1 -> 4_000L
        else -> 6_000L
    }

    fun shouldReconnectAfterError(shouldBePlaying: Boolean, attemptCount: Int): Boolean {
        return shouldBePlaying && attemptCount < MAX_AUTO_RECONNECT_ATTEMPTS
    }
}

class RadioStreamManager(context: Context) {

    private val appContext = context.applicationContext
    private var mediaPlayer: MediaPlayer? = null
    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.Main)
    private var playbackJob: Job? = null
    private var reconnectJob: Job? = null
    private var loadTimeoutJob: Job? = null

    private var isPrepared = false
    private var hasPlayerError = false
    private var shouldBePlaying = false
    private var reconnectAttempts = 0

    private val _isPlaying = MutableStateFlow(false)
    val isPlaying: StateFlow<Boolean> = _isPlaying.asStateFlow()

    private val _isLoading = MutableStateFlow(false)
    val isLoading: StateFlow<Boolean> = _isLoading.asStateFlow()

    private val _currentStation = MutableStateFlow<RadioStation?>(null)
    val currentStation: StateFlow<RadioStation?> = _currentStation.asStateFlow()

    private val _isDucked = MutableStateFlow(false)
    val isDucked: StateFlow<Boolean> = _isDucked.asStateFlow()

    private val _streamStatusDetail = MutableStateFlow<String?>(null)
    val streamStatusDetail: StateFlow<String?> = _streamStatusDetail.asStateFlow()

    fun playStation(station: RadioStation, preserveRetryCount: Boolean = false) {
        reconnectJob?.cancel()
        reconnectJob = null
        loadTimeoutJob?.cancel()
        loadTimeoutJob = null
        playbackJob?.cancel()
        playbackJob = null

        releasePlayerOnly()

        if (!preserveRetryCount) {
            reconnectAttempts = 0
        }
        shouldBePlaying = true
        isPrepared = false
        hasPlayerError = false
        _isPlaying.value = false
        _isLoading.value = true
        _currentStation.value = station
        _streamStatusDetail.value = if (preserveRetryCount) "Reconnecting stream…" else "Loading stream…"

        syncForegroundService(isPlaying = true, subtitle = _streamStatusDetail.value ?: "Loading stream…")

        loadTimeoutJob = scope.launch {
            delay(20_000L)
            if (_isLoading.value && !isPrepared) {
                Log.w("RadioStreamManager", "Stream prepare timed out for ${station.name}")
                handleStreamFailure(station, "Stream timed out")
            }
        }

        playbackJob = scope.launch {
            val audioUrl = RssPodcastResolver.resolveAudioUrl(station.streamUrl)
            try {
                val player = MediaPlayer().apply {
                    setWakeMode(appContext, PowerManager.PARTIAL_WAKE_LOCK)
                    setAudioAttributes(
                        AudioAttributes.Builder()
                            .setContentType(AudioAttributes.CONTENT_TYPE_MUSIC)
                            .setUsage(AudioAttributes.USAGE_MEDIA)
                            .build()
                    )
                    try {
                        setDataSource(
                            appContext,
                            Uri.parse(audioUrl),
                            mapOf("User-Agent" to "Dalvik/2.1.0 (Linux; U; Android) VoiceGemBrowser/${BuildConfig.VERSION_NAME}")
                        )
                    } catch (_: Exception) {
                        setDataSource(audioUrl)
                    }
                    setOnPreparedListener { mp ->
                        loadTimeoutJob?.cancel()
                        isPrepared = true
                        hasPlayerError = false
                        reconnectAttempts = 0
                        _isLoading.value = false
                        if (shouldBePlaying) {
                            val vol = if (_isDucked.value) 0.15f else 1.0f
                            try {
                                mp.setVolume(vol, vol)
                            } catch (_: Exception) {}
                            mp.start()
                            _isPlaying.value = true
                            _streamStatusDetail.value = null
                            syncForegroundService(isPlaying = true, subtitle = "Streaming ${station.name}")
                            Log.i("RadioStreamManager", "Started stream: ${station.name} -> $audioUrl")
                        } else {
                            _isPlaying.value = false
                            syncForegroundService(isPlaying = false, subtitle = "Paused")
                        }
                    }
                    setOnErrorListener { _, what, extra ->
                        Log.w("RadioStreamManager", "MediaPlayer error what=$what extra=$extra")
                        loadTimeoutJob?.cancel()
                        handleStreamFailure(station, "Connection interrupted ($what)")
                        true
                    }
                    setOnCompletionListener {
                        Log.i("RadioStreamManager", "Playback completed for ${station.name}")
                        isPrepared = false
                        hasPlayerError = false
                        _isPlaying.value = false
                        shouldBePlaying = false
                        _streamStatusDetail.value = "Playback finished — tap ▶ to replay"
                        syncForegroundService(isPlaying = false, subtitle = "Playback finished")
                    }
                    prepareAsync()
                }
                mediaPlayer = player
            } catch (e: Exception) {
                Log.e("RadioStreamManager", "Failed to prepare radio stream", e)
                loadTimeoutJob?.cancel()
                handleStreamFailure(station, "Unable to open stream")
            }
        }
    }

    private fun handleStreamFailure(station: RadioStation, reason: String) {
        hasPlayerError = true
        isPrepared = false
        _isLoading.value = false
        _isPlaying.value = false
        releasePlayerOnly()

        if (StreamRecoveryPolicy.shouldReconnectAfterError(shouldBePlaying, reconnectAttempts)) {
            val delayMs = StreamRecoveryPolicy.nextRetryDelayMs(reconnectAttempts)
            reconnectAttempts++
            _streamStatusDetail.value = "Signal lost — reconnecting ($reconnectAttempts/${StreamRecoveryPolicy.MAX_AUTO_RECONNECT_ATTEMPTS})…"
            syncForegroundService(isPlaying = true, subtitle = _streamStatusDetail.value ?: "Reconnecting…")

            reconnectJob?.cancel()
            reconnectJob = scope.launch {
                delay(delayMs)
                if (shouldBePlaying && _currentStation.value?.id == station.id) {
                    playStation(station, preserveRetryCount = true)
                }
            }
        } else {
            shouldBePlaying = false
            _streamStatusDetail.value = "$reason — tap ▶ to retry"
            syncForegroundService(isPlaying = false, subtitle = "Paused (tap Play to retry)")
        }
    }

    fun pause() {
        shouldBePlaying = false
        reconnectJob?.cancel()
        reconnectJob = null
        try {
            if (mediaPlayer?.isPlaying == true) {
                mediaPlayer?.pause()
            }
        } catch (e: Exception) {
            Log.w("RadioStreamManager", "Error pausing", e)
            hasPlayerError = true
        } finally {
            _isPlaying.value = false
            _isLoading.value = false
            if (_currentStation.value != null) {
                _streamStatusDetail.value = "Paused"
                syncForegroundService(isPlaying = false, subtitle = "Paused")
            }
        }
    }

    fun resume() {
        val station = _currentStation.value ?: return
        shouldBePlaying = true

        val player = mediaPlayer
        if (player != null && isPrepared && !hasPlayerError) {
            try {
                if (!player.isPlaying) {
                    val vol = if (_isDucked.value) 0.15f else 1.0f
                    player.setVolume(vol, vol)
                    player.start()
                }
                _isPlaying.value = true
                _streamStatusDetail.value = null
                syncForegroundService(isPlaying = true, subtitle = "Streaming ${station.name}")
                return
            } catch (e: Exception) {
                Log.w("RadioStreamManager", "MediaPlayer resume failed; restarting stream", e)
                hasPlayerError = true
            }
        }

        // Player was released, completed, or entered an error state (e.g. cellular drop) -> cleanly restart stream
        playStation(station)
    }

    fun stop() {
        shouldBePlaying = false
        reconnectJob?.cancel()
        reconnectJob = null
        loadTimeoutJob?.cancel()
        loadTimeoutJob = null
        playbackJob?.cancel()
        playbackJob = null

        releasePlayerOnly()
        isPrepared = false
        hasPlayerError = false
        reconnectAttempts = 0
        _isPlaying.value = false
        _isLoading.value = false
        _isDucked.value = false
        _streamStatusDetail.value = null
        _currentStation.value = null
        RadioPlaybackService.stop(appContext)
    }

    /**
     * Ducks audio volume to 15% when VoiceGem speaks/listens,
     * and restores volume to 100% when VoiceGem is done.
     */
    fun duck(shouldDuck: Boolean) {
        _isDucked.value = shouldDuck
        val volume = if (shouldDuck) 0.15f else 1.0f
        try {
            if (isPrepared && !hasPlayerError) {
                mediaPlayer?.setVolume(volume, volume)
            }
        } catch (e: Exception) {
            Log.w("RadioStreamManager", "Error setting volume ducking", e)
        }
    }

    private fun releasePlayerOnly() {
        val oldPlayer = mediaPlayer
        mediaPlayer = null
        isPrepared = false
        if (oldPlayer != null) {
            scope.launch(Dispatchers.IO) {
                try {
                    if (oldPlayer.isPlaying) {
                        oldPlayer.stop()
                    }
                } catch (_: Exception) {
                }
                try {
                    oldPlayer.reset()
                } catch (_: Exception) {
                }
                try {
                    oldPlayer.release()
                } catch (_: Exception) {
                }
            }
        }
    }

    private fun syncForegroundService(isPlaying: Boolean, subtitle: String) {
        val station = _currentStation.value ?: return
        RadioPlaybackService.startOrUpdate(
            context = appContext,
            title = station.name,
            subtitle = subtitle,
            isPlaying = isPlaying
        )
    }

    fun release() {
        stop()
        scope.cancel()
    }
}
