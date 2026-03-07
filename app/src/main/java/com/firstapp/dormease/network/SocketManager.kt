package com.firstapp.dormease.network

// FILE PATH: app/src/main/java/com/firstapp/dormease/network/SocketManager.kt

import android.util.Log
import io.socket.client.IO
import io.socket.client.Socket
import org.json.JSONObject
import java.net.URISyntaxException

/**
 * Singleton Socket.IO manager for DormEase.
 * Connects using the session token from SessionManager.
 */
object SocketManager {

    private const val TAG = "SocketManager"

    private var socket: Socket? = null
    private var isConnected = false

    // Listener lists
    private val notificationListeners      = mutableListOf<(JSONObject) -> Unit>()
    private val reservationUpdateListeners = mutableListOf<(JSONObject) -> Unit>()
    private val newMessageListeners        = mutableListOf<(JSONObject) -> Unit>()
    private val connectionListeners        = mutableListOf<(Boolean) -> Unit>()

    // ── Connect ──────────────────────────────────────────────────────────────

    /**
     * Connect to Socket.IO server using the Bearer token.
     * Call this right after a successful login (or on app start if token exists).
     */
    fun connect(token: String) {
        if (isConnected || socket?.connected() == true) {
            Log.d(TAG, "Already connected, skipping")
            return
        }

        try {
            val options = IO.Options().apply {
                auth = mapOf("token" to token)
                reconnection         = true
                reconnectionDelay    = 1000
                reconnectionDelayMax = 5000
                reconnectionAttempts = 5
            }

            socket = IO.socket(Constants.SOCKET_URL, options)

            socket?.apply {

                on(Socket.EVENT_CONNECT) {
                    Log.d(TAG, "✅ Connected: ${id()}")
                    isConnected = true
                    notifyConnectionListeners(true)
                }

                on(Socket.EVENT_DISCONNECT) { args ->
                    val reason = args?.getOrNull(0)?.toString() ?: "unknown"
                    Log.d(TAG, "❌ Disconnected: $reason")
                    isConnected = false
                    notifyConnectionListeners(false)
                }

                on(Socket.EVENT_CONNECT_ERROR) { args ->
                    val error = args?.getOrNull(0)?.toString() ?: "unknown"
                    Log.e(TAG, "🔴 Connect error: $error")
                    isConnected = false
                    notifyConnectionListeners(false)
                }

                on("notification") { args ->
                    parseAndDispatch(args, "notification", notificationListeners)
                }

                on("reservation_updated") { args ->
                    parseAndDispatch(args, "reservation_updated", reservationUpdateListeners)
                    parseAndDispatch(args, "reservation_updated", notificationListeners)
                }

                on("new_message") { args ->
                    parseAndDispatch(args, "new_message", newMessageListeners)
                }

                on("error") { args ->
                    Log.e(TAG, "🔴 Server error: ${args?.getOrNull(0)}")
                }

                connect()
            }

        } catch (e: URISyntaxException) {
            Log.e(TAG, "Invalid server URL: ${e.message}")
        }
    }

    // ── Disconnect ───────────────────────────────────────────────────────────

    fun disconnect() {
        socket?.disconnect()
        socket?.off()
        socket = null
        isConnected = false
        Log.d(TAG, "🔌 Socket disconnected and cleaned up")
    }

    // ── Emit ─────────────────────────────────────────────────────────────────

    fun sendMessage(recipientId: Int, message: String) {
        if (!isConnected) { Log.w(TAG, "Not connected"); return }
        val payload = JSONObject().apply {
            put("recipientId", recipientId)
            put("message", message)
        }
        socket?.emit("send_message", payload)
    }

    fun sendTyping(recipientId: Int, isTyping: Boolean) {
        if (!isConnected) return
        val payload = JSONObject().apply {
            put("recipientId", recipientId)
            put("isTyping", isTyping)
        }
        socket?.emit("typing", payload)
    }

    // ── Listener registration ────────────────────────────────────────────────

    fun addNotificationListener(listener: (JSONObject) -> Unit)         = notificationListeners.add(listener)
    fun removeNotificationListener(listener: (JSONObject) -> Unit)      = notificationListeners.remove(listener)

    fun addReservationUpdateListener(listener: (JSONObject) -> Unit)    = reservationUpdateListeners.add(listener)
    fun removeReservationUpdateListener(listener: (JSONObject) -> Unit) = reservationUpdateListeners.remove(listener)

    fun addNewMessageListener(listener: (JSONObject) -> Unit)           = newMessageListeners.add(listener)
    fun removeNewMessageListener(listener: (JSONObject) -> Unit)        = newMessageListeners.remove(listener)

    fun addConnectionListener(listener: (Boolean) -> Unit)              = connectionListeners.add(listener)
    fun removeConnectionListener(listener: (Boolean) -> Unit)           = connectionListeners.remove(listener)

    fun isConnected() = isConnected

    // ── Private ──────────────────────────────────────────────────────────────

    private fun parseAndDispatch(
        args: Array<Any?>?,
        eventName: String,
        listeners: List<(JSONObject) -> Unit>
    ) {
        args?.getOrNull(0)?.let { raw ->
            try {
                val data = JSONObject(raw.toString())
                Log.d(TAG, "📩 [$eventName] $data")
                listeners.forEach { it(data) }
            } catch (e: Exception) {
                Log.e(TAG, "Parse error [$eventName]: ${e.message}")
            }
        }
    }

    private fun notifyConnectionListeners(connected: Boolean) =
        connectionListeners.forEach { it(connected) }
}