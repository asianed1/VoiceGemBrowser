package com.example.media

import android.app.Notification
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.app.Service
import android.content.Context
import android.content.Intent
import android.content.pm.ServiceInfo
import android.graphics.drawable.Icon
import android.media.MediaMetadata
import android.media.session.MediaSession
import android.media.session.PlaybackState
import android.os.Build
import android.os.IBinder
import android.os.PowerManager
import android.util.Log
import androidx.core.content.ContextCompat
import com.example.MainActivity
import com.example.R

/**
 * Lightweight bridge allowing notification buttons & Bluetooth/Car Stereo MediaSession controls
 * to drive the active RadioStreamManager / ViewModel without coupling to a Binder.
 */
object RadioServiceBridge {
    @Volatile
    var onTogglePlayPause: (() -> Unit)? = null

    @Volatile
    var onPlay: (() -> Unit)? = null

    @Volatile
    var onPause: (() -> Unit)? = null

    @Volatile
    var onStopPlayback: (() -> Unit)? = null

    @Volatile
    var onSkipBackward: (() -> Unit)? = null

    @Volatile
    var onSkipForward: (() -> Unit)? = null

    @Volatile
    var onNextEpisode: (() -> Unit)? = null

    @Volatile
    var onPreviousEpisode: (() -> Unit)? = null

    @Volatile
    var onSeekTo: ((Long) -> Unit)? = null
}

/**
 * mediaPlayback Foreground Service keeping Bluetooth & cellular podcast streaming alive,
 * exposing native MediaSession metadata + steering-wheel / car display transport controls.
 */
class RadioPlaybackService : Service() {

    private var wakeLock: PowerManager.WakeLock? = null
    private var mediaSession: MediaSession? = null

    override fun onBind(intent: Intent?): IBinder? = null

    override fun onCreate() {
        super.onCreate()
        initMediaSession()
    }

    private fun initMediaSession() {
        if (mediaSession != null) return
        mediaSession = MediaSession(this, "VoiceGemMediaSession").apply {
            if (Build.VERSION.SDK_INT < Build.VERSION_CODES.O) {
                @Suppress("DEPRECATION")
                setFlags(
                    MediaSession.FLAG_HANDLES_MEDIA_BUTTONS or
                        MediaSession.FLAG_HANDLES_TRANSPORT_CONTROLS
                )
            }
            setCallback(object : MediaSession.Callback() {
                override fun onPlay() {
                    RadioServiceBridge.onPlay?.invoke() ?: RadioServiceBridge.onTogglePlayPause?.invoke()
                }

                override fun onPause() {
                    RadioServiceBridge.onPause?.invoke() ?: RadioServiceBridge.onTogglePlayPause?.invoke()
                }

                override fun onStop() {
                    RadioServiceBridge.onStopPlayback?.invoke()
                }

                override fun onRewind() {
                    RadioServiceBridge.onSkipBackward?.invoke()
                }

                override fun onFastForward() {
                    RadioServiceBridge.onSkipForward?.invoke()
                }

                override fun onSkipToNext() {
                    RadioServiceBridge.onNextEpisode?.invoke()
                }

                override fun onSkipToPrevious() {
                    RadioServiceBridge.onPreviousEpisode?.invoke()
                }

                override fun onSeekTo(pos: Long) {
                    RadioServiceBridge.onSeekTo?.invoke(pos)
                }
            })
            isActive = true
        }
    }

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        when (intent?.action) {
            ACTION_TOGGLE_PLAY_PAUSE -> {
                RadioServiceBridge.onTogglePlayPause?.invoke()
                return START_NOT_STICKY
            }
            ACTION_REWIND_15 -> {
                RadioServiceBridge.onSkipBackward?.invoke()
                return START_NOT_STICKY
            }
            ACTION_FORWARD_30 -> {
                RadioServiceBridge.onSkipForward?.invoke()
                return START_NOT_STICKY
            }
            ACTION_STOP_SERVICE -> {
                RadioServiceBridge.onStopPlayback?.invoke()
                stopSelfCleanly()
                return START_NOT_STICKY
            }
            ACTION_STOP_NOTIFICATION_ONLY -> {
                stopSelfCleanly()
                return START_NOT_STICKY
            }
            else -> {
                val title = intent?.getStringExtra(EXTRA_TITLE) ?: "VoiceGem Radio"
                val subtitle = intent?.getStringExtra(EXTRA_SUBTITLE) ?: "Streaming audio"
                val album = intent?.getStringExtra(EXTRA_ALBUM) ?: "VoiceGem Podcasts & Radio"
                val isPlaying = intent?.getBooleanExtra(EXTRA_IS_PLAYING, true) ?: true
                val positionMs = intent?.getLongExtra(EXTRA_POSITION_MS, 0L) ?: 0L
                val durationMs = intent?.getLongExtra(EXTRA_DURATION_MS, 0L) ?: 0L

                initMediaSession()
                updateMediaSessionMetadataAndState(
                    title = title,
                    artist = subtitle,
                    album = album,
                    isPlaying = isPlaying,
                    positionMs = positionMs,
                    durationMs = durationMs
                )

                ensureChannel()
                val notification = buildMediaNotification(title, subtitle, isPlaying)

                try {
                    if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
                        startForeground(
                            NOTIFICATION_ID,
                            notification,
                            ServiceInfo.FOREGROUND_SERVICE_TYPE_MEDIA_PLAYBACK
                        )
                    } else {
                        startForeground(NOTIFICATION_ID, notification)
                    }
                } catch (e: Exception) {
                    Log.e("RadioPlaybackService", "Failed to enter foreground", e)
                }

                if (isPlaying) {
                    acquireWakeLock()
                } else {
                    releaseWakeLock()
                }
            }
        }
        return START_STICKY
    }

    private fun updateMediaSessionMetadataAndState(
        title: String,
        artist: String,
        album: String,
        isPlaying: Boolean,
        positionMs: Long,
        durationMs: Long
    ) {
        val session = mediaSession ?: return
        val metadataBuilder = MediaMetadata.Builder()
            .putString(MediaMetadata.METADATA_KEY_TITLE, title)
            .putString(MediaMetadata.METADATA_KEY_ARTIST, artist)
            .putString(MediaMetadata.METADATA_KEY_ALBUM, album)
        if (durationMs > 0L) {
            metadataBuilder.putLong(MediaMetadata.METADATA_KEY_DURATION, durationMs)
        }
        session.setMetadata(metadataBuilder.build())

        val actions = PlaybackState.ACTION_PLAY or
            PlaybackState.ACTION_PAUSE or
            PlaybackState.ACTION_PLAY_PAUSE or
            PlaybackState.ACTION_STOP or
            PlaybackState.ACTION_REWIND or
            PlaybackState.ACTION_FAST_FORWARD or
            PlaybackState.ACTION_SKIP_TO_NEXT or
            PlaybackState.ACTION_SKIP_TO_PREVIOUS or
            PlaybackState.ACTION_SEEK_TO

        val stateCode = if (isPlaying) PlaybackState.STATE_PLAYING else PlaybackState.STATE_PAUSED
        val state = PlaybackState.Builder()
            .setActions(actions)
            .setState(stateCode, positionMs, if (isPlaying) 1.0f else 0.0f)
            .build()
        session.setPlaybackState(state)
        session.isActive = true
    }

    override fun onDestroy() {
        releaseWakeLock()
        try {
            mediaSession?.isActive = false
            mediaSession?.release()
        } catch (_: Exception) {
        }
        mediaSession = null
        super.onDestroy()
    }

    private fun stopSelfCleanly() {
        releaseWakeLock()
        try {
            mediaSession?.isActive = false
        } catch (_: Exception) {
        }
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.N) {
            stopForeground(STOP_FOREGROUND_REMOVE)
        } else {
            @Suppress("DEPRECATION")
            stopForeground(true)
        }
        stopSelf()
    }

    private fun acquireWakeLock() {
        try {
            if (wakeLock == null) {
                val pm = getSystemService(Context.POWER_SERVICE) as PowerManager
                wakeLock = pm.newWakeLock(
                    PowerManager.PARTIAL_WAKE_LOCK,
                    "VoiceGemBrowser::RadioMediaWakeLock"
                ).apply {
                    setReferenceCounted(false)
                }
            }
            if (wakeLock?.isHeld == false) {
                wakeLock?.acquire(4L * 60L * 60L * 1000L)
            }
        } catch (e: Exception) {
            Log.w("RadioPlaybackService", "Unable to acquire WakeLock", e)
        }
    }

    private fun releaseWakeLock() {
        try {
            if (wakeLock?.isHeld == true) {
                wakeLock?.release()
            }
        } catch (e: Exception) {
            Log.w("RadioPlaybackService", "Error releasing WakeLock", e)
        }
    }

    private fun ensureChannel() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            val nm = getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager
            val existing = nm.getNotificationChannel(CHANNEL_ID)
            if (existing == null) {
                val channel = NotificationChannel(
                    CHANNEL_ID,
                    "Podcast & Radio Playback",
                    NotificationManager.IMPORTANCE_LOW
                ).apply {
                    description = "Keeps radio and podcast audio streaming over Bluetooth with car stereo metadata."
                    setShowBadge(false)
                }
                nm.createNotificationChannel(channel)
            }
        }
    }

    private fun buildMediaNotification(title: String, subtitle: String, isPlaying: Boolean): Notification {
        val openAppIntent = PendingIntent.getActivity(
            this,
            0,
            Intent(this, MainActivity::class.java).apply {
                flags = Intent.FLAG_ACTIVITY_SINGLE_TOP or Intent.FLAG_ACTIVITY_CLEAR_TOP
            },
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        )

        val rewindIntent = PendingIntent.getService(
            this,
            3,
            Intent(this, RadioPlaybackService::class.java).apply { action = ACTION_REWIND_15 },
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        )

        val toggleIntent = PendingIntent.getService(
            this,
            1,
            Intent(this, RadioPlaybackService::class.java).apply { action = ACTION_TOGGLE_PLAY_PAUSE },
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        )

        val forwardIntent = PendingIntent.getService(
            this,
            4,
            Intent(this, RadioPlaybackService::class.java).apply { action = ACTION_FORWARD_30 },
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        )

        val stopIntent = PendingIntent.getService(
            this,
            2,
            Intent(this, RadioPlaybackService::class.java).apply { action = ACTION_STOP_SERVICE },
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        )

        val toggleLabel = if (isPlaying) "Pause" else "Play"
        val toggleIconRes = if (isPlaying) android.R.drawable.ic_media_pause else android.R.drawable.ic_media_play

        val builder = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            Notification.Builder(this, CHANNEL_ID)
        } else {
            @Suppress("DEPRECATION")
            Notification.Builder(this)
        }

        val rewindAction = Notification.Action.Builder(
            Icon.createWithResource(this, android.R.drawable.ic_media_rew),
            "-15s",
            rewindIntent
        ).build()

        val toggleAction = Notification.Action.Builder(
            Icon.createWithResource(this, toggleIconRes),
            toggleLabel,
            toggleIntent
        ).build()

        val forwardAction = Notification.Action.Builder(
            Icon.createWithResource(this, android.R.drawable.ic_media_ff),
            "+30s",
            forwardIntent
        ).build()

        val stopAction = Notification.Action.Builder(
            Icon.createWithResource(this, android.R.drawable.ic_menu_close_clear_cancel),
            "Stop",
            stopIntent
        ).build()

        val mediaStyle = Notification.MediaStyle()
            .setShowActionsInCompactView(0, 1, 2)
        mediaSession?.sessionToken?.let { token ->
            mediaStyle.setMediaSession(token)
        }

        return builder
            .setContentTitle(title)
            .setContentText(subtitle)
            .setSmallIcon(R.mipmap.ic_launcher)
            .setContentIntent(openAppIntent)
            .setOngoing(isPlaying)
            .setOnlyAlertOnce(true)
            .setVisibility(Notification.VISIBILITY_PUBLIC)
            .addAction(rewindAction)
            .addAction(toggleAction)
            .addAction(forwardAction)
            .addAction(stopAction)
            .setStyle(mediaStyle)
            .build()
    }

    companion object {
        private const val CHANNEL_ID = "voicegem_media_playback"
        private const val NOTIFICATION_ID = 10042

        const val ACTION_START_OR_UPDATE = "com.example.media.ACTION_START_OR_UPDATE"
        const val ACTION_TOGGLE_PLAY_PAUSE = "com.example.media.ACTION_TOGGLE_PLAY_PAUSE"
        const val ACTION_REWIND_15 = "com.example.media.ACTION_REWIND_15"
        const val ACTION_FORWARD_30 = "com.example.media.ACTION_FORWARD_30"
        const val ACTION_STOP_SERVICE = "com.example.media.ACTION_STOP_SERVICE"
        const val ACTION_STOP_NOTIFICATION_ONLY = "com.example.media.ACTION_STOP_NOTIFICATION_ONLY"

        private const val EXTRA_TITLE = "extra_title"
        private const val EXTRA_SUBTITLE = "extra_subtitle"
        private const val EXTRA_ALBUM = "extra_album"
        private const val EXTRA_IS_PLAYING = "extra_is_playing"
        private const val EXTRA_POSITION_MS = "extra_position_ms"
        private const val EXTRA_DURATION_MS = "extra_duration_ms"

        fun startOrUpdate(
            context: Context,
            title: String,
            subtitle: String,
            isPlaying: Boolean,
            album: String = "VoiceGem Podcasts & Radio",
            positionMs: Long = 0L,
            durationMs: Long = 0L
        ) {
            val intent = Intent(context, RadioPlaybackService::class.java).apply {
                action = ACTION_START_OR_UPDATE
                putExtra(EXTRA_TITLE, title)
                putExtra(EXTRA_SUBTITLE, subtitle)
                putExtra(EXTRA_ALBUM, album)
                putExtra(EXTRA_IS_PLAYING, isPlaying)
                putExtra(EXTRA_POSITION_MS, positionMs)
                putExtra(EXTRA_DURATION_MS, durationMs)
            }
            try {
                ContextCompat.startForegroundService(context, intent)
            } catch (e: Exception) {
                Log.w("RadioPlaybackService", "Unable to startForegroundService: ${e.message}")
            }
        }

        fun stop(context: Context) {
            val intent = Intent(context, RadioPlaybackService::class.java).apply {
                action = ACTION_STOP_NOTIFICATION_ONLY
            }
            try {
                context.startService(intent)
            } catch (_: Exception) {
                // Service wasn't running
            }
        }
    }
}
