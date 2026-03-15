package com.firstapp.dormease

// FILE PATH: app/src/main/java/com/firstapp/dormease/NotificationsActivity.kt

import android.content.Context
import android.content.Intent
import android.os.Bundle
import android.os.Handler
import android.os.Looper
import android.util.Log
import android.view.LayoutInflater
import android.view.View
import android.widget.Button
import android.widget.EditText
import android.widget.LinearLayout
import android.widget.ProgressBar
import android.widget.TextView
import android.widget.Toast
import androidx.appcompat.app.AlertDialog
import androidx.appcompat.app.AppCompatActivity
import androidx.core.content.ContextCompat
import androidx.swiperefreshlayout.widget.SwipeRefreshLayout
import com.firstapp.dormease.activity.TenantDashboardActivity
import com.firstapp.dormease.model.TenantAction
import com.firstapp.dormease.model.TenantReservation
import com.firstapp.dormease.network.RetrofitClient
import com.firstapp.dormease.network.SocketManager
import com.firstapp.dormease.utils.SessionManager
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import org.json.JSONObject
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale
import java.util.TimeZone

class NotificationsActivity : AppCompatActivity() {

    private val scope   = CoroutineScope(Dispatchers.IO + SupervisorJob())
    private val handler = Handler(Looper.getMainLooper())
    private lateinit var session: SessionManager

    private lateinit var swipeRefresh    : SwipeRefreshLayout
    private lateinit var notifContainer  : LinearLayout
    private lateinit var progressBar     : ProgressBar
    private lateinit var tvEmpty         : TextView
    private lateinit var tvOfflineBanner : TextView

    // ── Read state prefs ──────────────────────────────────────────────────────
    // Key format: "read_<reservationId>"
    // Value: true = user tapped "Mark as read"
    private val readPrefs by lazy {
        getSharedPreferences("NotifReadState", Context.MODE_PRIVATE)
    }

    private val onReservationUpdate: (JSONObject) -> Unit = { data ->
        Log.d("NotificationsActivity", "Socket push received: $data")
        handler.post { fetchReservations(silent = true) }
    }

    private val pollRunnable = object : Runnable {
        override fun run() {
            fetchReservations(silent = true)
            handler.postDelayed(this, 10_000L)
        }
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_notifications)
        supportActionBar?.hide()

        session = SessionManager(this)

        swipeRefresh    = findViewById(R.id.swipeRefresh)
        notifContainer  = findViewById(R.id.notifContainer)
        progressBar     = findViewById(R.id.progressBar)
        tvEmpty         = findViewById(R.id.tvEmpty)
        tvOfflineBanner = findViewById(R.id.tvOfflineBanner)

        findViewById<TextView>(R.id.btnBack).setOnClickListener { finish() }

        // "Clear all" — marks every currently rendered card as read, then clears UI
        findViewById<TextView>(R.id.btnMarkAllRead).setOnClickListener {
            val editor = readPrefs.edit()
            for (i in 0 until notifContainer.childCount) {
                val tag = notifContainer.getChildAt(i).tag
                if (tag is Int) editor.putBoolean("read_$tag", true)
            }
            editor.apply()
            notifContainer.removeAllViews()
            tvEmpty.visibility = View.VISIBLE
            tvEmpty.text       = "No notifications yet.\nPull down to refresh."
            // Tell DashboardActivity to hide the badge
            setResult(RESULT_OK, Intent().putExtra("all_read", true))
        }

        swipeRefresh.setOnRefreshListener { fetchReservations(silent = false) }
        SocketManager.addReservationUpdateListener(onReservationUpdate)
        fetchReservations(silent = false)
    }

    override fun onResume() {
        super.onResume()
        fetchReservations(silent = true)
        handler.removeCallbacks(pollRunnable)
        handler.postDelayed(pollRunnable, 10_000L)
    }

    override fun onPause() {
        super.onPause()
        handler.removeCallbacks(pollRunnable)
    }

    override fun onDestroy() {
        SocketManager.removeReservationUpdateListener(onReservationUpdate)
        scope.cancel()
        super.onDestroy()
    }

    // ─────────────────────────────────────────────────────────────────────────
    // Fetch reservations
    // ─────────────────────────────────────────────────────────────────────────
    private fun fetchReservations(silent: Boolean) {
        if (!silent) {
            progressBar.visibility = View.VISIBLE
            tvEmpty.visibility     = View.GONE
        }

        val rawPhone = session.getPhone().trim()
        val userId   = session.getUserId()

        scope.launch {
            try {
                val api = RetrofitClient.getApiService(applicationContext)

                val reservations: List<TenantReservation> = when {
                    rawPhone.isNotBlank() -> {
                        val digits = rawPhone.filter { it.isDigit() }.takeLast(10)
                        val resp   = api.getTenantReservations("+63$digits")
                        if (resp.isSuccessful) resp.body() ?: emptyList() else emptyList()
                    }
                    userId > 0 -> {
                        val resp = api.getMyReservations()
                        if (resp.isSuccessful) {
                            val list = resp.body() ?: emptyList()
                            list.firstOrNull { it.phone.isNotBlank() }?.phone?.let { p ->
                                val digits = p.filter { it.isDigit() }.takeLast(10)
                                if (digits.isNotBlank()) session.savePhone("+63$digits")
                            }
                            list
                        } else emptyList()
                    }
                    else -> {
                        val prefs     = getSharedPreferences("NotificationState", Context.MODE_PRIVATE)
                        val lastPhone = (prefs.getString("last_phone", "") ?: "").trim()
                        if (lastPhone.isNotBlank()) {
                            val digits = lastPhone.filter { it.isDigit() }.takeLast(10)
                            val resp   = api.getTenantReservations("+63$digits")
                            if (resp.isSuccessful) resp.body() ?: emptyList() else emptyList()
                        } else emptyList()
                    }
                }

                Log.d("NotificationsActivity", "Fetched ${reservations.size} reservations")
                cacheReservations(reservations)

                withContext(Dispatchers.Main) {
                    progressBar.visibility     = View.GONE
                    swipeRefresh.isRefreshing  = false
                    tvOfflineBanner.visibility = View.GONE
                    renderCards(reservations)
                }

            } catch (e: Exception) {
                Log.e("NotificationsActivity", "Fetch error: ${e.message}")
                val cached = loadCachedReservations()

                withContext(Dispatchers.Main) {
                    progressBar.visibility    = View.GONE
                    swipeRefresh.isRefreshing = false

                    if (cached.isNotEmpty()) {
                        tvOfflineBanner.visibility = View.VISIBLE
                        tvOfflineBanner.text       = "⚠ Offline — showing saved notifications"
                        renderCards(cached)
                    } else {
                        tvOfflineBanner.visibility = View.GONE
                        notifContainer.removeAllViews()
                        tvEmpty.visibility = View.VISIBLE
                        tvEmpty.text       = "No notifications yet.\nPull down to refresh."
                    }
                }
            }
        }
    }

    // ─────────────────────────────────────────────────────────────────────────
    // Render cards
    // ─────────────────────────────────────────────────────────────────────────
    private fun renderCards(list: List<TenantReservation>) {
        notifContainer.removeAllViews()

        if (list.isEmpty()) {
            tvEmpty.visibility = View.VISIBLE
            tvEmpty.text       = "No notifications yet.\nPull down to refresh."
            updateBadge(hasUnread = false)
            return
        }

        tvEmpty.visibility = View.GONE
        val sorted = list.sortedByDescending { it.created_at ?: "" }
        for (r in sorted) notifContainer.addView(buildCard(r))

        // Update badge based on whether any unread dots are showing
        updateBadge(hasUnread = sorted.any { isUnread(it) })
    }

    // ─────────────────────────────────────────────────────────────────────────
    // Badge helper — tells DashboardActivity via result intent
    // ─────────────────────────────────────────────────────────────────────────
    private fun updateBadge(hasUnread: Boolean) {
        setResult(RESULT_OK, Intent().putExtra("has_unread", hasUnread))
    }

    // ─────────────────────────────────────────────────────────────────────────
    // Whether a reservation is considered "unread"
    // ─────────────────────────────────────────────────────────────────────────
    private fun isUnread(r: TenantReservation): Boolean {
        if (readPrefs.getBoolean("read_${r.id}", false)) return false
        return when (r.status) {
            "approved" -> r.tenant_action.isNullOrBlank()
            "rejected", "archived" -> true
            else -> false
        }
    }

    // ─────────────────────────────────────────────────────────────────────────
    // Build a single notification card
    // ─────────────────────────────────────────────────────────────────────────
    private fun buildCard(r: TenantReservation): View {
        val view = LayoutInflater.from(this)
            .inflate(R.layout.item_notification_card, notifContainer, false)

        // Tag the view with reservation ID so "Clear all" can mark it read
        view.tag = r.id

        val unreadDot      = view.findViewById<View>(R.id.unreadDot)
        val tvTitle        = view.findViewById<TextView>(R.id.tvNotifTitle)
        val tvTime         = view.findViewById<TextView>(R.id.tvNotifTime)
        val tvMessage      = view.findViewById<TextView>(R.id.tvNotifMessage)
        val tvStatusBadge  = view.findViewById<TextView>(R.id.tvStatusBadge)
        val btnViewDetails = view.findViewById<Button>(R.id.btnViewDetails)
        val btnCancel      = view.findViewById<Button>(R.id.btnCancel)
        val btnAccept      = view.findViewById<Button>(R.id.btnAccept)
        val btnMarkRead    = view.findViewById<TextView>(R.id.btnMarkRead)

        tvTime.text = formatRelativeTime(r.created_at)

        // Default: hide all action buttons
        btnAccept.visibility   = View.GONE
        btnCancel.visibility   = View.GONE
        unreadDot.visibility   = View.GONE
        btnMarkRead.visibility = View.GONE

        // ── Set content by status ─────────────────────────────────────────────
        when (r.status) {
            "approved" -> {
                tvTitle.text       = "Reservation Approved! 🎉"
                tvMessage.text     = "Your reservation at ${r.dorm_name} has been approved by the owner."
                tvStatusBadge.text = "✓ Approved by Owner"
                tvStatusBadge.setTextColor(ContextCompat.getColor(this, android.R.color.holo_green_dark))
                try { tvStatusBadge.setBackgroundResource(R.drawable.badge_approved) } catch (_: Exception) {}

                if (r.tenant_action.isNullOrBlank()) {
                    btnAccept.visibility = View.VISIBLE
                    btnCancel.visibility = View.VISIBLE
                }
            }
            "rejected" -> {
                val reason         = r.rejection_reason?.takeIf { it.isNotBlank() } ?: "No reason provided."
                tvTitle.text       = "Reservation Rejected"
                tvMessage.text     = "Your reservation at ${r.dorm_name} was rejected.\nReason: $reason"
                tvStatusBadge.text = "✗ Rejected by Owner"
                tvStatusBadge.setTextColor(ContextCompat.getColor(this, android.R.color.holo_red_dark))
                try { tvStatusBadge.setBackgroundResource(R.drawable.badge_rejected) } catch (_: Exception) {}
            }
            "pending" -> {
                tvTitle.text       = "Reservation Pending"
                tvMessage.text     = "Your reservation at ${r.dorm_name} is waiting for owner approval."
                tvStatusBadge.text = "⏳ Awaiting Owner Review"
                tvStatusBadge.setTextColor(ContextCompat.getColor(this, android.R.color.darker_gray))
                try { tvStatusBadge.setBackgroundResource(R.drawable.badge_confirmed) } catch (_: Exception) {}
            }
            "archived" -> {
                val reason   = r.termination_reason?.takeIf { it.isNotBlank() } ?: "No reason provided."
                tvTitle.text = "⚠ Tenancy Terminated"
                tvMessage.text = "Property: ${r.dorm_name}\nReason: $reason"
                tvStatusBadge.text = "✗ Terminated by Owner"
                tvStatusBadge.setTextColor(ContextCompat.getColor(this, android.R.color.holo_red_dark))
                try { tvStatusBadge.setBackgroundResource(R.drawable.badge_rejected) } catch (_: Exception) {}

                btnCancel.visibility = View.VISIBLE
                btnAccept.visibility = View.VISIBLE
                btnCancel.text = "VIEW"
                try { btnCancel.setBackgroundResource(R.drawable.btn_outline_blue) } catch (_: Exception) {}
                btnCancel.setOnClickListener { showDetailsDialog(r) }

                // ── Appeal button: label changes if appeal already submitted ──
                if (!r.appeal_message.isNullOrBlank()) {
                    btnAccept.text = "APPEAL SENT ✓"
                    try { btnAccept.setBackgroundResource(R.drawable.btn_outline_blue) } catch (_: Exception) {}
                    btnAccept.isEnabled = false
                    btnAccept.alpha = 0.6f
                } else {
                    btnAccept.text = "APPEAL"
                    try { btnAccept.setBackgroundResource(R.drawable.btn_filled_blue) } catch (_: Exception) {}
                    btnAccept.isEnabled = true
                    btnAccept.alpha = 1f
                    btnAccept.setOnClickListener { showAppealDialog(r) }
                }

                btnViewDetails.visibility = View.GONE

                applyReadState(r, unreadDot, btnMarkRead, view)
                return view
            }
            else -> {
                tvTitle.text       = "Reservation Update"
                tvMessage.text     = "Status: ${r.status}"
                tvStatusBadge.text = r.status
                tvStatusBadge.setTextColor(ContextCompat.getColor(this, android.R.color.darker_gray))
            }
        }

        // ── Override badge if tenant already responded ────────────────────────
        when (r.tenant_action) {
            "accepted" -> {
                btnAccept.visibility = View.GONE
                btnCancel.visibility = View.GONE
                tvStatusBadge.text   = "✓ You Accepted"
                tvStatusBadge.setTextColor(ContextCompat.getColor(this, android.R.color.holo_green_dark))
                try { tvStatusBadge.setBackgroundResource(R.drawable.badge_approved) } catch (_: Exception) {}
            }
            "cancelled" -> {
                btnAccept.visibility = View.GONE
                btnCancel.visibility = View.GONE
                val reason           = r.cancel_reason?.takeIf { it.isNotBlank() } ?: "No reason."
                tvStatusBadge.text   = "✗ You Cancelled · $reason"
                tvStatusBadge.setTextColor(ContextCompat.getColor(this, android.R.color.holo_red_dark))
                try { tvStatusBadge.setBackgroundResource(R.drawable.badge_rejected) } catch (_: Exception) {}
            }
        }

        // ── Generic button listeners ──────────────────────────────────────────
        btnViewDetails.setOnClickListener { showDetailsDialog(r) }

        btnAccept.setOnClickListener { showAcceptConfirmDialog(r) }

        btnCancel.setOnClickListener {
            val input = EditText(this).apply {
                hint = "Reason for cancellation"
                setPadding(48, 32, 48, 32)
            }
            AlertDialog.Builder(this)
                .setTitle("Cancel Reservation")
                .setMessage("Please provide a reason:")
                .setView(input)
                .setPositiveButton("Confirm Cancel") { _, _ ->
                    val reason = input.text.toString().trim()
                    if (reason.isBlank()) {
                        Toast.makeText(this, "Reason is required.", Toast.LENGTH_SHORT).show()
                    } else {
                        sendTenantAction(r, "cancelled", reason, navigateToDashboard = false)
                    }
                }
                .setNegativeButton("Back", null)
                .show()
        }

        applyReadState(r, unreadDot, btnMarkRead, view)
        return view
    }

    // ─────────────────────────────────────────────────────────────────────────
    // Apply read/unread state to a card's dot and mark-as-read button
    // ─────────────────────────────────────────────────────────────────────────
    private fun applyReadState(
        r: TenantReservation,
        unreadDot: View,
        btnMarkRead: TextView,
        cardView: View
    ) {
        val unread = isUnread(r)
        unreadDot.visibility   = if (unread) View.VISIBLE else View.GONE
        btnMarkRead.visibility = if (unread) View.VISIBLE else View.GONE

        if (unread) {
            btnMarkRead.setOnClickListener {
                // Persist read state
                readPrefs.edit().putBoolean("read_${r.id}", true).apply()

                // Hide dot + button on this card
                unreadDot.visibility   = View.GONE
                btnMarkRead.visibility = View.GONE

                // Check if any other cards still have an unread dot
                val anyUnread = (0 until notifContainer.childCount).any { i ->
                    notifContainer.getChildAt(i)
                        .findViewById<View>(R.id.unreadDot)
                        ?.visibility == View.VISIBLE
                }
                updateBadge(hasUnread = anyUnread)
            }
        }
    }

    // ─────────────────────────────────────────────────────────────────────────
    // Appeal dialog — shows existing appeal read-only if already submitted
    // ─────────────────────────────────────────────────────────────────────────
    private fun showAppealDialog(r: TenantReservation) {
        val alreadySubmitted = !r.appeal_message.isNullOrBlank()

        val input = EditText(this).apply {
            hint     = "Describe your appeal..."
            setPadding(48, 32, 48, 32)
            minLines = 3
            if (alreadySubmitted) {
                setText(r.appeal_message)
                isEnabled = false
            }
        }

        AlertDialog.Builder(this)
            .setTitle(if (alreadySubmitted) "Appeal Already Submitted" else "Submit Appeal")
            .setMessage(
                if (alreadySubmitted)
                    "You have already submitted an appeal for ${r.dorm_name}. " +
                            "The owner has been notified."
                else
                    "Your appeal for ${r.dorm_name} will be sent to the owner " +
                            "via notification and email."
            )
            .setView(input)
            .setPositiveButton(if (alreadySubmitted) "Close" else "Send Appeal") { _, _ ->
                if (alreadySubmitted) return@setPositiveButton
                val appeal = input.text.toString().trim()
                if (appeal.isBlank()) {
                    Toast.makeText(this, "Please write your appeal first.", Toast.LENGTH_SHORT).show()
                } else {
                    sendAppeal(r, appeal)
                }
            }
            .apply {
                if (!alreadySubmitted) setNegativeButton("Cancel", null)
            }
            .show()
    }

    // ─────────────────────────────────────────────────────────────────────────
    // Send appeal to server
    // ─────────────────────────────────────────────────────────────────────────
    private fun sendAppeal(r: TenantReservation, appealMessage: String) {
        val phone = r.phone.ifBlank { session.getPhone() }
        if (phone.isBlank()) {
            Toast.makeText(
                this,
                "Phone number not found. Cannot submit appeal.",
                Toast.LENGTH_SHORT
            ).show()
            return
        }

        val progressDialog = AlertDialog.Builder(this)
            .setMessage("Submitting your appeal...")
            .setCancelable(false)
            .create()
        progressDialog.show()

        scope.launch {
            try {
                val resp = RetrofitClient.getApiService(applicationContext)
                    .submitAppeal(
                        r.id,
                        mapOf(
                            "phone"          to phone,
                            "appeal_message" to appealMessage
                        )
                    )

                withContext(Dispatchers.Main) {
                    progressDialog.dismiss()
                    if (resp.isSuccessful) {
                        Toast.makeText(
                            this@NotificationsActivity,
                            "✅ Appeal submitted! The owner has been notified.",
                            Toast.LENGTH_LONG
                        ).show()
                        // Refresh cards so the APPEAL button disables itself
                        fetchReservations(silent = true)
                    } else {
                        val errorBody = resp.errorBody()?.string() ?: "Unknown error"
                        Toast.makeText(
                            this@NotificationsActivity,
                            "Failed to submit appeal: $errorBody",
                            Toast.LENGTH_LONG
                        ).show()
                    }
                }
            } catch (e: Exception) {
                withContext(Dispatchers.Main) {
                    progressDialog.dismiss()
                    Toast.makeText(
                        this@NotificationsActivity,
                        "Network error: ${e.message}",
                        Toast.LENGTH_SHORT
                    ).show()
                }
            }
        }
    }

    // ─────────────────────────────────────────────────────────────────────────
    // Accept confirmation dialog
    // ─────────────────────────────────────────────────────────────────────────
    private fun showAcceptConfirmDialog(r: TenantReservation) {
        AlertDialog.Builder(this)
            .setTitle("Accept Reservation")
            .setMessage(
                "Accept your reservation at ${r.dorm_name}?\n\n" +
                        "Move-in: ${r.move_in_date ?: "—"}\n" +
                        "Monthly rent: ₱${r.price_per_month ?: "—"}"
            )
            .setPositiveButton("Yes, Accept") { _, _ ->
                sendTenantAction(r, "accepted", null, navigateToDashboard = true)
            }
            .setNegativeButton("Not Yet", null)
            .show()
    }

    // ─────────────────────────────────────────────────────────────────────────
    // Send tenant action
    // ─────────────────────────────────────────────────────────────────────────
    private fun sendTenantAction(
        r: TenantReservation,
        action: String,
        cancelReason: String?,
        navigateToDashboard: Boolean
    ) {
        val phone = session.getPhone().trim().ifBlank { r.phone }
        if (action == "accepted" && phone.isNotBlank()) session.savePhone(phone)

        scope.launch {
            try {
                val resp = RetrofitClient.getApiService(applicationContext)
                    .sendTenantAction(
                        r.id,
                        TenantAction(action = action, phone = phone, cancel_reason = cancelReason)
                    )
                withContext(Dispatchers.Main) {
                    if (resp.isSuccessful) {
                        if (action == "accepted" && navigateToDashboard) {
                            Toast.makeText(
                                this@NotificationsActivity,
                                "Reservation accepted! Welcome to your new home 🏠",
                                Toast.LENGTH_LONG
                            ).show()
                            startActivity(
                                Intent(this@NotificationsActivity, TenantDashboardActivity::class.java).apply {
                                    flags = Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TASK
                                    putExtra(TenantDashboardActivity.EXTRA_FORCE_CONFIRMED, true)
                                    putExtra(TenantDashboardActivity.EXTRA_RESERVATION_ID, r.id)
                                }
                            )
                        } else {
                            Toast.makeText(this@NotificationsActivity, "Reservation cancelled.", Toast.LENGTH_SHORT).show()
                            fetchReservations(silent = true)
                        }
                    } else {
                        Toast.makeText(
                            this@NotificationsActivity,
                            "Action failed (${resp.code()}). Please try again.",
                            Toast.LENGTH_SHORT
                        ).show()
                    }
                }
            } catch (e: Exception) {
                withContext(Dispatchers.Main) {
                    Toast.makeText(this@NotificationsActivity, "Network error.", Toast.LENGTH_SHORT).show()
                }
            }
        }
    }

    // ─────────────────────────────────────────────────────────────────────────
    // View Details dialog
    // ─────────────────────────────────────────────────────────────────────────
    private fun showDetailsDialog(r: TenantReservation) {
        val msg = buildString {
            appendLine("🏠 Property: ${r.dorm_name}")
            appendLine("📍 Location: ${r.location ?: "—"}")
            appendLine("👤 Tenant: ${r.full_name}")
            appendLine("📅 Move-in Date: ${r.move_in_date ?: "—"}")
            appendLine("📆 Duration: ${r.duration_months ?: "—"} month(s)")
            appendLine("💵 Monthly Rent: ₱${r.price_per_month ?: "—"}")
            appendLine("🔒 Deposit: ₱${r.deposit ?: "—"}")
            appendLine("⬆ Advance: ₱${r.advance ?: "—"}")
            appendLine("💳 Payment Method: ${r.payment_method ?: "—"}")
            if (!r.notes.isNullOrBlank())              appendLine("📝 Notes: ${r.notes}")
            if (!r.rejection_reason.isNullOrBlank())   appendLine("❌ Rejection Reason: ${r.rejection_reason}")
            if (!r.termination_reason.isNullOrBlank()) appendLine("⚠ Termination Reason: ${r.termination_reason}")
            if (!r.appeal_message.isNullOrBlank())     appendLine("📋 Your Appeal: ${r.appeal_message}")
        }
        AlertDialog.Builder(this)
            .setTitle("Reservation Details")
            .setMessage(msg.trim())
            .setPositiveButton("Close", null)
            .show()
    }

    // ─────────────────────────────────────────────────────────────────────────
    // Relative time formatter
    // ─────────────────────────────────────────────────────────────────────────
    private fun formatRelativeTime(isoDate: String?): String {
        if (isoDate.isNullOrBlank()) return ""
        return try {
            val formats = listOf(
                "yyyy-MM-dd'T'HH:mm:ss.SSS'Z'",
                "yyyy-MM-dd'T'HH:mm:ss'Z'",
                "yyyy-MM-dd"
            )
            var date: Date? = null
            for (fmt in formats) {
                date = try {
                    SimpleDateFormat(fmt, Locale.US).apply { timeZone = TimeZone.getTimeZone("UTC") }.parse(isoDate)
                } catch (_: Exception) { null }
                if (date != null) break
            }
            if (date == null) return isoDate.take(10)
            val diff  = System.currentTimeMillis() - date.time
            val mins  = diff / 60_000
            val hours = diff / 3_600_000
            val days  = diff / 86_400_000
            when {
                mins  < 1  -> "Just now"
                mins  < 60 -> "${mins}m ago"
                hours < 24 -> "${hours}h ago"
                days  < 7  -> "${days}d ago"
                else       -> SimpleDateFormat("MMM dd, yyyy", Locale.US).format(date)
            }
        } catch (_: Exception) { isoDate.take(10) }
    }

    // ─────────────────────────────────────────────────────────────────────────
    // Offline cache helpers
    // ─────────────────────────────────────────────────────────────────────────
    private fun cacheReservations(list: List<TenantReservation>) {
        if (list.isEmpty()) return
        try {
            val phone = session.getPhone().trim().filter { it.isDigit() }.takeLast(10)
            if (phone.isBlank()) return
            getSharedPreferences("NotificationState", Context.MODE_PRIVATE)
                .edit().putString("cache_$phone", com.google.gson.Gson().toJson(list)).apply()
        } catch (e: Exception) { Log.w("NotificationsActivity", "Cache write failed: ${e.message}") }
    }

    private fun loadCachedReservations(): List<TenantReservation> {
        return try {
            val phone = session.getPhone().trim().filter { it.isDigit() }.takeLast(10)
            if (phone.isBlank()) return emptyList()
            val json = getSharedPreferences("NotificationState", Context.MODE_PRIVATE)
                .getString("cache_$phone", null) ?: return emptyList()
            val type = object : com.google.gson.reflect.TypeToken<List<TenantReservation>>() {}.type
            com.google.gson.Gson().fromJson(json, type) ?: emptyList()
        } catch (e: Exception) { emptyList() }
    }
}