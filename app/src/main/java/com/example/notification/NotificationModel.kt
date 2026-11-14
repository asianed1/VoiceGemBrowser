package com.example.notification

data class CapturedMessage(
    val id: String = java.util.UUID.randomUUID().toString(),
    val packageName: String,
    val appName: String,
    val sender: String,
    val text: String,
    val timestamp: Long = System.currentTimeMillis()
)
