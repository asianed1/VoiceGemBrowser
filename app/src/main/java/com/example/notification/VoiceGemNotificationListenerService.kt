package com.example.notification

import android.app.Notification
import android.service.notification.NotificationListenerService
import android.service.notification.StatusBarNotification
import android.util.Log

class VoiceGemNotificationListenerService : NotificationListenerService() {

    override fun onNotificationPosted(sbn: StatusBarNotification?) {
        super.onNotificationPosted(sbn)
        if (sbn == null) return

        val packageName = sbn.packageName ?: return
        val extras = sbn.notification?.extras ?: return

        // Known messaging & communication apps
        val isTargetApp = when {
            packageName.contains("whatsapp", ignoreCase = true) -> true
            packageName.contains("telegram", ignoreCase = true) -> true
            packageName.contains("gm", ignoreCase = true) || packageName.contains("email", ignoreCase = true) -> true
            packageName.contains("viber", ignoreCase = true) -> true
            packageName.contains("messaging", ignoreCase = true) || packageName.contains("mms", ignoreCase = true) -> true
            packageName.contains("signal", ignoreCase = true) -> true
            packageName.contains("discord", ignoreCase = true) -> true
            packageName.contains("slack", ignoreCase = true) -> true
            sbn.notification.category == Notification.CATEGORY_MESSAGE -> true
            sbn.notification.category == Notification.CATEGORY_EMAIL -> true
            else -> false
        }

        if (!isTargetApp) return

        val title = extras.getString(Notification.EXTRA_TITLE) 
            ?: extras.getCharSequence(Notification.EXTRA_TITLE)?.toString() 
            ?: "Someone"
        val text = extras.getCharSequence(Notification.EXTRA_TEXT)?.toString()
            ?: extras.getCharSequence(Notification.EXTRA_BIG_TEXT)?.toString()
            ?: ""

        if (text.isBlank()) return

        val appLabel = try {
            val pm = packageManager
            val appInfo = pm.getApplicationInfo(packageName, 0)
            pm.getApplicationLabel(appInfo).toString()
        } catch (_: Exception) {
            when {
                packageName.contains("whatsapp", ignoreCase = true) -> "WhatsApp"
                packageName.contains("telegram", ignoreCase = true) -> "Telegram"
                packageName.contains("gm", ignoreCase = true) -> "Gmail"
                packageName.contains("viber", ignoreCase = true) -> "Viber"
                packageName.contains("messaging", ignoreCase = true) -> "SMS"
                else -> "Message"
            }
        }

        val captured = CapturedMessage(
            packageName = packageName,
            appName = appLabel,
            sender = title,
            text = text,
            timestamp = System.currentTimeMillis()
        )

        Log.d("VoiceGemNotif", "Captured message from $appLabel ($title): $text")
        NotificationQueueManager.addMessage(captured)
    }

    override fun onListenerConnected() {
        super.onListenerConnected()
        Log.i("VoiceGemNotif", "VoiceGemNotificationListenerService connected successfully.")
    }

    override fun onListenerDisconnected() {
        super.onListenerDisconnected()
        Log.i("VoiceGemNotif", "VoiceGemNotificationListenerService disconnected.")
    }
}
