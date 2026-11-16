package com.example.media

import android.app.Notification
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.app.Service
import android.content.Context
import android.content.Intent
import android.content.pm.ServiceInfo
import android.os.Build
import android.os.IBinder
import android.os.PowerManager
import android.util.Log
import androidx.core.app.NotificationCompat
import androidx.core.content.ContextCompat
import com.example.MainActivity
import com.example.R

/**
 * Lightweight bridge allowing notification media actions (Play/Pause/Stop)
 * to control the active RadioStreamManager / ViewModel without coupling to a Binder.
 */
object RadioServiceBridge {
    @Volatile
    var onTogglePlayPause: (() -> Unit)? = null

    @Volatile
    var onStopPlayback: (() -> Unit)? = null
}

/**
 * mediaPlayback Foreground Service keeping Bluetooth & cellular podcast streaming alive
 * reliably when the phone screen goes to sleep or VoiceGem is backgrounded behind Maps.
 */
class RadioPlaybackService : Service() {

    private var wakeLock: PowerManager.WakeLock? = null

    override fun onBind(intent: Intent?): IBinder? = null

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        when (intent?.action) {
            ACTION_TOGGLE_PLAY_PAUSE -> {
                RadioServiceBridge.onTogglePlayPause?.invoke()
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
                val isPlaying = intent?.getBooleanExtra(EXTRA_IS_PLAYING, true) ?: true

                ensureChannel()
                val notification = buildNotification(title, subtitle, isPlaying)

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

    override fun onTaskRemoved(rootIntent: Intent?) {
        // Keep playing if active playback is running, or release cleanly on explicit stop
        super.onTaskRemoved(rootIntent)
    }

    override fun onDestroy() {
        releaseWakeLock()
        super.onDestroy()
    }

    private fun stopSelfCleanly() {
        releaseWakeLock()
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
                // Bounded safety timeout (4 hours) renewed whenever stream updates
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
                    description = "Keeps radio and podcast audio streaming over Bluetooth when the screen is off."
                    setShowBadge(false)
                }
                nm.createNotificationChannel(channel)
            }
        }
    }

    private fun buildNotification(title: String, subtitle: String, isPlaying: Boolean): Notification {
        val openAppIntent = PendingIntent.getActivity(
            this,
            0,
            Intent(this, MainActivity::class.java).apply {
                flags = Intent.FLAG_ACTIVITY_SINGLE_TOP or Intent.FLAG_ACTIVITY_CLEAR_TOP
            },
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        )

        val toggleIntent = PendingIntent.getService(
            this,
            1,
            Intent(this, RadioPlaybackService::class.java).apply {
                action = ACTION_TOGGLE_PLAY_PAUSE
            },
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        )

        val stopIntent = PendingIntent.getService(
            this,
            2,
            Intent(this, RadioPlaybackService::class.java).apply {
                action = ACTION_STOP_SERVICE
            },
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        )

        val toggleLabel = if (isPlaying) "Pause" else "Play"
        val toggleIcon = if (isPlaying) android.R.drawable.ic_media_pause else android.R.drawable.ic_media_play

        return NotificationCompat.Builder(this, CHANNEL_ID)
            .setContentTitle(title)
            .setContentText(subtitle)
            .setSmallIcon(R.mipmap.ic_launcher)
            .setContentIntent(openAppIntent)
            .setOngoing(isPlaying)
            .setOnlyAlertOnce(true)
            .setVisibility(NotificationCompat.VISIBILITY_PUBLIC)
            .addAction(toggleIcon, toggleLabel, toggleIntent)
            .addAction(android.R.drawable.ic_menu_close_clear_cancel, "Stop", stopIntent)
            .build()
    }

    companion object {
        private const val CHANNEL_ID = "voicegem_media_playback"
        private const val NOTIFICATION_ID = 10042

        const val ACTION_START_OR_UPDATE = "com.example.media.ACTION_START_OR_UPDATE"
        const val ACTION_TOGGLE_PLAY_PAUSE = "com.example.media.ACTION_TOGGLE_PLAY_PAUSE"
        const val ACTION_STOP_SERVICE = "com.example.media.ACTION_STOP_SERVICE"
        const val ACTION_STOP_NOTIFICATION_ONLY = "com.example.media.ACTION_STOP_NOTIFICATION_ONLY"

        private const val EXTRA_TITLE = "extra_title"
        private const val EXTRA_SUBTITLE = "extra_subtitle"
        private const val EXTRA_IS_PLAYING = "extra_is_playing"

        fun startOrUpdate(context: Context, title: String, subtitle: String, isPlaying: Boolean) {
            val intent = Intent(context, RadioPlaybackService::class.java).apply {
                action = ACTION_START_OR_UPDATE
                putExtra(EXTRA_TITLE, title)
                putExtra(EXTRA_SUBTITLE, subtitle)
                putExtra(EXTRA_IS_PLAYING, isPlaying)
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
