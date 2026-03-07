package com.firstapp.dormease

// FILE PATH: app/src/main/java/com/firstapp/dormease/ChatActivity.kt

import android.os.Bundle
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
    private val messages: MutableList<ChatMessage>
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
            is TheirsVH -> { holder.tvText.text = msg.text; holder.tvTime.text = timeStr }
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
    private lateinit var btnBack       : ImageButton
    private lateinit var btnDelete     : ImageButton

    private lateinit var adapter       : ChatAdapter
    private lateinit var sessionManager: SessionManager

    private var recipientId   = -1
    private var recipientName = ""

    // ── Socket listeners ──────────────────────────────────────────────────────

    private val newMessageListener: (JSONObject) -> Unit = { data ->
        val senderId = data.optInt("senderId", -1)
        if (senderId == recipientId) {
            val text = data.optString("message", "").trim()
            if (text.isNotEmpty()) {
                val msg = ChatMessage(
                    text      = text,
                    timestamp = System.currentTimeMillis(),
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

    private val connectionListener: (Boolean) -> Unit = { connected ->
        runOnUiThread {
            // Show status but NEVER disable the send button — socket is optional
            tvChatStatus.text = if (connected) "Active now" else "Offline"
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
        btnBack       = findViewById(R.id.btnBack)
        btnDelete     = findViewById(R.id.btnDelete)

        tvChatName.text   = recipientName
        tvChatStatus.text = if (SocketManager.isConnected()) "Active now" else "Offline"

        // Send button is ALWAYS enabled — socket failure won't block sending
        btnSend.isEnabled = true

        // RecyclerView
        adapter = ChatAdapter(MessageRepository.getMessages(recipientId).toMutableList())
        rvMessages.layoutManager = LinearLayoutManager(this).apply {
            stackFromEnd = true
        }
        rvMessages.adapter = adapter
        if (adapter.itemCount > 0) scrollToBottom()

        btnBack.setOnClickListener { finish() }
        btnSend.setOnClickListener { sendMessage() }
        btnDelete.setOnClickListener { confirmDeleteConversation() }

        etMessage.setOnEditorActionListener { _, _, _ ->
            sendMessage()
            true
        }

        // ── Ensure socket is connected ────────────────────────────────────────
        val token = sessionManager.fetchAuthToken()
        if (token != null && !SocketManager.isConnected()) {
            Log.d("ChatActivity", "Connecting socket with token")
            SocketManager.connect(token)
        } else {
            Log.d("ChatActivity", "Socket already connected: ${SocketManager.isConnected()}")
        }
        // ──────────────────────────────────────────────────────────────────────

        SocketManager.addNewMessageListener(newMessageListener)
        SocketManager.addConnectionListener(connectionListener)

        if (!MessageRepository.isHistoryLoaded(recipientId)) {
            loadHistoryFromServer()
        }
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
                // Don't show error toast — cached messages still visible
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

        // ── Try socket first, silently ignore if not connected ────────────────
        if (SocketManager.isConnected()) {
            try {
                SocketManager.sendMessage(recipientId, text)
            } catch (e: Exception) {
                Log.w("ChatActivity", "Socket send failed (non-fatal): ${e.message}")
                Toast.makeText(this, "Real-time unavailable, message may be delayed", Toast.LENGTH_SHORT).show()
            }
        } else {
            Toast.makeText(this, "Offline — message queued", Toast.LENGTH_SHORT).show()
        }

        // Always add to UI and cache regardless of socket status
        val msg = ChatMessage(
            text      = text,
            timestamp = System.currentTimeMillis(),
            isMine    = true
        )
        MessageRepository.addMessage(recipientId, msg)
        adapter.addMessage(msg)
        etMessage.setText("")
        scrollToBottom()
    }

    private fun scrollToBottom() {
        if (adapter.itemCount > 0) {
            rvMessages.smoothScrollToPosition(adapter.itemCount - 1)
        }
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