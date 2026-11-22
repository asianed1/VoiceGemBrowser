package com.example.notification

import android.app.Notification
import android.app.PendingIntent
import android.app.RemoteInput
import android.content.Context
import android.content.Intent
import android.os.Bundle
import java.util.Locale
import java.util.concurrent.ConcurrentHashMap

sealed class DirectReplyOutcome {
    data object Sent : DirectReplyOutcome()
    data object Expired : DirectReplyOutcome()
    data object NoDirectReplyAction : DirectReplyOutcome()
}

data class ReplyActionEntry(
    val message: CapturedMessage,
    val notificationKey: String?,
    val replyAction: Notification.Action?
)

/**
 * Strictly in-memory store of active Notification RemoteInput reply tokens and recent reply targets.
 * Never persisted to disk or SharedPreferences, and never populated for OTP or spam notifications.
 */
object NotificationReplyStore {

    private val entriesById = ConcurrentHashMap<String, ReplyActionEntry>()
    private val recentOrderedIds = mutableListOf<String>()
    private const val MAX_RECENT_TARGETS = 20

    fun recordReplyTarget(
        message: CapturedMessage,
        notificationKey: String?,
        replyAction: Notification.Action?
    ) {
        // Defense-in-depth: never hold reply tokens for OTP/2FA messages
        if (LocalSpamFilter.isSensitiveOtpOr2fa(message.sender, message.text)) return

        val entry = ReplyActionEntry(
            message = message,
            notificationKey = notificationKey,
            replyAction = replyAction
        )
        entriesById[message.id] = entry
        synchronized(recentOrderedIds) {
            recentOrderedIds.remove(message.id)
            recentOrderedIds.add(0, message.id)
            while (recentOrderedIds.size > MAX_RECENT_TARGETS) {
                val evictedId = recentOrderedIds.removeAt(recentOrderedIds.lastIndex)
                entriesById.remove(evictedId)
            }
        }
    }

    fun onNotificationRemoved(notificationKey: String?) {
        if (notificationKey.isNullOrBlank()) return
        entriesById.entries.forEach { (id, entry) ->
            if (entry.notificationKey == notificationKey && entry.replyAction != null) {
                entriesById[id] = entry.copy(replyAction = null, message = entry.message.copy(canDirectReply = false))
            }
        }
    }

    fun getEntry(messageId: String?): ReplyActionEntry? {
        if (messageId.isNullOrBlank()) return null
        return entriesById[messageId]
    }

    fun findReplyTarget(senderQuery: String?, appFilter: String? = null): ReplyActionEntry? {
        val snapshot = synchronized(recentOrderedIds) {
            recentOrderedIds.mapNotNull { entriesById[it] }
        }
        if (snapshot.isEmpty()) return null

        val filteredByApp = if (!appFilter.isNullOrBlank()) {
            snapshot.filter {
                it.message.appName.contains(appFilter, ignoreCase = true) ||
                    it.message.packageName.contains(appFilter, ignoreCase = true)
            }.ifEmpty { snapshot }
        } else {
            snapshot
        }

        if (senderQuery.isNullOrBlank()) {
            return filteredByApp.firstOrNull { it.replyAction != null } ?: filteredByApp.firstOrNull()
        }

        val needle = senderQuery.trim().lowercase(Locale.US)
        val exact = filteredByApp.firstOrNull {
            it.message.sender.trim().lowercase(Locale.US) == needle
        }
        if (exact != null) return exact

        return filteredByApp.firstOrNull {
            val senderLower = it.message.sender.lowercase(Locale.US)
            senderLower.contains(needle) || needle.contains(senderLower)
        }
    }

    fun extractBestReplyAction(notification: Notification): Notification.Action? {
        val actions = buildList {
            notification.actions?.let { addAll(it) }
            runCatching {
                Notification.WearableExtender(notification).actions
            }.getOrNull()?.let { addAll(it) }
        }

        val replyCandidates = actions.filter { action ->
            val inputs = action.remoteInputs
            action.actionIntent != null && !inputs.isNullOrEmpty() && inputs.any { it.allowFreeFormInput }
        }
        if (replyCandidates.isEmpty()) return null

        return replyCandidates.firstOrNull { action ->
            action.title?.toString()?.contains("reply", ignoreCase = true) == true
        } ?: replyCandidates.first()
    }

    fun sendDirectReply(context: Context, messageId: String, replyText: String): DirectReplyOutcome {
        val cleanText = replyText.trim()
        if (cleanText.isEmpty()) return DirectReplyOutcome.NoDirectReplyAction

        val entry = entriesById[messageId] ?: return DirectReplyOutcome.NoDirectReplyAction
        val action = entry.replyAction ?: return DirectReplyOutcome.Expired
        val remoteInputs = action.remoteInputs
        if (remoteInputs.isNullOrEmpty()) return DirectReplyOutcome.NoDirectReplyAction

        val targetInput = remoteInputs.firstOrNull { it.allowFreeFormInput } ?: remoteInputs.first()
        val resultsBundle = Bundle().apply {
            putCharSequence(targetInput.resultKey, cleanText)
        }
        val fillInIntent = Intent()
        RemoteInput.addResultsToIntent(remoteInputs, fillInIntent, resultsBundle)

        return try {
            action.actionIntent.send(context, 0, fillInIntent)
            DirectReplyOutcome.Sent
        } catch (_: PendingIntent.CanceledException) {
            onNotificationRemoved(entry.notificationKey)
            DirectReplyOutcome.Expired
        } catch (_: Exception) {
            DirectReplyOutcome.Expired
        }
    }

    fun clearAll() {
        entriesById.clear()
        synchronized(recentOrderedIds) {
            recentOrderedIds.clear()
        }
    }
}
