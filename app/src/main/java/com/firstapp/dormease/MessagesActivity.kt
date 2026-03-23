package com.firstapp.dormease

// FILE PATH: app/src/main/java/com/firstapp/dormease/MessagesActivity.kt
// CHANGES:
//   1. Calls UnreadMessageCounter.increment(senderId) when a socket message arrives
//   2. Calls UnreadMessageCounter.clearAll() in onResume (user is looking at messages list)
//   3. Calls UnreadMessageCounter.clearFor(id) when a conversation is opened

import android.content.Intent
import android.os.Bundle
import android.text.Editable
import android.text.TextWatcher
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.EditText
import android.widget.LinearLayout
import android.widget.TextView
import android.widget.Toast
import androidx.appcompat.app.AppCompatActivity
import androidx.lifecycle.lifecycleScope
import androidx.recyclerview.widget.LinearLayoutManager
import androidx.recyclerview.widget.RecyclerView
import com.firstapp.dormease.network.MessageRepository
import com.firstapp.dormease.network.RetrofitClient
import com.firstapp.dormease.network.SocketManager
import com.firstapp.dormease.utils.HomeRouter
import com.firstapp.dormease.utils.SessionManager
import com.firstapp.dormease.utils.UnreadMessageCounter
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import org.json.JSONObject
import retrofit2.Call
import retrofit2.Callback
import retrofit2.Response
import java.text.SimpleDateFormat
import java.util.*

// ─── Data model ──────────────────────────────────────────────────────────────

data class ConversationItem(
    val userId: Int,
    val userName: String,
    var preview: String = "Say hello 👋",
    var unreadCount: Int = 0,
    var updatedAt: Long = 0L
)

// ─── Adapter ─────────────────────────────────────────────────────────────────

class ConversationAdapter(
    private val items: MutableList<ConversationItem>,
    private val onClick: (ConversationItem) -> Unit
) : RecyclerView.Adapter<ConversationAdapter.VH>() {

    inner class VH(view: View) : RecyclerView.ViewHolder(view) {
        val tvLetter  : TextView = view.findViewById(R.id.tvAvatarLetter)
        val tvName    : TextView = view.findViewById(R.id.tvConversationName)
        val tvPreview : TextView = view.findViewById(R.id.tvConversationPreview)
        val tvTime    : TextView = view.findViewById(R.id.tvConversationTime)
        val tvBadge   : TextView = view.findViewById(R.id.tvUnreadBadge)
    }

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): VH {
        val v = LayoutInflater.from(parent.context)
            .inflate(R.layout.item_conversation, parent, false)
        return VH(v)
    }

    override fun getItemCount() = items.size

    override fun onBindViewHolder(holder: VH, position: Int) {
        val item = items[position]

        holder.tvLetter.text  = item.userName.trim().firstOrNull()?.uppercaseChar()?.toString() ?: "?"
        holder.tvName.text    = item.userName
        holder.tvPreview.text = item.preview

        if (item.updatedAt > 0L) {
            val fmt = SimpleDateFormat("hh:mm a", Locale.getDefault())
            holder.tvTime.text       = fmt.format(Date(item.updatedAt))
            holder.tvTime.visibility = View.VISIBLE
        } else {
            holder.tvTime.visibility = View.GONE
        }

        if (item.unreadCount > 0) {
            holder.tvBadge.text       = if (item.unreadCount > 9) "9+" else item.unreadCount.toString()
            holder.tvBadge.visibility = View.VISIBLE
        } else {
            holder.tvBadge.visibility = View.GONE
        }

        holder.itemView.setOnClickListener { onClick(item) }
    }

    fun updateList(newItems: List<ConversationItem>) {
        items.clear()
        items.addAll(newItems)
        notifyDataSetChanged()
    }
}

// ─── Activity ─────────────────────────────────────────────────────────────────

class MessagesActivity : AppCompatActivity() {

    private lateinit var sessionManager     : SessionManager
    private lateinit var adapter            : ConversationAdapter
    private lateinit var rvConversations    : RecyclerView
    private lateinit var layoutEmpty        : LinearLayout
    private lateinit var viewConnectionDot  : View
    private lateinit var tvConnectionStatus : TextView

    private val allConversations = mutableListOf<ConversationItem>()

    private val newMessageListener: (JSONObject) -> Unit = { data ->
        runOnUiThread { handleIncomingMessage(data) }
    }

    private val connectionListener: (Boolean) -> Unit = { connected ->
        runOnUiThread { updateConnectionUI(connected) }
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_messages)
        supportActionBar?.hide()

        sessionManager = SessionManager(this)

        rvConversations    = findViewById(R.id.rvConversations)
        layoutEmpty        = findViewById(R.id.layoutEmpty)
        viewConnectionDot  = findViewById(R.id.viewConnectionDot)
        tvConnectionStatus = findViewById(R.id.tvConnectionStatus)

        adapter = ConversationAdapter(mutableListOf()) { conversation ->
            openChat(conversation)
        }
        rvConversations.layoutManager = LinearLayoutManager(this)
        rvConversations.adapter       = adapter

        findViewById<EditText>(R.id.etSearch).addTextChangedListener(object : TextWatcher {
            override fun beforeTextChanged(s: CharSequence?, start: Int, count: Int, after: Int) = Unit
            override fun onTextChanged(s: CharSequence?, start: Int, before: Int, count: Int) {
                filterConversations(s?.toString() ?: "")
            }
            override fun afterTextChanged(s: Editable?) = Unit
        })

        // ── Bottom navigation ─────────────────────────────────────────────────
        findViewById<LinearLayout>(R.id.navHome).setOnClickListener {
            navigateHome()
        }
        findViewById<LinearLayout>(R.id.navSettings).setOnClickListener {
            startActivity(Intent(this, SettingsActivity::class.java))
            finish()
        }
        findViewById<LinearLayout>(R.id.navProfile).setOnClickListener {
            startActivity(Intent(this, ProfileActivity::class.java))
            finish()
        }

        updateConnectionUI(SocketManager.isConnected())
        SocketManager.addConnectionListener(connectionListener)
        SocketManager.addNewMessageListener(newMessageListener)

        loadContacts()
    }

    override fun onResume() {
        super.onResume()
        // NOTE: We do NOT clear the badge here — opening the list does NOT count
        // as reading messages. The badge only clears when the user opens a specific chat.
        refreshPreviewsFromCache()
    }

    override fun onDestroy() {
        super.onDestroy()
        SocketManager.removeNewMessageListener(newMessageListener)
        SocketManager.removeConnectionListener(connectionListener)
    }

    private fun navigateHome() {
        HomeRouter.navigate(
            context = this,
            scope = lifecycleScope,
            session = sessionManager,
            intentFlags = Intent.FLAG_ACTIVITY_REORDER_TO_FRONT
        )
    }

    // ── Load contacts ─────────────────────────────────────────────────────────

    private fun loadContacts() {
        RetrofitClient.getApiService(this)
            .getMessageContacts()
            .enqueue(object : Callback<List<com.firstapp.dormease.model.ContactUser>> {
                override fun onResponse(
                    call: Call<List<com.firstapp.dormease.model.ContactUser>>,
                    response: Response<List<com.firstapp.dormease.model.ContactUser>>
                ) {
                    if (response.isSuccessful) {
                        val contacts = response.body() ?: emptyList()
                        allConversations.clear()
                        contacts.forEach { contact ->
                            val label = buildString {
                                append(contact.full_name ?: contact.username ?: "User ${contact.id}")
                                when (contact.relation) {
                                    "tenant" -> append(" (Tenant)")
                                    "owner"  -> append(" (Owner)")
                                }
                            }
                            allConversations.add(
                                ConversationItem(userId = contact.id, userName = label)
                            )
                        }
                        refreshPreviewsFromCache()
                        filterConversations(findViewById<EditText>(R.id.etSearch).text.toString())
                        showEmpty(allConversations.isEmpty())
                    } else {
                        showEmpty(true)
                        Toast.makeText(this@MessagesActivity, "Could not load contacts", Toast.LENGTH_SHORT).show()
                    }
                }

                override fun onFailure(call: Call<List<com.firstapp.dormease.model.ContactUser>>, t: Throwable) {
                    showEmpty(true)
                    Toast.makeText(this@MessagesActivity, "Network error: ${t.message}", Toast.LENGTH_SHORT).show()
                }
            })
    }

    private fun refreshPreviewsFromCache() {
        allConversations.forEach { conv ->
            val cached = MessageRepository.getMessages(conv.userId)
            if (cached.isNotEmpty()) {
                val last = cached.last()
                conv.preview   = if (last.isMine) "You: ${last.text}" else last.text
                conv.updatedAt = last.timestamp
            }
        }
        filterConversations(findViewById<EditText>(R.id.etSearch).text.toString())
    }

    private fun handleIncomingMessage(data: JSONObject) {
        val senderId = data.optInt("senderId", -1)
        if (senderId == -1) return

        val text       = data.optString("message", "").trim()
        val senderName = data.optString("senderEmail", "User $senderId")
        val now        = System.currentTimeMillis()

        MessageRepository.addMessage(
            senderId,
            ChatMessage(text = text, timestamp = now, isMine = false)
        )

        // ── Increment the per-sender unread count in the global counter ───────
        // (MessagesActivity is open but user may be looking at a different sender)
        UnreadMessageCounter.increment(senderId)

        val existing = allConversations.find { it.userId == senderId }
        if (existing != null) {
            existing.preview      = text
            existing.unreadCount += 1
            existing.updatedAt    = now
        } else {
            allConversations.add(
                ConversationItem(
                    userId      = senderId,
                    userName    = senderName,
                    preview     = text,
                    unreadCount = 1,
                    updatedAt   = now
                )
            )
        }

        filterConversations(findViewById<EditText>(R.id.etSearch).text.toString())
        showEmpty(allConversations.isEmpty())
    }

    private fun filterConversations(query: String) {
        val filtered = if (query.isBlank()) allConversations
        else allConversations.filter {
            it.userName.contains(query, ignoreCase = true) ||
                    it.preview.contains(query, ignoreCase = true)
        }
        val sorted = filtered.sortedByDescending { it.updatedAt }
        adapter.updateList(sorted)
        showEmpty(sorted.isEmpty() && allConversations.isEmpty())
    }

    private fun openChat(conversation: ConversationItem) {
        // ── Clear per-sender unread when the user taps into the chat ──────────
        conversation.unreadCount = 0
        UnreadMessageCounter.clearFor(conversation.userId)
        filterConversations(findViewById<EditText>(R.id.etSearch).text.toString())
        startActivity(Intent(this, ChatActivity::class.java).apply {
            putExtra("EXTRA_RECIPIENT_ID",   conversation.userId)
            putExtra("EXTRA_RECIPIENT_NAME", conversation.userName)
        })
    }

    private fun showEmpty(empty: Boolean) {
        rvConversations.visibility = if (empty) View.GONE    else View.VISIBLE
        layoutEmpty.visibility     = if (empty) View.VISIBLE else View.GONE
    }

    private fun updateConnectionUI(connected: Boolean) {
        if (connected) {
            viewConnectionDot.setBackgroundResource(R.drawable.circle_dot_green)
            tvConnectionStatus.text = "Connected"
            tvConnectionStatus.setTextColor(0xFF2ECC71.toInt())
        } else {
            viewConnectionDot.setBackgroundResource(R.drawable.circle_dot_grey)
            tvConnectionStatus.text = "Offline"
            tvConnectionStatus.setTextColor(0xFF999999.toInt())
        }
    }
}