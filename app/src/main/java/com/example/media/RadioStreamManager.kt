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

class RadioStreamManager(
    context: Context,
    private val podcastPreferences: PodcastPreferences? = null
) {

    private val appContext = context.applicationContext
    private var mediaPlayer: MediaPlayer? = null
    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.Main)
    private var playbackJob: Job? = null
    private var reconnectJob: Job? = null
    private var loadTimeoutJob: Job? = null
    private var progressJob: Job? = null
    private var sleepTimerJob: Job? = null

    private var isPrepared = false
    private var hasPlayerError = false
    private var shouldBePlaying = false
    private var reconnectAttempts = 0
    private var activeEpisodeAudioUrl: String = ""

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

    private val _episodes = MutableStateFlow<List<PodcastEpisode>>(emptyList())
    val episodes: StateFlow<List<PodcastEpisode>> = _episodes.asStateFlow()

    private val _currentEpisodeIndex = MutableStateFlow(0)
    val currentEpisodeIndex: StateFlow<Int> = _currentEpisodeIndex.asStateFlow()

    private val _currentEpisodeTitle = MutableStateFlow<String?>(null)
    val currentEpisodeTitle: StateFlow<String?> = _currentEpisodeTitle.asStateFlow()

    private val _currentPositionMs = MutableStateFlow(0L)
    val currentPositionMs: StateFlow<Long> = _currentPositionMs.asStateFlow()

    private val _durationMs = MutableStateFlow(0L)
    val durationMs: StateFlow<Long> = _durationMs.asStateFlow()

    private val _sleepTimerRemainingSec = MutableStateFlow<Int?>(null)
    val sleepTimerRemainingSec: StateFlow<Int?> = _sleepTimerRemainingSec.asStateFlow()

    private val _sleepAtEndOfEpisode = MutableStateFlow(false)
    val sleepAtEndOfEpisode: StateFlow<Boolean> = _sleepAtEndOfEpisode.asStateFlow()

    fun playStation(
        station: RadioStation,
        preserveRetryCount: Boolean = false,
        initialEpisodeIndex: Int = 0
    ) {
        cancelPlaybackJobs()
        saveCurrentPositionImmediate()
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
        _currentPositionMs.value = 0L
        _durationMs.value = 0L
        _streamStatusDetail.value = if (preserveRetryCount) "Reconnecting stream…" else "Loading stream…"

        syncForegroundService(isPlaying = true, subtitle = _streamStatusDetail.value ?: "Loading stream…")

        playbackJob = scope.launch {
            val resolvedEpisodes = RssPodcastResolver.resolveFeedEpisodes(station.streamUrl, maxEpisodes = 8)
            if (resolvedEpisodes.isEmpty()) {
                handleStreamFailure(station, "Blocked invalid or non-HTTPS URL", allowReconnect = false)
                return@launch
            }
            _episodes.value = resolvedEpisodes
            val safeIndex = initialEpisodeIndex.coerceIn(0, resolvedEpisodes.size - 1)
            startEpisodeInternal(station, resolvedEpisodes, safeIndex, preserveRetryCount)
        }
    }

    fun selectEpisode(episodeIndex: Int) {
        val station = _currentStation.value ?: return
        val list = _episodes.value
        if (episodeIndex !in list.indices) return

        cancelPlaybackJobs()
        saveCurrentPositionImmediate()
        releasePlayerOnly()

        reconnectAttempts = 0
        shouldBePlaying = true
        isPrepared = false
        hasPlayerError = false
        _isPlaying.value = false
        _isLoading.value = true
        _currentPositionMs.value = 0L
        _durationMs.value = 0L

        playbackJob = scope.launch {
            startEpisodeInternal(station, list, episodeIndex, preserveRetryCount = false)
        }
    }

    fun nextEpisode(): Boolean {
        val list = _episodes.value
        val nextIdx = _currentEpisodeIndex.value + 1
        if (nextIdx in list.indices) {
            selectEpisode(nextIdx)
            return true
        }
        return false
    }

    fun previousEpisode(): Boolean {
        val list = _episodes.value
        val prevIdx = _currentEpisodeIndex.value - 1
        if (prevIdx in list.indices) {
            selectEpisode(prevIdx)
            return true
        }
        return false
    }

    private fun startEpisodeInternal(
        station: RadioStation,
        episodeList: List<PodcastEpisode>,
        episodeIndex: Int,
        preserveRetryCount: Boolean
    ) {
        val episode = episodeList[episodeIndex]
        _currentEpisodeIndex.value = episodeIndex
        val displayEpisode = if (episodeList.size > 1) episode.title else null
        _currentEpisodeTitle.value = displayEpisode
        activeEpisodeAudioUrl = episode.audioUrl

        _streamStatusDetail.value = if (preserveRetryCount) {
            "Reconnecting stream…"
        } else if (displayEpisode != null) {
            "Loading: $displayEpisode"
        } else {
            "Loading stream…"
        }

        loadTimeoutJob?.cancel()
        loadTimeoutJob = scope.launch {
            delay(20_000L)
            if (_isLoading.value && !isPrepared) {
                Log.w("RadioStreamManager", "Stream prepare timed out for ${station.name}")
                handleStreamFailure(station, "Stream timed out", allowReconnect = true)
            }
        }

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
                        Uri.parse(episode.audioUrl),
                        mapOf("User-Agent" to "Dalvik/2.1.0 (Linux; U; Android) VoiceGemBrowser/${BuildConfig.VERSION_NAME}")
                    )
                } catch (_: Exception) {
                    setDataSource(episode.audioUrl)
                }
                setOnPreparedListener { mp ->
                    loadTimeoutJob?.cancel()
                    isPrepared = true
                    hasPlayerError = false
                    reconnectAttempts = 0
                    _isLoading.value = false

                    val totalDur = try {
                        mp.duration.toLong().coerceAtLeast(0L)
                    } catch (_: Exception) {
                        0L
                    }
                    _durationMs.value = totalDur

                    val savedPos = podcastPreferences?.getSavedPlaybackPosition(station.id, episode.audioUrl) ?: 0L
                    if (savedPos > 3_000L && (totalDur <= 0L || savedPos < totalDur - 8_000L)) {
                        try {
                            mp.seekTo(savedPos.toInt())
                            _currentPositionMs.value = savedPos
                        } catch (_: Exception) {
                        }
                    }

                    if (shouldBePlaying) {
                        val vol = if (_isDucked.value) 0.15f else 1.0f
                        try {
                            mp.setVolume(vol, vol)
                        } catch (_: Exception) {
                        }
                        mp.start()
                        _isPlaying.value = true
                        _streamStatusDetail.value = null
                        startProgressTicker()
                        val subtitle = displayEpisode ?: "Streaming ${station.name}"
                        syncForegroundService(isPlaying = true, subtitle = subtitle)
                        Log.i("RadioStreamManager", "Started stream: ${station.name}")
                    } else {
                        _isPlaying.value = false
                        syncForegroundService(isPlaying = false, subtitle = "Paused")
                    }
                }
                setOnErrorListener { _, what, extra ->
                    Log.w("RadioStreamManager", "MediaPlayer error what=$what extra=$extra")
                    loadTimeoutJob?.cancel()
                    handleStreamFailure(station, "Connection interrupted ($what)", allowReconnect = true)
                    true
                }
                setOnCompletionListener {
                    Log.i("RadioStreamManager", "Playback completed for ${station.name}")
                    stopProgressTicker()
                    podcastPreferences?.clearPlaybackPosition(station.id, activeEpisodeAudioUrl)
                    isPrepared = false
                    hasPlayerError = false
                    _isPlaying.value = false
                    _currentPositionMs.value = 0L

                    if (_sleepAtEndOfEpisode.value) {
                        _sleepAtEndOfEpisode.value = false
                        shouldBePlaying = false
                        _streamStatusDetail.value = "Sleep timer stopped playback at end of episode"
                        syncForegroundService(isPlaying = false, subtitle = "Sleep timer finished")
                    } else if (_currentEpisodeIndex.value + 1 < _episodes.value.size) {
                        val nextIndex = _currentEpisodeIndex.value + 1
                        selectEpisode(nextIndex)
                    } else {
                        shouldBePlaying = false
                        _streamStatusDetail.value = "Playback finished — tap ▶ to replay"
                        syncForegroundService(isPlaying = false, subtitle = "Playback finished")
                    }
                }
                prepareAsync()
            }
            mediaPlayer = player
        } catch (e: Exception) {
            Log.e("RadioStreamManager", "Failed to prepare radio stream", e)
            loadTimeoutJob?.cancel()
            handleStreamFailure(station, "Unable to open stream", allowReconnect = true)
        }
    }

    private fun handleStreamFailure(station: RadioStation, reason: String, allowReconnect: Boolean) {
        stopProgressTicker()
        saveCurrentPositionImmediate()
        hasPlayerError = true
        isPrepared = false
        _isLoading.value = false
        _isPlaying.value = false
        releasePlayerOnly()

        if (allowReconnect && StreamRecoveryPolicy.shouldReconnectAfterError(shouldBePlaying, reconnectAttempts)) {
            val delayMs = StreamRecoveryPolicy.nextRetryDelayMs(reconnectAttempts)
            reconnectAttempts++
            _streamStatusDetail.value = "Signal lost — reconnecting ($reconnectAttempts/${StreamRecoveryPolicy.MAX_AUTO_RECONNECT_ATTEMPTS})…"
            syncForegroundService(isPlaying = true, subtitle = _streamStatusDetail.value ?: "Reconnecting…")

            reconnectJob?.cancel()
            reconnectJob = scope.launch {
                delay(delayMs)
                if (shouldBePlaying && _currentStation.value?.id == station.id) {
                    playStation(
                        station = station,
                        preserveRetryCount = true,
                        initialEpisodeIndex = _currentEpisodeIndex.value
                    )
                }
            }
        } else {
            shouldBePlaying = false
            _streamStatusDetail.value = "$reason — tap ▶ to retry"
            syncForegroundService(isPlaying = false, subtitle = "Paused (tap Play to retry)")
        }
    }

    fun seekBy(deltaMs: Long) {
        val player = mediaPlayer ?: return
        if (!isPrepared || hasPlayerError) return
        try {
            val current = player.currentPosition.toLong().coerceAtLeast(0L)
            val dur = player.duration.toLong().coerceAtLeast(0L)
            val target = if (dur > 0L) {
                (current + deltaMs).coerceIn(0L, (dur - 500L).coerceAtLeast(0L))
            } else {
                (current + deltaMs).coerceAtLeast(0L)
            }
            seekTo(target)
        } catch (e: Exception) {
            Log.w("RadioStreamManager", "Error seeking by delta", e)
        }
    }

    fun seekTo(targetMs: Long) {
        val player = mediaPlayer ?: return
        if (!isPrepared || hasPlayerError) return
        try {
            val dur = player.duration.toLong().coerceAtLeast(0L)
            val clamped = if (dur > 0L) {
                targetMs.coerceIn(0L, (dur - 500L).coerceAtLeast(0L))
            } else {
                targetMs.coerceAtLeast(0L)
            }
            player.seekTo(clamped.toInt())
            _currentPositionMs.value = clamped
            _currentStation.value?.let { station ->
                podcastPreferences?.savePlaybackPosition(station.id, activeEpisodeAudioUrl, clamped)
            }
            syncForegroundService(
                isPlaying = _isPlaying.value,
                subtitle = _currentEpisodeTitle.value ?: _currentStation.value?.name ?: "Streaming"
            )
        } catch (e: Exception) {
            Log.w("RadioStreamManager", "Error seeking to position", e)
        }
    }

    fun setSleepTimerMinutes(minutes: Int) {
        if (minutes <= 0) {
            cancelSleepTimer()
            return
        }
        sleepTimerJob?.cancel()
        _sleepAtEndOfEpisode.value = false
        var remaining = minutes * 60
        _sleepTimerRemainingSec.value = remaining
        sleepTimerJob = scope.launch {
            while (remaining > 0) {
                delay(1_000L)
                remaining--
                _sleepTimerRemainingSec.value = remaining
            }
            _sleepTimerRemainingSec.value = null
            if (_isPlaying.value) {
                pause()
                _streamStatusDetail.value = "Sleep timer paused playback"
            }
        }
    }

    fun setSleepAtEndOfEpisode() {
        sleepTimerJob?.cancel()
        sleepTimerJob = null
        _sleepTimerRemainingSec.value = null
        _sleepAtEndOfEpisode.value = true
    }

    fun cancelSleepTimer() {
        sleepTimerJob?.cancel()
        sleepTimerJob = null
        _sleepTimerRemainingSec.value = null
        _sleepAtEndOfEpisode.value = false
    }

    fun pause() {
        shouldBePlaying = false
        reconnectJob?.cancel()
        reconnectJob = null
        stopProgressTicker()
        saveCurrentPositionImmediate()
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
                startProgressTicker()
                val subtitle = _currentEpisodeTitle.value ?: "Streaming ${station.name}"
                syncForegroundService(isPlaying = true, subtitle = subtitle)
                return
            } catch (e: Exception) {
                Log.w("RadioStreamManager", "MediaPlayer resume failed; restarting stream", e)
                hasPlayerError = true
            }
        }

        playStation(station, initialEpisodeIndex = _currentEpisodeIndex.value)
    }

    fun stop() {
        shouldBePlaying = false
        cancelPlaybackJobs()
        cancelSleepTimer()
        saveCurrentPositionImmediate()

        releasePlayerOnly()
        isPrepared = false
        hasPlayerError = false
        reconnectAttempts = 0
        activeEpisodeAudioUrl = ""
        _isPlaying.value = false
        _isLoading.value = false
        _isDucked.value = false
        _streamStatusDetail.value = null
        _currentStation.value = null
        _episodes.value = emptyList()
        _currentEpisodeIndex.value = 0
        _currentEpisodeTitle.value = null
        _currentPositionMs.value = 0L
        _durationMs.value = 0L
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

    private fun startProgressTicker() {
        progressJob?.cancel()
        progressJob = scope.launch {
            var tickCount = 0
            while (true) {
                delay(1_000L)
                val player = mediaPlayer
                if (player != null && isPrepared && !hasPlayerError && _isPlaying.value) {
                    try {
                        val pos = player.currentPosition.toLong().coerceAtLeast(0L)
                        val dur = player.duration.toLong().coerceAtLeast(0L)
                        _currentPositionMs.value = pos
                        _durationMs.value = dur
                        tickCount++
                        if (tickCount % 5 == 0) {
                            _currentStation.value?.let { station ->
                                podcastPreferences?.savePlaybackPosition(station.id, activeEpisodeAudioUrl, pos)
                            }
                        }
                    } catch (_: Exception) {
                    }
                }
            }
        }
    }

    private fun stopProgressTicker() {
        progressJob?.cancel()
        progressJob = null
    }

    private fun saveCurrentPositionImmediate() {
        val station = _currentStation.value ?: return
        if (activeEpisodeAudioUrl.isBlank()) return
        val pos = try {
            if (isPrepared && !hasPlayerError) {
                mediaPlayer?.currentPosition?.toLong()?.coerceAtLeast(0L) ?: _currentPositionMs.value
            } else {
                _currentPositionMs.value
            }
        } catch (_: Exception) {
            _currentPositionMs.value
        }
        podcastPreferences?.savePlaybackPosition(station.id, activeEpisodeAudioUrl, pos)
    }

    private fun cancelPlaybackJobs() {
        reconnectJob?.cancel()
        reconnectJob = null
        loadTimeoutJob?.cancel()
        loadTimeoutJob = null
        playbackJob?.cancel()
        playbackJob = null
        stopProgressTicker()
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
            title = _currentEpisodeTitle.value ?: station.name,
            subtitle = if (_currentEpisodeTitle.value != null) station.name else subtitle,
            album = station.genre,
            isPlaying = isPlaying,
            positionMs = _currentPositionMs.value,
            durationMs = _durationMs.value
        )
    }

    fun release() {
        stop()
        scope.cancel()
    }
}
