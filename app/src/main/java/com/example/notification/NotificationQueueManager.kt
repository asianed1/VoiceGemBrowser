package com.example.notification

import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import java.util.Collections

object NotificationQueueManager {

    private val messageQueue = Collections.synchronizedList(mutableListOf<CapturedMessage>())

    private val _messages = MutableStateFlow<List<CapturedMessage>>(emptyList())
    val messages: StateFlow<List<CapturedMessage>> = _messages.asStateFlow()

    private val _unreadCount = MutableStateFlow(0)
    val unreadCount: StateFlow<Int> = _unreadCount.asStateFlow()

    fun addMessage(message: CapturedMessage) {
        // Prevent exact duplicate spam within 3 seconds
        val isDuplicate = messageQueue.any { 
            it.sender == message.sender && it.text == message.text && (message.timestamp - it.timestamp < 3000)
        }
        if (isDuplicate) return

        messageQueue.add(0, message)
        // Keep max 20 messages in queue
        if (messageQueue.size > 20) {
            messageQueue.removeAt(messageQueue.size - 1)
        }
        val currentList = synchronized(messageQueue) { messageQueue.toList() }
        _messages.value = currentList
        _unreadCount.value = currentList.size
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
}
