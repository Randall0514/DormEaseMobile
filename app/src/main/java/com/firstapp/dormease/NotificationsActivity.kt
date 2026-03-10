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

        findViewById<TextView>(R.id.btnMarkAllRead).setOnClickListener {
            notifContainer.removeAllViews()
            tvEmpty.visibility = View.VISIBLE
            tvEmpty.text       = "No notifications yet.\nPull down to refresh."
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
    // Fetch reservations from server
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
                reservations.forEach {
                    Log.d("NotificationsActivity", "  id=${it.id} status=${it.status} tenant_action=${it.tenant_action}")
                }

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
            return
        }

        tvEmpty.visibility = View.GONE

        val sorted = list.sortedByDescending { it.created_at ?: "" }
        for (r in sorted) {
            notifContainer.addView(buildCard(r))
        }
    }

    // ─────────────────────────────────────────────────────────────────────────
    // Build a single notification card
    // ─────────────────────────────────────────────────────────────────────────
    private fun buildCard(r: TenantReservation): View {
        val view = LayoutInflater.from(this)
            .inflate(R.layout.item_notification_card, notifContainer, false)

        val unreadDot      = view.findViewById<View>(R.id.unreadDot)
        val tvTitle        = view.findViewById<TextView>(R.id.tvNotifTitle)
        val tvTime         = view.findViewById<TextView>(R.id.tvNotifTime)
        val tvMessage      = view.findViewById<TextView>(R.id.tvNotifMessage)
        val tvStatusBadge  = view.findViewById<TextView>(R.id.tvStatusBadge)
        val btnViewDetails = view.findViewById<Button>(R.id.btnViewDetails)
        val btnCancel      = view.findViewById<Button>(R.id.btnCancel)
        val btnAccept      = view.findViewById<Button>(R.id.btnAccept)

        tvTime.text = formatRelativeTime(r.created_at)

        // Default: hide all action buttons
        btnAccept.visibility = View.GONE
        btnCancel.visibility = View.GONE
        unreadDot.visibility = View.GONE

        when (r.status) {
            "approved" -> {
                tvTitle.text       = "Reservation Approved! 🎉"
                tvMessage.text     = "Your reservation at ${r.dorm_name} has been approved by the owner."
                tvStatusBadge.text = "✓ Approved by Owner"
                tvStatusBadge.setTextColor(ContextCompat.getColor(this, android.R.color.holo_green_dark))
                try { tvStatusBadge.setBackgroundResource(R.drawable.badge_approved) } catch (_: Exception) {}
                unreadDot.visibility = View.VISIBLE

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
                unreadDot.visibility = View.VISIBLE
            }
            "pending" -> {
                tvTitle.text       = "Reservation Pending"
                tvMessage.text     = "Your reservation at ${r.dorm_name} is waiting for owner approval."
                tvStatusBadge.text = "⏳ Awaiting Owner Review"
                tvStatusBadge.setTextColor(ContextCompat.getColor(this, android.R.color.darker_gray))
                try { tvStatusBadge.setBackgroundResource(R.drawable.badge_confirmed) } catch (_: Exception) {}
            }
            "archived" -> {
                // ── ⚠ Tenancy Terminated card ─────────────────────────────────
                val reason = r.termination_reason?.takeIf { it.isNotBlank() } ?: "No reason provided."

                tvTitle.text   = "⚠ Tenancy Terminated"
                tvMessage.text = "Property: ${r.dorm_name}\nReason: $reason"

                tvStatusBadge.text = "✗ Terminated by Owner"
                tvStatusBadge.setTextColor(ContextCompat.getColor(this, android.R.color.holo_red_dark))
                try { tvStatusBadge.setBackgroundResource(R.drawable.badge_rejected) } catch (_: Exception) {}
                unreadDot.visibility = View.VISIBLE

                // VIEW + APPEAL buttons
                btnCancel.visibility = View.VISIBLE
                btnAccept.visibility = View.VISIBLE

                // Repurpose btnCancel → VIEW
                btnCancel.text = "VIEW"
                btnCancel.setTextColor(ContextCompat.getColor(this, android.R.color.white))
                try { btnCancel.setBackgroundResource(R.drawable.btn_outline_blue) } catch (_: Exception) {}
                btnCancel.setOnClickListener { showDetailsDialog(r) }

                // Repurpose btnAccept → APPEAL
                btnAccept.text = "APPEAL"
                try { btnAccept.setBackgroundResource(R.drawable.btn_filled_blue) } catch (_: Exception) {}
                btnAccept.setOnClickListener { showAppealDialog(r) }

                // Override the generic listeners set later — return early
                btnViewDetails.visibility = View.GONE
                return view
            }
            else -> {
                tvTitle.text       = "Reservation Update"
                tvMessage.text     = "Status: ${r.status}"
                tvStatusBadge.text = r.status
                tvStatusBadge.setTextColor(ContextCompat.getColor(this, android.R.color.darker_gray))
            }
        }

        // Override badge if tenant already responded (non-archived only)
        when (r.tenant_action) {
            "accepted" -> {
                btnAccept.visibility = View.GONE
                btnCancel.visibility = View.GONE
                unreadDot.visibility = View.GONE
                tvStatusBadge.text   = "✓ You Accepted"
                tvStatusBadge.setTextColor(ContextCompat.getColor(this, android.R.color.holo_green_dark))
                try { tvStatusBadge.setBackgroundResource(R.drawable.badge_approved) } catch (_: Exception) {}
            }
            "cancelled" -> {
                btnAccept.visibility = View.GONE
                btnCancel.visibility = View.GONE
                unreadDot.visibility = View.GONE
                val reason           = r.cancel_reason?.takeIf { it.isNotBlank() } ?: "No reason."
                tvStatusBadge.text   = "✗ You Cancelled · $reason"
                tvStatusBadge.setTextColor(ContextCompat.getColor(this, android.R.color.holo_red_dark))
                try { tvStatusBadge.setBackgroundResource(R.drawable.badge_rejected) } catch (_: Exception) {}
            }
        }

        // Generic button listeners for non-archived cards
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

        return view
    }

    // ─────────────────────────────────────────────────────────────────────────
    // Appeal dialog (for terminated tenancy)
    // ─────────────────────────────────────────────────────────────────────────
    private fun showAppealDialog(r: TenantReservation) {
        val input = EditText(this).apply {
            hint = "Describe your appeal..."
            setPadding(48, 32, 48, 32)
            minLines = 3
        }
        AlertDialog.Builder(this)
            .setTitle("Submit Appeal")
            .setMessage("Your appeal for ${r.dorm_name} will be sent to the owner.")
            .setView(input)
            .setPositiveButton("Send Appeal") { _, _ ->
                val appeal = input.text.toString().trim()
                if (appeal.isBlank()) {
                    Toast.makeText(this, "Please write your appeal first.", Toast.LENGTH_SHORT).show()
                } else {
                    // For now, show a confirmation — hook into your backend when ready
                    Toast.makeText(this, "Appeal submitted. The owner will be notified.", Toast.LENGTH_LONG).show()
                }
            }
            .setNegativeButton("Cancel", null)
            .show()
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

        if (action == "accepted" && phone.isNotBlank()) {
            session.savePhone(phone)
        }

        scope.launch {
            try {
                val resp = RetrofitClient.getApiService(applicationContext)
                    .sendTenantAction(
                        r.id,
                        TenantAction(
                            action        = action,
                            phone         = phone,
                            cancel_reason = cancelReason
                        )
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
                            Toast.makeText(
                                this@NotificationsActivity,
                                "Reservation cancelled.",
                                Toast.LENGTH_SHORT
                            ).show()
                            fetchReservations(silent = true)
                        }
                    } else {
                        val errBody = resp.errorBody()?.string() ?: "Unknown error"
                        Log.e("NotificationsActivity", "sendTenantAction failed: ${resp.code()} $errBody")
                        Toast.makeText(
                            this@NotificationsActivity,
                            "Action failed (${resp.code()}). Please try again.",
                            Toast.LENGTH_SHORT
                        ).show()
                    }
                }
            } catch (e: Exception) {
                Log.e("NotificationsActivity", "sendTenantAction error: ${e.message}")
                withContext(Dispatchers.Main) {
                    Toast.makeText(
                        this@NotificationsActivity,
                        "Network error. Please check your connection.",
                        Toast.LENGTH_SHORT
                    ).show()
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
            if (!r.notes.isNullOrBlank())
                appendLine("📝 Notes: ${r.notes}")
            if (!r.rejection_reason.isNullOrBlank())
                appendLine("❌ Rejection Reason: ${r.rejection_reason}")
            if (!r.termination_reason.isNullOrBlank())
                appendLine("⚠ Termination Reason: ${r.termination_reason}")
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
                    SimpleDateFormat(fmt, Locale.US)
                        .apply { timeZone = TimeZone.getTimeZone("UTC") }
                        .parse(isoDate)
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
            val prefs = getSharedPreferences("NotificationState", Context.MODE_PRIVATE)
            prefs.edit().putString("cache_$phone", com.google.gson.Gson().toJson(list)).apply()
        } catch (e: Exception) {
            Log.w("NotificationsActivity", "Cache write failed: ${e.message}")
        }
    }

    private fun loadCachedReservations(): List<TenantReservation> {
        return try {
            val phone = session.getPhone().trim().filter { it.isDigit() }.takeLast(10)
            if (phone.isBlank()) return emptyList()
            val prefs = getSharedPreferences("NotificationState", Context.MODE_PRIVATE)
            val json  = prefs.getString("cache_$phone", null) ?: return emptyList()
            val type  = object : com.google.gson.reflect.TypeToken<List<TenantReservation>>() {}.type
            com.google.gson.Gson().fromJson(json, type) ?: emptyList()
        } catch (e: Exception) { emptyList() }
    }
}