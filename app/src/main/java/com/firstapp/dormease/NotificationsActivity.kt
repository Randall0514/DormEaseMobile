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
import com.google.android.material.bottomsheet.BottomSheetDialog
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

    private data class NotificationTag(
        val reservationId: Int,
        val hiddenKey: String
    )

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

    private val hiddenPrefs by lazy {
        getSharedPreferences("NotifHiddenState", Context.MODE_PRIVATE)
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

        // "Clear all" — confirms intent, then persists hidden/read state.
        findViewById<TextView>(R.id.btnMarkAllRead).setOnClickListener {
            AlertDialog.Builder(this)
                .setTitle("Clear all notifications?")
                .setMessage("This will hide all current notifications on this device.")
                .setPositiveButton("Yes, clear all") { _, _ ->
                    val readEditor = readPrefs.edit()
                    val hiddenEditor = hiddenPrefs.edit()

                    for (i in 0 until notifContainer.childCount) {
                        val tag = notifContainer.getChildAt(i).tag
                        if (tag is NotificationTag) {
                            readEditor.putBoolean("read_${tag.reservationId}", true)
                            hiddenEditor.putBoolean(tag.hiddenKey, true)
                        }
                    }

                    readEditor.apply()
                    hiddenEditor.apply()

                    notifContainer.removeAllViews()
                    tvEmpty.visibility = View.VISIBLE
                    tvEmpty.text = "No notifications yet.\nPull down to refresh."

                    // Tell parent screens to hide badge immediately.
                    setResult(RESULT_OK, Intent().putExtra("all_read", true))
                    updateBadge(hasUnread = false)
                }
                .setNegativeButton("Cancel", null)
                .show()
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

        scope.launch {
            try {
                val api = RetrofitClient.getApiService(applicationContext)
                val resp = api.getMyReservations()
                val reservations: List<TenantReservation> = if (resp.isSuccessful) {
                    val list = resp.body() ?: emptyList()
                    list.firstOrNull { it.phone.isNotBlank() }?.phone?.let { p ->
                        val digits = p.filter { it.isDigit() }.takeLast(10)
                        if (digits.isNotBlank()) session.savePhone("+63$digits")
                    }
                    list
                } else {
                    emptyList()
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

        val visibleItems = list
            .sortedByDescending { it.created_at ?: "" }
            .filterNot { hiddenPrefs.getBoolean(hiddenKeyFor(it), false) }

        if (visibleItems.isEmpty()) {
            tvEmpty.visibility = View.VISIBLE
            tvEmpty.text       = "No notifications yet.\nPull down to refresh."
            updateBadge(hasUnread = false)
            return
        }

        tvEmpty.visibility = View.GONE
        for (r in visibleItems) notifContainer.addView(buildCard(r))

        // Update badge based on whether any unread dots are showing
        updateBadge(hasUnread = visibleItems.any { isUnread(it) })
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
        return !readPrefs.getBoolean("read_${r.id}", false)
    }

    private fun hiddenKeyFor(r: TenantReservation): String {
        val token = listOf(
            r.id.toString(),
            r.status,
            r.tenant_action.orEmpty(),
            r.tenant_action_at.orEmpty(),
            r.rejection_reason.orEmpty(),
            r.termination_reason.orEmpty(),
            r.appeal_submitted_at.orEmpty(),
            r.appeal_dismissed_at.orEmpty()
        ).joinToString("|")
        return "hidden_${token.hashCode()}"
    }

    private fun isAppealDismissed(r: TenantReservation): Boolean {
        return !r.appeal_dismissed_at.isNullOrBlank()
    }

    // ─────────────────────────────────────────────────────────────────────────
    // Build a single notification card
    // ─────────────────────────────────────────────────────────────────────────
    private fun buildCard(r: TenantReservation): View {
        val view = LayoutInflater.from(this)
            .inflate(R.layout.item_notification_card, notifContainer, false)

        // Tag with both reservation id and a stable hidden key.
        view.tag = NotificationTag(
            reservationId = r.id,
            hiddenKey = hiddenKeyFor(r)
        )

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

                // Tenant can cancel while request is still pending.
                btnCancel.visibility = View.VISIBLE
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

                // Keep appeal closed after owner dismisses it.
                if (isAppealDismissed(r)) {
                    btnAccept.text = "APPEAL DISMISSED"
                    try { btnAccept.setBackgroundResource(R.drawable.btn_outline_blue) } catch (_: Exception) {}
                    btnAccept.isEnabled = false
                    btnAccept.alpha = 0.6f
                } else if (!r.appeal_message.isNullOrBlank()) {
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

        btnCancel.setOnClickListener { showCancelDialog(r) }

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
                readPrefs.edit().putBoolean("read_${r.id}", true).commit()

                // Hide dot + button on this card
                unreadDot.visibility   = View.GONE
                btnMarkRead.visibility = View.GONE

                // Recompute unread state across all visible cards.
                updateBadge(hasUnread = hasAnyUnreadVisibleCards())
            }
        }
    }

    private fun hasAnyUnreadVisibleCards(): Boolean {
        for (i in 0 until notifContainer.childCount) {
            val tag = notifContainer.getChildAt(i).tag
            if (tag is NotificationTag && !readPrefs.getBoolean("read_${tag.reservationId}", false)) {
                return true
            }
        }
        return false
    }

    // ─────────────────────────────────────────────────────────────────────────
    // Appeal dialog — shows existing appeal read-only if already submitted
    // ─────────────────────────────────────────────────────────────────────────
    private fun showAppealDialog(r: TenantReservation) {
        val appealDismissed = isAppealDismissed(r)
        val alreadySubmitted = !r.appeal_message.isNullOrBlank()

        val input = EditText(this).apply {
            hint     = "Describe your appeal..."
            setPadding(48, 32, 48, 32)
            minLines = 3
            if (appealDismissed) {
                setText("Your previous appeal was dismissed by the owner.")
                isEnabled = false
            } else if (alreadySubmitted) {
                setText(r.appeal_message)
                isEnabled = false
            }
        }

        AlertDialog.Builder(this)
            .setTitle(
                when {
                    appealDismissed -> "Appeal Unavailable"
                    alreadySubmitted -> "Appeal Already Submitted"
                    else -> "Submit Appeal"
                }
            )
            .setMessage(
                when {
                    appealDismissed ->
                        "The owner already dismissed your previous appeal for ${r.dorm_name}. " +
                                "You can no longer submit another appeal."
                    alreadySubmitted ->
                    "You have already submitted an appeal for ${r.dorm_name}. " +
                            "The owner has been notified."
                    else ->
                    "Your appeal for ${r.dorm_name} will be sent to the owner " +
                            "via notification and email."
                }
            )
            .setView(input)
            .setPositiveButton(if (appealDismissed || alreadySubmitted) "Close" else "Send Appeal") { _, _ ->
                if (appealDismissed || alreadySubmitted) return@setPositiveButton
                val appeal = input.text.toString().trim()
                if (appeal.isBlank()) {
                    Toast.makeText(this, "Please write your appeal first.", Toast.LENGTH_SHORT).show()
                } else {
                    sendAppeal(r, appeal)
                }
            }
            .apply {
                if (!appealDismissed && !alreadySubmitted) setNegativeButton("Cancel", null)
            }
            .show()
    }

    // ─────────────────────────────────────────────────────────────────────────
    // Send appeal to server
    // ─────────────────────────────────────────────────────────────────────────
    private fun sendAppeal(r: TenantReservation, appealMessage: String) {
        val email = session.getEmail().trim().ifBlank { r.tenant_email?.trim().orEmpty() }
        val phone = r.phone.ifBlank { session.getPhone() }
        if (email.isBlank() && phone.isBlank()) {
            Toast.makeText(
                this,
                "Tenant identity not found. Cannot submit appeal.",
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
                        buildMap {
                            put("appeal_message", appealMessage)
                            if (email.isNotBlank()) put("email", email)
                            if (email.isBlank() && phone.isNotBlank()) put("phone", phone)
                        }
                    )

                withContext(Dispatchers.Main) {
                    progressDialog.dismiss()
                    if (resp.isSuccessful) {
                        Toast.makeText(
                            this@NotificationsActivity,
                            "✅ Appeal submitted! The owner has been notified.",
                            Toast.LENGTH_LONG
                        ).show()
                        // Refresh cards so the appeal button state updates immediately.
                        fetchReservations(silent = true)
                    } else {
                        val errorBody = resp.errorBody()?.string().orEmpty()
                        val message = if (resp.code() == 403) {
                            "Appeal unavailable. The owner already dismissed your previous appeal."
                        } else {
                            errorBody.ifBlank { "Unknown error" }
                        }
                        Toast.makeText(
                            this@NotificationsActivity,
                            "Failed to submit appeal: $message",
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
        val email = session.getEmail().trim().ifBlank { r.tenant_email?.trim().orEmpty() }
        val phone = session.getPhone().trim().ifBlank { r.phone }
        if (action == "accepted" && phone.isNotBlank()) session.savePhone(phone)

        scope.launch {
            try {
                val resp = RetrofitClient.getApiService(applicationContext)
                    .sendTenantAction(
                        r.id,
                        TenantAction(
                            action = action,
                            email = email.ifBlank { null },
                            phone = phone.ifBlank { null },
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
                        } else if (action == "cancelled") {
                            Toast.makeText(
                                this@NotificationsActivity,
                                "Reservation cancelled. You can browse available dorms again.",
                                Toast.LENGTH_LONG
                            ).show()
                            startActivity(
                                Intent(this@NotificationsActivity, DashboardActivity::class.java).apply {
                                    flags = Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TASK
                                }
                            )
                            finish()
                        } else {
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

    private fun showCancelDialog(r: TenantReservation) {
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

    // ─────────────────────────────────────────────────────────────────────────
    // View Details dialog
    // ─────────────────────────────────────────────────────────────────────────
    private fun showDetailsDialog(r: TenantReservation) {
        val dialog = BottomSheetDialog(this)
        val view = layoutInflater.inflate(R.layout.bottom_sheet_notification_detail, null)
        dialog.setContentView(view)

        val tvTitle = view.findViewById<TextView>(R.id.tvSheetTitle)
        val tvBadge = view.findViewById<TextView>(R.id.tvSheetBadge)
        val tvDormName = view.findViewById<TextView>(R.id.tvSheetDormName)
        val tvMoveIn = view.findViewById<TextView>(R.id.tvSheetMoveIn)
        val tvTotal = view.findViewById<TextView>(R.id.tvSheetTotal)
        val rowReason = view.findViewById<View>(R.id.rowRejectReason)
        val tvReasonLabel = view.findViewById<TextView>(R.id.tvSheetReasonLabel)
        val tvReason = view.findViewById<TextView>(R.id.tvSheetRejectReason)
        val btnClose = view.findViewById<Button>(R.id.bsBtnClose)
        val btnAction = view.findViewById<Button>(R.id.bsBtnAccept)

        tvDormName.text = r.dorm_name.ifBlank { "—" }
        tvMoveIn.text = (r.move_in_date ?: "—")
        tvTotal.text = r.total_amount?.takeIf { it.isNotBlank() }
            ?.let { "₱$it" }
            ?: r.price_per_month?.takeIf { it.isNotBlank() }?.let { "₱$it / month" }
            ?: "—"

        rowReason.visibility = View.GONE
        btnAction.visibility = View.GONE

        when {
            r.status == "archived" -> {
                tvTitle.text = "Tenancy Terminated"
                tvBadge.text = "Terminated by owner"
                tvBadge.setTextColor(ContextCompat.getColor(this, android.R.color.holo_red_dark))
                tvBadge.setBackgroundResource(R.drawable.badge_rejected)

                rowReason.visibility = View.VISIBLE
                tvReasonLabel.text = "Termination reason"
                tvReason.text = r.termination_reason?.takeIf { it.isNotBlank() }
                    ?: "No reason was provided by the owner."

                btnAction.visibility = View.VISIBLE
                if (isAppealDismissed(r)) {
                    btnAction.text = "Appeal dismissed"
                    btnAction.isEnabled = false
                    btnAction.alpha = 0.65f
                    btnAction.setBackgroundResource(R.drawable.btn_outline_blue)
                } else if (!r.appeal_message.isNullOrBlank()) {
                    btnAction.text = "Appeal sent"
                    btnAction.isEnabled = false
                    btnAction.alpha = 0.65f
                    btnAction.setBackgroundResource(R.drawable.btn_outline_blue)
                } else {
                    btnAction.text = "Submit appeal"
                    btnAction.isEnabled = true
                    btnAction.alpha = 1f
                    btnAction.setBackgroundResource(R.drawable.btn_filled_blue)
                    btnAction.setOnClickListener {
                        dialog.dismiss()
                        showAppealDialog(r)
                    }
                }
            }

            r.status == "rejected" -> {
                tvTitle.text = "Reservation Rejected"
                tvBadge.text = "Rejected by owner"
                tvBadge.setTextColor(ContextCompat.getColor(this, android.R.color.holo_red_dark))
                tvBadge.setBackgroundResource(R.drawable.badge_rejected)

                rowReason.visibility = View.VISIBLE
                tvReasonLabel.text = "Rejection reason"
                tvReason.text = r.rejection_reason?.takeIf { it.isNotBlank() }
                    ?: "No reason was provided by the owner."
            }

            r.status == "approved" && r.tenant_action.isNullOrBlank() -> {
                tvTitle.text = "Reservation Approved"
                tvBadge.text = "Approved by owner"
                tvBadge.setTextColor(ContextCompat.getColor(this, android.R.color.holo_green_dark))
                tvBadge.setBackgroundResource(R.drawable.badge_approved)

                btnAction.visibility = View.VISIBLE
                btnAction.text = "Accept"
                btnAction.setBackgroundResource(R.drawable.btn_filled_blue)
                btnAction.setOnClickListener {
                    dialog.dismiss()
                    showAcceptConfirmDialog(r)
                }
            }

            r.status == "pending" -> {
                tvTitle.text = "Reservation Pending"
                tvBadge.text = "Awaiting owner review"
                tvBadge.setTextColor(ContextCompat.getColor(this, android.R.color.darker_gray))
                tvBadge.setBackgroundResource(R.drawable.badge_confirmed)

                btnAction.visibility = View.VISIBLE
                btnAction.text = "Cancel"
                btnAction.setBackgroundResource(R.drawable.btn_filled_red)
                btnAction.setOnClickListener {
                    dialog.dismiss()
                    showCancelDialog(r)
                }
            }

            else -> {
                tvTitle.text = "Reservation Details"
                tvBadge.text = r.status.replaceFirstChar { it.uppercase() }
            }
        }

        btnClose.setOnClickListener { dialog.dismiss() }
        dialog.show()
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
            val email = session.getEmail().trim().lowercase()
            val phone = session.getPhone().trim().filter { it.isDigit() }.takeLast(10)
            val cacheKey = when {
                email.isNotBlank() -> "cache_email_$email"
                phone.isNotBlank() -> "cache_phone_$phone"
                else -> return
            }
            getSharedPreferences("NotificationState", Context.MODE_PRIVATE)
                .edit().putString(cacheKey, com.google.gson.Gson().toJson(list)).apply()
        } catch (e: Exception) { Log.w("NotificationsActivity", "Cache write failed: ${e.message}") }
    }

    private fun loadCachedReservations(): List<TenantReservation> {
        return try {
            val email = session.getEmail().trim().lowercase()
            val phone = session.getPhone().trim().filter { it.isDigit() }.takeLast(10)
            val cacheKey = when {
                email.isNotBlank() -> "cache_email_$email"
                phone.isNotBlank() -> "cache_phone_$phone"
                else -> return emptyList()
            }
            val json = getSharedPreferences("NotificationState", Context.MODE_PRIVATE)
                .getString(cacheKey, null) ?: return emptyList()
            val type = object : com.google.gson.reflect.TypeToken<List<TenantReservation>>() {}.type
            com.google.gson.Gson().fromJson(json, type) ?: emptyList()
        } catch (e: Exception) { emptyList() }
    }
}