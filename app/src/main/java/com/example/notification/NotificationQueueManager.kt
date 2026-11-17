package com.example.notification

import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import java.util.Collections
import java.util.Locale

object NotificationQueueManager {

    private val messageQueue = Collections.synchronizedList(mutableListOf<CapturedMessage>())
    private val filteredQueue = Collections.synchronizedList(mutableListOf<CapturedMessage>())

    private val _messages = MutableStateFlow<List<CapturedMessage>>(emptyList())
    val messages: StateFlow<List<CapturedMessage>> = _messages.asStateFlow()

    private val _unreadCount = MutableStateFlow(0)
    val unreadCount: StateFlow<Int> = _unreadCount.asStateFlow()

    private val _filteredMessages = MutableStateFlow<List<CapturedMessage>>(emptyList())
    val filteredMessages: StateFlow<List<CapturedMessage>> = _filteredMessages.asStateFlow()

    private val _filteredCount = MutableStateFlow(0)
    val filteredCount: StateFlow<Int> = _filteredCount.asStateFlow()

    fun addMessage(message: CapturedMessage) {
        // Hard guard: OTP/2FA codes are never stored or spoken aloud
        if (LocalSpamFilter.isSensitiveOtpOr2fa(message.sender, message.text)) {
            return
        }

        synchronized(messageQueue) {
            val isDuplicate = messageQueue.any {
                it.sender == message.sender && it.text == message.text && (message.timestamp - it.timestamp < 3000)
            }
            if (isDuplicate) return

            messageQueue.add(0, message)
            if (messageQueue.size > 20) {
                messageQueue.removeAt(messageQueue.size - 1)
            }
            val currentList = messageQueue.toList()
            _messages.value = currentList
            _unreadCount.value = currentList.size
        }
    }

    fun addFilteredMessage(message: CapturedMessage, reason: String) {
        // Hard guard: OTP/2FA codes must never be retained anywhere, even in the filtered bucket
        if (LocalSpamFilter.isSensitiveOtpOr2fa(message.sender, message.text)) {
            return
        }

        val tagged = message.copy(filterReason = reason)
        synchronized(filteredQueue) {
            val isDuplicate = filteredQueue.any {
                it.sender == tagged.sender && it.text == tagged.text && (tagged.timestamp - it.timestamp < 3000)
            }
            if (isDuplicate) return

            filteredQueue.add(0, tagged)
            if (filteredQueue.size > 30) {
                filteredQueue.removeAt(filteredQueue.size - 1)
            }
            val currentList = filteredQueue.toList()
            _filteredMessages.value = currentList
            _filteredCount.value = currentList.size
        }
    }

    fun muteSenderFromQueue(sender: String) {
        val targetLower = sender.trim().lowercase(Locale.US)
        if (targetLower.isEmpty()) return

        val moved = mutableListOf<CapturedMessage>()
        synchronized(messageQueue) {
            val iterator = messageQueue.iterator()
            while (iterator.hasNext()) {
                val item = iterator.next()
                val itemSenderLower = item.sender.trim().lowercase(Locale.US)
                if (itemSenderLower == targetLower || itemSenderLower.contains(targetLower)) {
                    iterator.remove()
                    moved.add(item)
                }
            }
            val currentList = messageQueue.toList()
            _messages.value = currentList
            _unreadCount.value = currentList.size
        }

        moved.forEach { addFilteredMessage(it, "Muted sender") }
    }

    fun restoreFilteredMessage(messageId: String) {
        var restored: CapturedMessage? = null
        synchronized(filteredQueue) {
            val iterator = filteredQueue.iterator()
            while (iterator.hasNext()) {
                val item = iterator.next()
                if (item.id == messageId) {
                    iterator.remove()
                    restored = item.copy(filterReason = null)
                    break
                }
            }
            val currentList = filteredQueue.toList()
            _filteredMessages.value = currentList
            _filteredCount.value = currentList.size
        }
        restored?.let { addMessage(it) }
    }

    fun getUnreadMessages(): List<CapturedMessage> {
        return synchronized(messageQueue) {
            messageQueue.toList()
        }
    }

    fun clearQueue() {
        synchronized(messageQueue) {
            messageQueue.clear()
        }
        _messages.value = emptyList()
        _unreadCount.value = 0
    }

    fun clearFilteredQueue() {
        synchronized(filteredQueue) {
            filteredQueue.clear()
        }
        _filteredMessages.value = emptyList()
        _filteredCount.value = 0
    }
}
