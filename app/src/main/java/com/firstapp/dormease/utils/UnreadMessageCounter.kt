package com.firstapp.dormease.utils

// FILE PATH: app/src/main/java/com/firstapp/dormease/utils/UnreadMessageCounter.kt

/**
 * Singleton that tracks the total number of unread messages received via socket.
 *
 * How it works:
 *  - Any activity/service that receives a `new_message` socket event calls [increment].
 *  - When the user opens MessagesActivity or a specific ChatActivity, call [clearAll]
 *    or [clearFor] to reset counts.
 *  - Activities with a bottom nav bar observe [total] to show/hide the badge.
 */
object UnreadMessageCounter {

    // Per-sender unread counts
    private val counts = mutableMapOf<Int, Int>()

    // Listeners notified whenever the total changes
    private val listeners = mutableListOf<(Int) -> Unit>()

    /** Total unread messages across all senders. */
    val total: Int get() = counts.values.sum()

    /** Increment unread count for a specific sender. */
    fun increment(senderId: Int) {
        counts[senderId] = (counts[senderId] ?: 0) + 1
        notifyListeners()
    }

    /** Clear unread count for a specific sender (user opened that chat). */
    fun clearFor(senderId: Int) {
        counts.remove(senderId)
        notifyListeners()
    }

    /** Clear all unread counts (user visited MessagesActivity). */
    fun clearAll() {
        counts.clear()
        notifyListeners()
    }

    /** Get unread count for one sender. */
    fun getFor(senderId: Int): Int = counts[senderId] ?: 0

    // ── Listener management ───────────────────────────────────────────────────

    fun addListener(listener: (Int) -> Unit) {
        listeners.add(listener)
    }

    fun removeListener(listener: (Int) -> Unit) {
        listeners.remove(listener)
    }

    private fun notifyListeners() {
        val t = total
        listeners.forEach { it(t) }
    }
}