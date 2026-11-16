package com.example.media

import android.content.Context
import android.media.AudioAttributes
import android.media.MediaPlayer
import android.util.Log
import android.net.Uri
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch

class RadioStreamManager(context: Context) {

    private val appContext = context.applicationContext
    private var mediaPlayer: MediaPlayer? = null
    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.Main)
    private var playbackJob: Job? = null

    private val _isPlaying = MutableStateFlow(false)
    val isPlaying: StateFlow<Boolean> = _isPlaying.asStateFlow()

    private val _isLoading = MutableStateFlow(false)
    val isLoading: StateFlow<Boolean> = _isLoading.asStateFlow()

    private val _currentStation = MutableStateFlow<RadioStation?>(null)
    val currentStation: StateFlow<RadioStation?> = _currentStation.asStateFlow()

    private val _isDucked = MutableStateFlow(false)
    val isDucked: StateFlow<Boolean> = _isDucked.asStateFlow()

    fun playStation(station: RadioStation) {
        stop()
        _isLoading.value = true
        _currentStation.value = station

        playbackJob?.cancel()
        playbackJob = scope.launch {
            // Automatically resolve XML RSS podcast feeds to their latest MP3/audio enclosure
            val audioUrl = RssPodcastResolver.resolveAudioUrl(station.streamUrl)

            try {
                mediaPlayer = MediaPlayer().apply {
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
                            mapOf("User-Agent" to "Dalvik/2.1.0 (Linux; U; Android) VoiceGemBrowser/9.0")
                        )
                    } catch (_: Exception) {
                        setDataSource(audioUrl)
                    }
                    setOnPreparedListener { mp ->
                        _isLoading.value = false
                        mp.start()
                        _isPlaying.value = true
                        Log.i("RadioStreamManager", "Started stream: ${station.name} -> $audioUrl")
                    }
                    setOnErrorListener { _, what, extra ->
                        Log.w("RadioStreamManager", "MediaPlayer error what: $what extra: $extra")
                        _isLoading.value = false
                        _isPlaying.value = false
                        true
                    }
                    prepareAsync()
                }
            } catch (e: Exception) {
                Log.e("RadioStreamManager", "Failed to stream radio station", e)
                _isLoading.value = false
                _isPlaying.value = false
            }
        }
    }

    fun pause() {
        try {
            if (mediaPlayer?.isPlaying == true) {
                mediaPlayer?.pause()
                _isPlaying.value = false
            }
        } catch (e: Exception) {
            Log.w("RadioStreamManager", "Error pausing", e)
        }
    }

    fun resume() {
        try {
            if (mediaPlayer != null && !_isPlaying.value) {
                mediaPlayer?.start()
                _isPlaying.value = true
            }
        } catch (e: Exception) {
            Log.w("RadioStreamManager", "Error resuming", e)
        }
    }

    fun stop() {
        playbackJob?.cancel()
        playbackJob = null
        try {
            mediaPlayer?.stop()
            mediaPlayer?.release()
            mediaPlayer = null
        } catch (e: Exception) {
            Log.w("RadioStreamManager", "Error stopping", e)
        } finally {
            _isPlaying.value = false
            _isLoading.value = false
        }
    }

    /**
     * Ducks audio volume to 15% when VoiceGem starts speaking/listening,
     * and restores volume to 100% when VoiceGem is done!
     */
    fun duck(shouldDuck: Boolean) {
        _isDucked.value = shouldDuck
        val volume = if (shouldDuck) 0.15f else 1.0f
        try {
            mediaPlayer?.setVolume(volume, volume)
        } catch (e: Exception) {
            Log.w("RadioStreamManager", "Error setting volume ducking", e)
        }
    }

    fun release() {
        stop()
        scope.cancel()
    }
}
