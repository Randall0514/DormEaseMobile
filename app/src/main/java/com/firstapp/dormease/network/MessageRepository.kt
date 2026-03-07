package com.firstapp.dormease.network

// FILE PATH: app/src/main/java/com/firstapp/dormease/network/MessageRepository.kt
//
// Singleton that caches all chat messages in memory for the app's lifetime.
// This means messages survive navigation between activities and are only
// cleared on process death (i.e. actual app close / logout).
//
// It also tracks which conversations have already had their server history
// loaded so we never double-fetch.

import com.firstapp.dormease.ChatMessage

object MessageRepository {

    // Map of  recipientId  →  ordered list of messages
    private val messageMap = mutableMapOf<Int, MutableList<ChatMessage>>()

    // Tracks which contact IDs have had their full server history loaded
    private val historyLoaded = mutableSetOf<Int>()

    // ── Read ─────────────────────────────────────────────────────────────────

    fun getMessages(contactId: Int): List<ChatMessage> =
        messageMap[contactId] ?: emptyList()

    fun isHistoryLoaded(contactId: Int): Boolean =
        historyLoaded.contains(contactId)

    // ── Write ────────────────────────────────────────────────────────────────

    /** Replace the full message list for a contact (used after history fetch). */
    fun setHistory(contactId: Int, messages: List<ChatMessage>) {
        messageMap[contactId] = messages.toMutableList()
        historyLoaded.add(contactId)
    }

    /** Append a single new message (sent or received in real-time). */
    fun addMessage(contactId: Int, message: ChatMessage) {
        messageMap.getOrPut(contactId) { mutableListOf() }.add(message)
    }

    /** Remove a whole conversation (after server-side delete). */
    fun deleteConversation(contactId: Int) {
        messageMap.remove(contactId)
        historyLoaded.remove(contactId)
    }

    /** Clear everything on logout so the next user starts fresh. */
    fun clearAll() {
        messageMap.clear()
        historyLoaded.clear()
    }
}