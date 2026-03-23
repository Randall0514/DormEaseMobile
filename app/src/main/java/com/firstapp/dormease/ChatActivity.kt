package com.firstapp.dormease

// FILE PATH: app/src/main/java/com/firstapp/dormease/ChatActivity.kt

import android.content.res.ColorStateList
import android.os.Bundle
import android.graphics.Color
import android.text.Editable
import android.text.TextWatcher
import android.util.Log
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.EditText
import android.widget.ImageButton
import android.widget.TextView
import android.widget.Toast
import androidx.appcompat.app.AlertDialog
import androidx.appcompat.app.AppCompatActivity
import androidx.lifecycle.lifecycleScope
import androidx.recyclerview.widget.LinearLayoutManager
import androidx.recyclerview.widget.RecyclerView
import com.firstapp.dormease.model.SendMessageRequest
import com.firstapp.dormease.network.MessageRepository
import com.firstapp.dormease.network.RetrofitClient
import com.firstapp.dormease.network.SocketManager
import com.firstapp.dormease.utils.SessionManager
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import org.json.JSONObject
import java.text.SimpleDateFormat
import java.util.*

// ─── Data model ──────────────────────────────────────────────────────────────

data class ChatMessage(
    val text      : String,
    val timestamp : Long,
    val isMine    : Boolean
)

// ─── Adapter ─────────────────────────────────────────────────────────────────

class ChatAdapter(
    private val messages: MutableList<ChatMessage>,
    private val peerInitial: String
) : RecyclerView.Adapter<RecyclerView.ViewHolder>() {

    companion object {
        private const val VIEW_MINE   = 1
        private const val VIEW_THEIRS = 2
    }

    inner class MineVH(view: View) : RecyclerView.ViewHolder(view) {
        val tvText : TextView = view.findViewById(R.id.tvMessageText)
        val tvTime : TextView = view.findViewById(R.id.tvMessageTime)
    }

    inner class TheirsVH(view: View) : RecyclerView.ViewHolder(view) {
        val tvAvatar: TextView = view.findViewById(R.id.tvSenderAvatar)
        val tvText : TextView = view.findViewById(R.id.tvMessageText)
        val tvTime : TextView = view.findViewById(R.id.tvMessageTime)
    }

    override fun getItemViewType(position: Int) =
        if (messages[position].isMine) VIEW_MINE else VIEW_THEIRS

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): RecyclerView.ViewHolder {
        val inflater = LayoutInflater.from(parent.context)
        return if (viewType == VIEW_MINE) {
            MineVH(inflater.inflate(R.layout.item_message_mine, parent, false))
        } else {
            TheirsVH(inflater.inflate(R.layout.item_message_theirs, parent, false))
        }
    }

    override fun getItemCount() = messages.size

    override fun onBindViewHolder(holder: RecyclerView.ViewHolder, position: Int) {
        val msg = messages[position]
        val fmt = SimpleDateFormat("hh:mm a", Locale.getDefault())
        val timeStr = fmt.format(Date(msg.timestamp))
        when (holder) {
            is MineVH   -> { holder.tvText.text = msg.text; holder.tvTime.text = timeStr }
            is TheirsVH -> {
                holder.tvAvatar.text = peerInitial
                holder.tvText.text = msg.text
                holder.tvTime.text = timeStr
            }
        }
    }

    fun addMessage(msg: ChatMessage) {
        messages.add(msg)
        notifyItemInserted(messages.size - 1)
    }

    fun setMessages(newMessages: List<ChatMessage>) {
        messages.clear()
        messages.addAll(newMessages)
        notifyDataSetChanged()
    }
}

// ─── Activity ─────────────────────────────────────────────────────────────────

class ChatActivity : AppCompatActivity() {

    private lateinit var rvMessages    : RecyclerView
    private lateinit var etMessage     : EditText
    private lateinit var btnSend       : ImageButton
    private lateinit var tvChatName    : TextView
    private lateinit var tvChatStatus  : TextView
    private lateinit var vChatStatusDot: View
    private lateinit var btnBack       : ImageButton
    private lateinit var btnDelete     : ImageButton

    private lateinit var adapter       : ChatAdapter
    private lateinit var sessionManager: SessionManager

    private var recipientId   = -1
    private var recipientName = ""

    // ── Socket listeners ──────────────────────────────────────────────────────

    private val newMessageListener: (JSONObject) -> Unit = { data ->
        val senderId = data.optInt("senderId", -1)
        // FIX: Only handle messages FROM the other person, never from ourselves.
        // Our own messages are already shown optimistically when we send them.
        if (senderId == recipientId) {
            val text = data.optString("message", "").trim()
            if (text.isNotEmpty()) {
                val incomingTimestamp = System.currentTimeMillis()

                // FIX: Deduplicate — skip if we already have this exact message
                // from the server within the last 3 seconds (prevents socket + HTTP echo)
                val isDuplicate = MessageRepository.getMessages(recipientId).any { existing ->
                    !existing.isMine &&
                            existing.text == text &&
                            Math.abs(existing.timestamp - incomingTimestamp) < 3000
                }

                if (!isDuplicate) {
                    val msg = ChatMessage(
                        text      = text,
                        timestamp = incomingTimestamp,
                        isMine    = false
                    )
                    MessageRepository.addMessage(recipientId, msg)
                    runOnUiThread {
                        adapter.addMessage(msg)
                        scrollToBottom()
                    }
                }
            }
        }
    }

    private val connectionListener: (Boolean) -> Unit = { connected ->
        runOnUiThread {
            setPresenceState(connected)
        }
    }

    // ─────────────────────────────────────────────────────────────────────────

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_chat)
        supportActionBar?.hide()

        recipientId   = intent.getIntExtra("EXTRA_RECIPIENT_ID", -1)
        recipientName = intent.getStringExtra("EXTRA_RECIPIENT_NAME") ?: "Chat"
        sessionManager = SessionManager(this)

        if (recipientId == -1) {
            Toast.makeText(this, "Invalid recipient", Toast.LENGTH_SHORT).show()
            finish()
            return
        }

        // Bind views
        rvMessages    = findViewById(R.id.rvMessages)
        etMessage     = findViewById(R.id.etMessage)
        btnSend       = findViewById(R.id.btnSend)
        tvChatName    = findViewById(R.id.tvChatName)
        tvChatStatus  = findViewById(R.id.tvChatStatus)
        vChatStatusDot = findViewById(R.id.vChatStatusDot)
        btnBack       = findViewById(R.id.btnBack)
        btnDelete     = findViewById(R.id.btnDelete)

        tvChatName.text   = recipientName
        setPresenceState(SocketManager.isConnected())

        btnSend.isEnabled = false

        // Set avatar initial
        val tvAvatar = findViewById<TextView?>(R.id.tvChatAvatar)
        tvAvatar?.text = recipientName.trim().firstOrNull()?.uppercaseChar()?.toString() ?: "?"

        // RecyclerView
        adapter = ChatAdapter(
            MessageRepository.getMessages(recipientId).toMutableList(),
            recipientName.trim().firstOrNull()?.uppercaseChar()?.toString() ?: "?"
        )
        rvMessages.layoutManager = LinearLayoutManager(this).apply {
            stackFromEnd = true
        }
        rvMessages.adapter = adapter
        if (adapter.itemCount > 0) scrollToBottom()

        btnBack.setOnClickListener { finish() }
        btnSend.setOnClickListener { sendMessage() }
        btnDelete.setOnClickListener { confirmDeleteConversation() }

        etMessage.addTextChangedListener(object : TextWatcher {
            override fun beforeTextChanged(s: CharSequence?, start: Int, count: Int, after: Int) = Unit
            override fun onTextChanged(s: CharSequence?, start: Int, before: Int, count: Int) {
                updateSendEnabledState(s)
            }
            override fun afterTextChanged(s: Editable?) = Unit
        })

        etMessage.setOnEditorActionListener { _, _, _ ->
            sendMessage()
            true
        }

        updateSendEnabledState(etMessage.text)

        // ── Ensure socket is connected ────────────────────────────────────────
        val token = sessionManager.fetchAuthToken()
        if (token != null && !SocketManager.isConnected()) {
            SocketManager.connect(token)
        }

        SocketManager.addNewMessageListener(newMessageListener)
        SocketManager.addConnectionListener(connectionListener)

        loadHistoryFromServer()
    }

    override fun onDestroy() {
        super.onDestroy()
        SocketManager.removeNewMessageListener(newMessageListener)
        SocketManager.removeConnectionListener(connectionListener)
    }

    private fun loadHistoryFromServer() {
        lifecycleScope.launch {
            try {
                val response = withContext(Dispatchers.IO) {
                    RetrofitClient.getApiService(this@ChatActivity)
                        .getMessageHistory(recipientId)
                }
                if (response.isSuccessful) {
                    val myId = sessionManager.getUserId()
                    val rows = response.body() ?: emptyList()
                    val history = rows.map { row ->
                        ChatMessage(
                            text      = row.message,
                            timestamp = parseIso(row.created_at),
                            isMine    = row.sender_id == myId
                        )
                    }
                    MessageRepository.setHistory(recipientId, history)
                    adapter.setMessages(history)
                    scrollToBottom()
                }
            } catch (e: Exception) {
                Log.w("ChatActivity", "History load failed (non-fatal): ${e.message}")
            }
        }
    }

    private fun confirmDeleteConversation() {
        AlertDialog.Builder(this)
            .setTitle("Delete conversation?")
            .setMessage("This will permanently delete your chat with $recipientName. This cannot be undone.")
            .setPositiveButton("Delete") { _, _ -> deleteConversation() }
            .setNegativeButton("Cancel", null)
            .show()
    }

    private fun deleteConversation() {
        lifecycleScope.launch {
            try {
                val response = withContext(Dispatchers.IO) {
                    RetrofitClient.getApiService(this@ChatActivity)
                        .deleteConversation(recipientId)
                }
                if (response.isSuccessful) {
                    MessageRepository.deleteConversation(recipientId)
                    Toast.makeText(this@ChatActivity, "Conversation deleted", Toast.LENGTH_SHORT).show()
                    finish()
                } else {
                    Toast.makeText(this@ChatActivity, "Could not delete conversation", Toast.LENGTH_SHORT).show()
                }
            } catch (e: Exception) {
                Toast.makeText(this@ChatActivity, "Network error: ${e.message}", Toast.LENGTH_SHORT).show()
            }
        }
    }

    private fun sendMessage() {
        val text = etMessage.text.toString().trim()
        if (text.isEmpty()) return

        // FIX: ONLY use HTTP to send. The HTTP endpoint persists the message AND
        // notifies the recipient via WebSocket push. Do NOT call SocketManager.sendMessage()
        // separately — that would cause the recipient to receive two notifications
        // (one from the socket event + one from the HTTP endpoint's notifyUser call).

        // Optimistically add to UI immediately so the sender sees it right away
        val msg = ChatMessage(
            text      = text,
            timestamp = System.currentTimeMillis(),
            isMine    = true
        )
        MessageRepository.addMessage(recipientId, msg)
        adapter.addMessage(msg)
        etMessage.setText("")
        updateSendEnabledState(etMessage.text)
        scrollToBottom()

        // Persist on server (also triggers real-time push to recipient via WebSocket)
        sendViaHttp(text)
    }

    /**
     * Persists the message on the server via POST /messages/send.
     * The server saves it to the DB and pushes a `new_message` WebSocket event
     * to the RECIPIENT only — so the sender never gets a duplicate.
     */
    private fun sendViaHttp(text: String) {
        lifecycleScope.launch {
            try {
                val response = withContext(Dispatchers.IO) {
                    RetrofitClient.getApiService(this@ChatActivity)
                        .sendMessage(SendMessageRequest(recipientId, text))
                }
                if (!response.isSuccessful) {
                    val errorBody = response.errorBody()?.string() ?: ""
                    Log.w("ChatActivity", "HTTP send failed ${response.code()}: $errorBody")
                }
            } catch (e: Exception) {
                Log.w("ChatActivity", "HTTP send error (non-fatal): ${e.message}")
            }
        }
    }

    private fun scrollToBottom() {
        if (adapter.itemCount > 0) {
            rvMessages.smoothScrollToPosition(adapter.itemCount - 1)
        }
    }

    private fun updateSendEnabledState(text: CharSequence?) {
        val enabled = !text.isNullOrBlank()
        btnSend.isEnabled = enabled
        btnSend.alpha = if (enabled) 1f else 0.72f
    }

    private fun setPresenceState(connected: Boolean) {
        tvChatStatus.text = if (connected) "Active now" else "Offline"
        tvChatStatus.setTextColor(
            Color.parseColor(if (connected) "#AEE8C8" else "#D3DEEA")
        )
        vChatStatusDot.backgroundTintList = ColorStateList.valueOf(
            Color.parseColor(if (connected) "#77D9A2" else "#90A2B8")
        )
    }

    private fun parseIso(iso: String): Long {
        return try {
            val sdf = SimpleDateFormat("yyyy-MM-dd'T'HH:mm:ss.SSS'Z'", Locale.US).apply {
                timeZone = TimeZone.getTimeZone("UTC")
            }
            sdf.parse(iso)?.time ?: System.currentTimeMillis()
        } catch (_: Exception) {
            System.currentTimeMillis()
        }
    }
}