package com.firstapp.dormease.model

// FILE PATH: app/src/main/java/com/firstapp/dormease/model/MessageHistoryItem.kt
// Matches the JSON shape returned by GET /messages/{contactId}/history

data class MessageHistoryItem(
    val id           : Int,
    val sender_id    : Int,
    val recipient_id : Int,
    val message      : String,
    val created_at   : String   // ISO-8601 timestamp string from the server
)