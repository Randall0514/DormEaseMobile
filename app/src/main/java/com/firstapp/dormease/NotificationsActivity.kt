package com.firstapp.dormease

// File path: app/src/main/java/com/firstapp/dormease/NotificationsActivity.kt

import android.app.AlertDialog
import android.content.Context
import android.content.SharedPreferences
import android.os.Bundle
import android.os.Handler
import android.os.Looper
import android.util.Log
import android.view.LayoutInflater
import android.view.View
import android.widget.Button
import android.widget.LinearLayout
import android.widget.TextView
import android.widget.Toast
import androidx.appcompat.app.AppCompatActivity
import androidx.core.content.ContextCompat
import androidx.swiperefreshlayout.widget.SwipeRefreshLayout
import com.firstapp.dormease.model.TenantAction
import com.firstapp.dormease.model.TenantReservation
import com.firstapp.dormease.network.RetrofitClient
import com.firstapp.dormease.utils.SessionManager
import com.google.android.material.bottomsheet.BottomSheetDialog
import com.google.gson.Gson
import com.google.gson.reflect.TypeToken
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

class NotificationsActivity : AppCompatActivity() {

    companion object {
        private const val TAG           = "NotifDebug"
        private const val POLL_INTERVAL = 10_000L

        // ── Two completely separate SharedPreferences files ──────────────────
        // PREFS_FILE  → notification cache, dismissed, accepted, last phone
        //               this file is NEVER cleared on logout
        // DormEaseSession (inside SessionManager) → auth token + profile only
        private const val PREFS_FILE = "NotificationState"

        // Stores the last 10-digit phone we successfully used.
        // Written every time we resolve a valid phone so it survives logout.
        private const val KEY_LAST_PHONE  = "last_phone"

        // Per-user buckets — phone appended as suffix
        private const val KEY_DISMISSED   = "dismissed_"
        private const val KEY_ACCEPTED    = "accepted_"
        private const val KEY_CACHE       = "cache_"
    }

    private lateinit var notifContainer : LinearLayout
    private lateinit var emptyView      : TextView
    private lateinit var swipeRefresh   : SwipeRefreshLayout
    private lateinit var sessionManager : SessionManager

    // The ONE SharedPreferences file we ever touch for notifications
    private lateinit var prefs: SharedPreferences

    private val gson = Gson()

    private val scope   = CoroutineScope(Dispatchers.IO + SupervisorJob())
    private val handler = Handler(Looper.getMainLooper())

    private val refreshRunnable = object : Runnable {
        override fun run() {
            fetchAndRender()
            handler.postDelayed(this, POLL_INTERVAL)
        }
    }

    // ── Resolve user key ──────────────────────────────────────────────────────
    // Priority:
    //   1. Phone currently in SessionManager  (user is logged in)
    //   2. Phone we saved last time            (user just logged out)
    // Either way we get a stable 10-digit key.
    private fun resolveUserKey(): String {
        // Try session first
        val fromSession = sessionManager.getPhone()
            .trim()
            .filter { it.isDigit() }
            .takeLast(10)

        if (fromSession.isNotBlank()) {
            // Persist so post-logout reads still work
            prefs.edit().putString(KEY_LAST_PHONE, fromSession).apply()
            Log.d(TAG, "userKey from session: $fromSession")
            return fromSession
        }

        // Fall back to last saved phone
        val saved = prefs.getString(KEY_LAST_PHONE, "") ?: ""
        Log.d(TAG, "userKey from prefs fallback: '$saved'")
        return saved
    }

    // ── Dismissed ─────────────────────────────────────────────────────────────

    private fun loadDismissed(key: String): MutableSet<Int> {
        val raw = prefs.getString(KEY_DISMISSED + key, "") ?: ""
        if (raw.isBlank()) return mutableSetOf()
        return raw.split(",").mapNotNull { it.trim().toIntOrNull() }.toMutableSet()
    }

    private fun saveDismissed(key: String, ids: Set<Int>) {
        prefs.edit().putString(KEY_DISMISSED + key, ids.joinToString(",")).apply()
    }

    private fun dismissId(key: String, id: Int) {
        val ids = loadDismissed(key); ids.add(id); saveDismissed(key, ids)
    }

    // ── Accepted ──────────────────────────────────────────────────────────────

    private fun loadAccepted(key: String): MutableSet<Int> {
        val raw = prefs.getString(KEY_ACCEPTED + key, "") ?: ""
        if (raw.isBlank()) return mutableSetOf()
        return raw.split(",").mapNotNull { it.trim().toIntOrNull() }.toMutableSet()
    }

    private fun saveAccepted(key: String, id: Int) {
        val ids = loadAccepted(key); ids.add(id)
        prefs.edit().putString(KEY_ACCEPTED + key, ids.joinToString(",")).apply()
    }

    private fun isAccepted(key: String, id: Int) = loadAccepted(key).contains(id)

    // ── Cache ─────────────────────────────────────────────────────────────────

    private fun saveCache(key: String, list: List<TenantReservation>) {
        if (key.isBlank()) { Log.w(TAG, "saveCache: blank key, skipping"); return }
        try {
            prefs.edit().putString(KEY_CACHE + key, gson.toJson(list)).apply()
            Log.d(TAG, "Cache saved: ${list.size} items → key='$key'")
        } catch (e: Exception) {
            Log.e(TAG, "saveCache failed: ${e.message}")
        }
    }

    private fun loadCache(key: String): List<TenantReservation> {
        if (key.isBlank()) { Log.w(TAG, "loadCache: blank key"); return emptyList() }
        val json = prefs.getString(KEY_CACHE + key, null)
        if (json.isNullOrBlank()) { Log.d(TAG, "Cache empty for key='$key'"); return emptyList() }
        return try {
            val type = object : TypeToken<List<TenantReservation>>() {}.type
            val list: List<TenantReservation> = gson.fromJson(json, type) ?: emptyList()
            Log.d(TAG, "Cache loaded: ${list.size} items ← key='$key'")
            list
        } catch (e: Exception) {
            Log.e(TAG, "loadCache parse failed: ${e.message}"); emptyList()
        }
    }

    // ─────────────────────────────────────────────────────────────────────────

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_notifications)
        supportActionBar?.hide()

        sessionManager = SessionManager(this)
        prefs          = getSharedPreferences(PREFS_FILE, Context.MODE_PRIVATE)

        notifContainer = findViewById(R.id.notifContainer)
        emptyView      = findViewById(R.id.tvEmpty)
        swipeRefresh   = findViewById(R.id.swipeRefresh)

        // Resolve key early so KEY_LAST_PHONE is written while we still have the session
        val key = resolveUserKey()
        Log.d(TAG, "onCreate — resolved key='$key'")

        findViewById<TextView>(R.id.btnBack).setOnClickListener { finish() }

        findViewById<TextView>(R.id.btnMarkAllRead).setOnClickListener {
            AlertDialog.Builder(this)
                .setTitle("Clear All Notifications")
                .setMessage("Are you sure you want to permanently delete all notifications?")
                .setPositiveButton("Yes, Clear All") { dialog, _ ->
                    val k = resolveUserKey()
                    // Dismiss all visible cards
                    val ids = loadDismissed(k)
                    for (i in 0 until notifContainer.childCount) {
                        val tag = notifContainer.getChildAt(i).tag
                        if (tag is Int) ids.add(tag)
                    }
                    saveDismissed(k, ids)
                    // Wipe cache so they don't come back on next poll
                    saveCache(k, emptyList())
                    notifContainer.removeAllViews()
                    emptyView.visibility = View.VISIBLE
                    emptyView.text = "No notifications yet.\nPull down to refresh."
                    Toast.makeText(this, "All notifications cleared", Toast.LENGTH_SHORT).show()
                    dialog.dismiss()
                }
                .setNegativeButton("Cancel") { d, _ -> d.dismiss() }
                .show()
        }

        swipeRefresh.setOnRefreshListener { fetchAndRender() }

        // Show cached notifications IMMEDIATELY — no network, no login needed
        showCached()
    }

    override fun onResume()  { super.onResume();  handler.post(refreshRunnable) }
    override fun onPause()   { super.onPause();   handler.removeCallbacks(refreshRunnable) }
    override fun onDestroy() { scope.cancel();    super.onDestroy() }

    // ─────────────────────────────────────────────────────────────────────────

    private fun showCached() {
        val key       = resolveUserKey()
        val dismissed = loadDismissed(key)
        val visible   = loadCache(key)
            .filter { it.id !in dismissed }
            .sortedByDescending { it.id }
        Log.d(TAG, "showCached: ${visible.size} notifications for key='$key'")
        renderCards(visible, key)
    }

    private fun fetchAndRender() {
        val rawPhone = sessionManager.getPhone().trim()

        // Not logged in — just show cache, no network call needed
        if (rawPhone.isBlank()) {
            swipeRefresh.isRefreshing = false
            Log.d(TAG, "fetchAndRender: no session phone, showing cache")
            showCached()
            return
        }

        val digits     = rawPhone.filter { it.isDigit() }
        val last10     = digits.takeLast(10)
        val phoneParam = "+63$last10"

        scope.launch {
            try {
                val response = RetrofitClient.getApiService(applicationContext)
                    .getTenantReservations(phoneParam)

                withContext(Dispatchers.Main) {
                    swipeRefresh.isRefreshing = false

                    if (!response.isSuccessful) {
                        Log.e(TAG, "Server ${response.code()} — showing cache")
                        Toast.makeText(this@NotificationsActivity,
                            "Could not refresh — showing saved notifications",
                            Toast.LENGTH_SHORT).show()
                        showCached()
                        return@withContext
                    }

                    val key       = resolveUserKey()   // always fresh
                    val dismissed = loadDismissed(key)
                    val all       = response.body() ?: emptyList()

                    val filtered = all
                        .filter { it.status == "approved" || it.status == "rejected" }
                        .filter { it.id !in dismissed }
                        .sortedByDescending { it.id }

                    Log.d(TAG, "Fetched ${all.size} total → ${filtered.size} visible, key='$key'")

                    // Always persist before rendering
                    saveCache(key, filtered)
                    renderCards(filtered, key)
                }
            } catch (e: Exception) {
                Log.e(TAG, "Network error: ${e.message}", e)
                withContext(Dispatchers.Main) {
                    swipeRefresh.isRefreshing = false
                    Toast.makeText(this@NotificationsActivity,
                        "Offline — showing saved notifications", Toast.LENGTH_SHORT).show()
                    showCached()
                }
            }
        }
    }

    // ─────────────────────────────────────────────────────────────────────────

    private fun renderCards(reservations: List<TenantReservation>, key: String) {
        notifContainer.removeAllViews()
        if (reservations.isEmpty()) {
            emptyView.visibility = View.VISIBLE
            emptyView.text = "No notifications yet.\nPull down to refresh."
            return
        }
        emptyView.visibility = View.GONE
        for (res in reservations) {
            notifContainer.addView(buildCard(res, key))
        }
    }

    // ─────────────────────────────────────────────────────────────────────────

    private fun buildCard(res: TenantReservation, key: String): View {
        val isApproved      = res.status == "approved"
        val alreadyAccepted = isAccepted(key, res.id)

        val card = LayoutInflater.from(this)
            .inflate(R.layout.item_notification_card, notifContainer, false)
        card.tag = res.id

        card.findViewById<TextView>(R.id.tvNotifTitle).text =
            if (isApproved) "Reservation Approved!" else "Reservation Rejected"

        card.findViewById<TextView>(R.id.tvNotifMessage).text =
            if (isApproved)
                "Your reservation for ${res.dorm_name} has been approved."
            else
                "Your reservation for ${res.dorm_name} was rejected.\nReason: ${
                    res.rejection_reason?.ifBlank { "No reason provided." } ?: "No reason provided."
                }"

        val tvBadge = card.findViewById<TextView>(R.id.tvStatusBadge)
        if (isApproved) {
            tvBadge.text = "✓ Approved by Owner"
            tvBadge.setBackgroundResource(R.drawable.badge_approved)
            tvBadge.setTextColor(ContextCompat.getColor(this, R.color.badge_approved_text))
        } else {
            tvBadge.text = "✗ Rejected by Owner"
            tvBadge.setBackgroundResource(R.drawable.badge_rejected)
            tvBadge.setTextColor(ContextCompat.getColor(this, R.color.badge_rejected_text))
        }

        card.findViewById<Button>(R.id.btnViewDetails).setOnClickListener {
            showDetailsBottomSheet(res, key, alreadyAccepted)
        }

        // ── Cancel ────────────────────────────────────────────────────────────
        card.findViewById<Button>(R.id.btnCancel).setOnClickListener {
            val input = android.widget.EditText(this).apply {
                hint      = "Enter your reason here..."
                inputType = android.text.InputType.TYPE_CLASS_TEXT or
                        android.text.InputType.TYPE_TEXT_FLAG_MULTI_LINE
                minLines  = 3; maxLines = 5
                gravity   = android.view.Gravity.TOP or android.view.Gravity.START
                setPadding(32, 24, 32, 24)
            }
            val container = android.widget.LinearLayout(this).apply {
                orientation = android.widget.LinearLayout.VERTICAL
                setPadding(48, 16, 48, 8)
                addView(input)
            }
            val dialog = AlertDialog.Builder(this)
                .setTitle("Cancel Reservation")
                .setMessage("Please provide a reason for cancelling:")
                .setView(container)
                .setPositiveButton("Submit") { _, _ -> }
                .setNegativeButton("Back") { d, _ -> d.dismiss() }
                .create()
            dialog.show()

            dialog.getButton(AlertDialog.BUTTON_POSITIVE).setOnClickListener {
                val reason = input.text.toString().trim()
                if (reason.isBlank()) { input.error = "Please enter a reason"; return@setOnClickListener }

                sendTenantAction(res.id, "cancelled", reason)

                // Remove from cache + dismiss so it never comes back
                val updated = loadCache(key).filter { it.id != res.id }
                saveCache(key, updated)
                dismissId(key, res.id)

                notifContainer.removeView(card)
                if (notifContainer.childCount == 0) {
                    emptyView.visibility = View.VISIBLE
                    emptyView.text = "No notifications yet.\nPull down to refresh."
                }
                Toast.makeText(this@NotificationsActivity,
                    "Reservation cancelled.", Toast.LENGTH_LONG).show()
                dialog.dismiss()
            }
        }

        // ── Accept ────────────────────────────────────────────────────────────
        val btnAccept = card.findViewById<Button>(R.id.btnAccept)
        if (isApproved) {
            btnAccept.visibility = View.VISIBLE
            if (alreadyAccepted) {
                btnAccept.text      = "✓ Accepted"
                btnAccept.isEnabled = false
                btnAccept.setBackgroundResource(R.drawable.btn_accepted)
            } else {
                btnAccept.setOnClickListener {
                    btnAccept.text      = "✓ Accepted"
                    btnAccept.isEnabled = false
                    btnAccept.setBackgroundResource(R.drawable.btn_accepted)
                    saveAccepted(key, res.id)
                    sendTenantAction(res.id, "accepted")
                    Toast.makeText(this, "Reservation accepted!", Toast.LENGTH_SHORT).show()
                }
            }
        } else {
            btnAccept.visibility = View.GONE
        }

        return card
    }

    // ─────────────────────────────────────────────────────────────────────────

    private fun sendTenantAction(reservationId: Int, action: String, cancelReason: String? = null) {
        val phone = "+63${sessionManager.getPhone().trim().filter { it.isDigit() }.takeLast(10)}"
        scope.launch {
            try {
                val response = RetrofitClient.getApiService(applicationContext)
                    .sendTenantAction(
                        reservationId,
                        TenantAction(action = action, phone = phone, cancel_reason = cancelReason)
                    )
                if (response.isSuccessful)
                    Log.d(TAG, "Action '$action' sent for $reservationId")
                else
                    Log.e(TAG, "Action failed: ${response.code()}")
            } catch (e: Exception) {
                Log.e(TAG, "sendTenantAction error: ${e.message}", e)
            }
        }
    }

    // ─────────────────────────────────────────────────────────────────────────

    private fun showDetailsBottomSheet(
        res: TenantReservation,
        key: String,
        alreadyAccepted: Boolean
    ) {
        val isApproved = res.status == "approved"
        val dialog     = BottomSheetDialog(this, R.style.BottomSheetDialogTheme)
        val sheetView  = layoutInflater.inflate(R.layout.bottom_sheet_notification_detail, null)
        dialog.setContentView(sheetView)

        sheetView.findViewById<TextView>(R.id.tvSheetTitle).text =
            if (isApproved) "Reservation Approved!" else "Reservation Rejected"

        val tvBadge = sheetView.findViewById<TextView>(R.id.tvSheetBadge)
        if (isApproved) {
            tvBadge.text = "✓ Approved"
            tvBadge.setBackgroundResource(R.drawable.badge_approved)
            tvBadge.setTextColor(ContextCompat.getColor(this, R.color.badge_approved_text))
        } else {
            tvBadge.text = "✗ Rejected"
            tvBadge.setBackgroundResource(R.drawable.badge_rejected)
            tvBadge.setTextColor(ContextCompat.getColor(this, R.color.badge_rejected_text))
        }

        sheetView.findViewById<TextView>(R.id.tvSheetDormName).text = res.dorm_name
        sheetView.findViewById<TextView>(R.id.tvSheetMoveIn).text   = res.move_in_date
        sheetView.findViewById<TextView>(R.id.tvSheetTotal).text    = "₱${"%,.0f".format(res.total_amount)}"

        val rejectRow = sheetView.findViewById<View>(R.id.rowRejectReason)
        val reason    = res.rejection_reason?.trim()
        if (!isApproved && !reason.isNullOrBlank()) {
            rejectRow.visibility = View.VISIBLE
            sheetView.findViewById<TextView>(R.id.tvSheetRejectReason).text = reason
        } else {
            rejectRow.visibility = View.GONE
        }

        sheetView.findViewById<Button>(R.id.bsBtnClose).setOnClickListener { dialog.dismiss() }

        val bsBtnAccept = sheetView.findViewById<Button>(R.id.bsBtnAccept)
        if (isApproved) {
            bsBtnAccept.visibility = View.VISIBLE
            if (alreadyAccepted) {
                bsBtnAccept.text      = "✓ Accepted"
                bsBtnAccept.isEnabled = false
            } else {
                bsBtnAccept.setOnClickListener {
                    bsBtnAccept.text      = "✓ Accepted"
                    bsBtnAccept.isEnabled = false
                    saveAccepted(key, res.id)
                    sendTenantAction(res.id, "accepted")
                    // Sync the card behind the sheet
                    notifContainer.findViewWithTag<View>(res.id)
                        ?.findViewById<Button>(R.id.btnAccept)
                        ?.apply {
                            text      = "✓ Accepted"
                            isEnabled = false
                            setBackgroundResource(R.drawable.btn_accepted)
                        }
                    Toast.makeText(this, "Reservation accepted!", Toast.LENGTH_SHORT).show()
                    dialog.dismiss()
                }
            }
        } else {
            bsBtnAccept.visibility = View.GONE
        }

        dialog.show()
    }
}