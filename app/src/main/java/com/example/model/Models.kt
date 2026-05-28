package com.example.model

import java.util.UUID

data class ChatMessage(
    val id: String = java.util.UUID.randomUUID().toString(),
    val sender: String,
    val text: String,
    val timestamp: Long = System.currentTimeMillis(),
    val isMe: Boolean
)

data class ClipboardItem(
    val id: String = java.util.UUID.randomUUID().toString(),
    val text: String,
    val sender: String,
    val timestamp: Long = System.currentTimeMillis()
)

data class TransferItem(
    val id: String = java.util.UUID.randomUUID().toString(),
    val name: String,
    val size: Long,
    val path: String,
    val isIncoming: Boolean,
    val progress: Float, // 0.0f to 1.0f
    val status: String = "Success", // "Transferring", "Success", "Failed"
    val timestamp: Long = System.currentTimeMillis()
)

data class ConnectedDevice(
    val id: String,
    val ip: String,
    val deviceName: String,
    val lastSeen: Long = System.currentTimeMillis()
)
