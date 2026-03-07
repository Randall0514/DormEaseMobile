package com.firstapp.dormease

// FILE PATH: app/src/main/java/com/firstapp/dormease/NotificationsActivity.kt

import android.app.Dialog
import android.content.Context
import android.content.Intent
import android.content.SharedPreferences
import android.os.Bundle
import android.os.Handler
import android.os.Looper
import android.util.Log
import android.view.LayoutInflater
import android.view.View
import android.view.Window
import android.widget.Button
import android.widget.EditText
import android.widget.LinearLayout
import android.widget.TextView
import android.widget.Toast
import androidx.appcompat.app.AppCompatActivity
import androidx.core.content.ContextCompat
import androidx.swiperefreshlayout.widget.SwipeRefreshLayout
import com.firstapp.dormease.activity.TenantDashboardActivity
import com.firstapp.dormease.model.TenantAction
import com.firstapp.dormease.model.TenantReservation
import com.firstapp.dormease.network.RetrofitClient
import com.firstapp.dormease.network.SocketManager
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
import org.json.JSONObject

class NotificationsActivity : AppCompatActivity() {

    companion object {
        private const val TAG           = "NotifDebug"
        private const val POLL_INTERVAL = 10_000L

        private const val PREFS_FILE     = "NotificationState"
        private const val KEY_LAST_PHONE = "last_phone"
        private const val KEY_DISMISSED  = "dismissed_"
        private const val KEY_ACCEPTED   = "accepted_"
        private const val KEY_CACHE      = "cache_"
    }

    private lateinit var notifContainer : LinearLayout
    private lateinit var emptyView      : TextView
    private lateinit var swipeRefresh   : SwipeRefreshLayout
    private lateinit var sessionManager : SessionManager
    private lateinit var prefs          : SharedPreferences

    private val gson    = Gson()
    private val scope   = CoroutineScope(Dispatchers.IO + SupervisorJob())
    private val handler = Handler(Looper.getMainLooper())

    private val socketListener: (JSONObject) -> Unit = { data ->
        Log.d(TAG, "⚡ Socket.IO push received: $data")
        handler.post { fetchAndRender() }
    }

    private val refreshRunnable = object : Runnable {
        override fun run() {
            fetchAndRender()
            handler.postDelayed(this, POLL_INTERVAL)
        }
    }

    // ── Resolve user key ──────────────────────────────────────────────────────

    private fun resolveUserKey(): String {
        val fromSession = sessionManager.getPhone()
            .trim()
            .filter { it.isDigit() }
            .takeLast(10)

        if (fromSession.isNotBlank()) {
            prefs.edit().putString(KEY_LAST_PHONE, fromSession).apply()
            Log.d(TAG, "userKey from session: $fromSession")
            return fromSession
        }

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
            Log.d(TAG, "saveCache: ${list.size} items → key='$key'")
        } catch (e: Exception) {
            Log.e(TAG, "saveCache failed: ${e.message}")
        }
    }

    private fun loadCache(key: String): List<TenantReservation> {
        if (key.isBlank()) { Log.w(TAG, "loadCache: blank key"); return emptyList() }
        val json = prefs.getString(KEY_CACHE + key, null)
        if (json.isNullOrBlank()) { Log.d(TAG, "loadCache: empty for key='$key'"); return emptyList() }
        return try {
            val type = object : TypeToken<List<TenantReservation>>() {}.type
            val list: List<TenantReservation> = gson.fromJson(json, type) ?: emptyList()
            Log.d(TAG, "loadCache: ${list.size} items ← key='$key'")
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

        val key = resolveUserKey()
        Log.d(TAG, "onCreate — resolved key='$key'")

        findViewById<TextView>(R.id.btnBack).setOnClickListener { finish() }
        findViewById<TextView>(R.id.btnMarkAllRead).setOnClickListener { showClearAllDialog() }

        swipeRefresh.setOnRefreshListener { fetchAndRender() }
        showCached()
    }

    override fun onResume() {
        super.onResume()
        SocketManager.addReservationUpdateListener(socketListener)
        SocketManager.addNotificationListener(socketListener)
        handler.post(refreshRunnable)
    }

    override fun onPause() {
        super.onPause()
        SocketManager.removeReservationUpdateListener(socketListener)
        SocketManager.removeNotificationListener(socketListener)
        handler.removeCallbacks(refreshRunnable)
    }

    override fun onDestroy() { scope.cancel(); super.onDestroy() }

    // ── Clear All ─────────────────────────────────────────────────────────────

    private fun showClearAllDialog() {
        val dialog = Dialog(this)
        dialog.requestWindowFeature(Window.FEATURE_NO_TITLE)
        dialog.setContentView(R.layout.dialog_cancel_reservation)
        dialog.window?.apply {
            setBackgroundDrawableResource(android.R.color.transparent)
            setLayout(
                (resources.displayMetrics.widthPixels * 0.88).toInt(),
                android.view.ViewGroup.LayoutParams.WRAP_CONTENT
            )
        }
        dialog.setCanceledOnTouchOutside(true)

        dialog.findViewById<EditText>(R.id.etCancelReason)?.visibility = View.GONE
        dialog.findViewById<TextView>(R.id.tvCancelError)?.visibility  = View.GONE

        dialog.findViewById<Button>(R.id.btnCancelDialogBack).setOnClickListener { dialog.dismiss() }
        dialog.findViewById<Button>(R.id.btnCancelDialogSubmit).apply {
            text = "Clear All"
            setOnClickListener {
                val k   = resolveUserKey()
                val ids = loadDismissed(k)
                for (i in 0 until notifContainer.childCount) {
                    val tag = notifContainer.getChildAt(i).tag
                    if (tag is Int) ids.add(tag)
                }
                saveDismissed(k, ids)
                saveCache(k, emptyList())
                notifContainer.removeAllViews()
                emptyView.visibility = View.VISIBLE
                emptyView.text = "No notifications yet.\nPull down to refresh."
                Toast.makeText(this@NotificationsActivity, "All notifications cleared", Toast.LENGTH_SHORT).show()
                dialog.dismiss()
            }
        }
        dialog.show()
    }

    // ─────────────────────────────────────────────────────────────────────────

    private fun showCached() {
        val key       = resolveUserKey()
        val dismissed = loadDismissed(key)
        val cached    = loadCache(key)
        Log.d(TAG, "showCached: total cached=${cached.size}, dismissed=${dismissed.size}")

        val visible = cached
            .filter { it.id !in dismissed }
            .sortedByDescending { it.id }

        renderCards(visible, key)
    }

    // ─────────────────────────────────────────────────────────────────────────

    private fun fetchAndRender() {
        val rawPhone = sessionManager.getPhone().trim()
        Log.d(TAG, "fetchAndRender: rawPhone='$rawPhone'")

        if (rawPhone.isBlank()) {
            swipeRefresh.isRefreshing = false
            Log.w(TAG, "fetchAndRender: no phone in session — showing cache only")
            showCached()
            return
        }

        val phoneParam = "+63${rawPhone.filter { it.isDigit() }.takeLast(10)}"
        Log.d(TAG, "fetchAndRender: calling API with phone='$phoneParam'")

        scope.launch {
            try {
                val response = RetrofitClient.getApiService(applicationContext)
                    .getTenantReservations(phoneParam)

                withContext(Dispatchers.Main) {
                    swipeRefresh.isRefreshing = false
                    Log.d(TAG, "fetchAndRender: HTTP ${response.code()}")

                    if (!response.isSuccessful) {
                        Log.e(TAG, "fetchAndRender: server error ${response.code()} — showing cache")
                        Toast.makeText(this@NotificationsActivity,
                            "Could not refresh — showing saved notifications",
                            Toast.LENGTH_SHORT).show()
                        showCached()
                        return@withContext
                    }

                    val key       = resolveUserKey()
                    val dismissed = loadDismissed(key)
                    val all       = response.body() ?: emptyList()

                    Log.d(TAG, "fetchAndRender: server returned ${all.size} total records")

                    val filtered = all
                        .filter {
                            it.status == "approved" ||
                                    it.status == "rejected" ||
                                    it.status == "archived"
                        }
                        .filter { it.id !in dismissed }
                        .sortedByDescending { it.id }

                    Log.d(TAG, "fetchAndRender: ${filtered.size} after filter")

                    saveCache(key, filtered)
                    renderCards(filtered, key)
                }
            } catch (e: Exception) {
                Log.e(TAG, "fetchAndRender: network error — ${e.message}", e)
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
        Log.d(TAG, "renderCards: rendering ${reservations.size} cards")
        notifContainer.removeAllViews()

        if (reservations.isEmpty()) {
            emptyView.visibility = View.VISIBLE
            emptyView.text = "No notifications yet.\nPull down to refresh."
            return
        }

        emptyView.visibility = View.GONE
        for (res in reservations) {
            Log.d(TAG, "renderCards: building card id=${res.id} status='${res.status}'")
            val card = if (res.status == "archived") {
                Log.d(TAG, "renderCards: → using TERMINATED card layout")
                buildTerminatedCard(res, key)
            } else {
                buildCard(res, key)
            }
            notifContainer.addView(card)
        }
    }

    // ── Terminated / Archived card ────────────────────────────────────────────

    private fun buildTerminatedCard(res: TenantReservation, key: String): View {
        val card = LayoutInflater.from(this)
            .inflate(R.layout.item_notification_terminated, notifContainer, false)
        card.tag = res.id

        val reason = res.termination_reason?.ifBlank { "No reason provided." } ?: "No reason provided."

        card.findViewById<TextView>(R.id.tvTerminatedMessage).text =
            "Your tenancy at ${res.dorm_name} has been terminated by the owner.\nReason: $reason"

        card.findViewById<Button>(R.id.btnTerminatedView).setOnClickListener {
            showTerminatedBottomSheet(res)
        }

        card.findViewById<Button>(R.id.btnTerminatedAppeal).setOnClickListener {
            showAppealDialog(res, key, card)
        }

        return card
    }

    // ── Standard approved / rejected card ─────────────────────────────────────

    private fun buildCard(res: TenantReservation, key: String): View {
        val isApproved      = res.status == "approved"
        val alreadyAccepted = isAccepted(key, res.id) || res.tenant_action == "accepted"

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

        val btnCancel = card.findViewById<Button>(R.id.btnCancel)
        if (isApproved) {
            btnCancel.visibility = View.VISIBLE
            btnCancel.setOnClickListener { showCancelDialog(res, key, card) }
        } else {
            btnCancel.visibility = View.GONE
        }

        val btnAccept = card.findViewById<Button>(R.id.btnAccept)
        if (isApproved) {
            btnAccept.visibility = View.VISIBLE
            if (alreadyAccepted) {
                btnAccept.text      = "✓ Accepted"
                btnAccept.isEnabled = false
                btnAccept.setBackgroundResource(R.drawable.btn_accepted)
            } else {
                btnAccept.setOnClickListener {
                    showAcceptDialog(res, key, btnAccept, null)
                }
            }
        } else {
            btnAccept.visibility = View.GONE
        }

        return card
    }

    // ── Appeal Dialog ─────────────────────────────────────────────────────────

    private fun showAppealDialog(res: TenantReservation, key: String, card: View) {
        val dialog = Dialog(this)
        dialog.requestWindowFeature(Window.FEATURE_NO_TITLE)
        dialog.setContentView(R.layout.dialog_appeal_termination)
        dialog.window?.apply {
            setBackgroundDrawableResource(android.R.color.transparent)
            setLayout(
                (resources.displayMetrics.widthPixels * 0.88).toInt(),
                android.view.ViewGroup.LayoutParams.WRAP_CONTENT
            )
        }
        dialog.setCanceledOnTouchOutside(true)

        val etMessage = dialog.findViewById<EditText>(R.id.etAppealMessage)
        val tvError   = dialog.findViewById<TextView>(R.id.tvAppealError)
        etMessage.setHintTextColor(0xFFAAAACC.toInt())

        dialog.findViewById<Button>(R.id.btnAppealBack).setOnClickListener { dialog.dismiss() }

        dialog.findViewById<Button>(R.id.btnAppealSubmit).setOnClickListener {
            val appealText = etMessage.text.toString().trim()
            if (appealText.isBlank()) {
                tvError.visibility = View.VISIBLE
                etMessage.requestFocus()
                return@setOnClickListener
            }
            tvError.visibility = View.GONE

            sendTenantAction(res.id, "appealed", appealText)

            dismissId(key, res.id)
            val updated = loadCache(key).filter { it.id != res.id }
            saveCache(key, updated)
            notifContainer.removeView(card)
            if (notifContainer.childCount == 0) {
                emptyView.visibility = View.VISIBLE
                emptyView.text = "No notifications yet.\nPull down to refresh."
            }

            Toast.makeText(this, "Your appeal has been submitted.", Toast.LENGTH_LONG).show()
            dialog.dismiss()
        }

        dialog.show()
    }

    // ── Terminated bottom sheet ───────────────────────────────────────────────

    private fun showTerminatedBottomSheet(res: TenantReservation) {
        val dialog    = BottomSheetDialog(this, R.style.BottomSheetDialogTheme)
        val sheetView = layoutInflater.inflate(R.layout.bottom_sheet_notification_detail, null)
        dialog.setContentView(sheetView)

        sheetView.findViewById<TextView>(R.id.tvSheetTitle).text = "Tenancy Terminated"

        val tvBadge = sheetView.findViewById<TextView>(R.id.tvSheetBadge)
        tvBadge.text = "⚠ Terminated by Owner"
        tvBadge.setBackgroundResource(R.drawable.badge_rejected)
        tvBadge.setTextColor(ContextCompat.getColor(this, R.color.badge_rejected_text))

        sheetView.findViewById<TextView>(R.id.tvSheetDormName).text = res.dorm_name
        sheetView.findViewById<TextView>(R.id.tvSheetMoveIn).text   = res.move_in_date ?: "—"
        sheetView.findViewById<TextView>(R.id.tvSheetTotal).text    = "₱${"%,.0f".format(res.total_amount ?: 0.0)}"

        val rejectRow  = sheetView.findViewById<View>(R.id.rowRejectReason)
        val reasonText = res.termination_reason?.trim()
        if (!reasonText.isNullOrBlank()) {
            rejectRow.visibility = View.VISIBLE
            sheetView.findViewById<TextView>(R.id.tvSheetRejectReason).text = reasonText
        } else {
            rejectRow.visibility = View.GONE
        }

        sheetView.findViewById<Button>(R.id.bsBtnClose).setOnClickListener { dialog.dismiss() }
        sheetView.findViewById<Button>(R.id.bsBtnAccept).visibility = View.GONE

        dialog.show()
    }

    // ── Cancel Dialog ─────────────────────────────────────────────────────────

    private fun showCancelDialog(res: TenantReservation, key: String, card: View) {
        val dialog = Dialog(this)
        dialog.requestWindowFeature(Window.FEATURE_NO_TITLE)
        dialog.setContentView(R.layout.dialog_cancel_reservation)
        dialog.window?.apply {
            setBackgroundDrawableResource(android.R.color.transparent)
            setLayout(
                (resources.displayMetrics.widthPixels * 0.88).toInt(),
                android.view.ViewGroup.LayoutParams.WRAP_CONTENT
            )
        }
        dialog.setCanceledOnTouchOutside(true)

        val etReason = dialog.findViewById<EditText>(R.id.etCancelReason)
        val tvError  = dialog.findViewById<TextView>(R.id.tvCancelError)
        etReason.setHintTextColor(0xFFAAAACC.toInt())

        dialog.findViewById<Button>(R.id.btnCancelDialogBack).setOnClickListener { dialog.dismiss() }

        dialog.findViewById<Button>(R.id.btnCancelDialogSubmit).setOnClickListener {
            val reason = etReason.text.toString().trim()
            if (reason.isBlank()) {
                tvError.visibility = View.VISIBLE
                etReason.requestFocus()
                return@setOnClickListener
            }
            tvError.visibility = View.GONE

            sendTenantAction(res.id, "cancelled", reason)

            val updated = loadCache(key).filter { it.id != res.id }
            saveCache(key, updated)
            dismissId(key, res.id)

            notifContainer.removeView(card)
            if (notifContainer.childCount == 0) {
                emptyView.visibility = View.VISIBLE
                emptyView.text = "No notifications yet.\nPull down to refresh."
            }

            Toast.makeText(this, "Reservation cancelled.", Toast.LENGTH_LONG).show()
            dialog.dismiss()
        }

        dialog.show()
    }

    // ── Accept Dialog ─────────────────────────────────────────────────────────

    private fun showAcceptDialog(
        res: TenantReservation,
        key: String,
        cardAcceptBtn: Button,
        syncBtnInSheet: Button?
    ) {
        val dialog = Dialog(this)
        dialog.requestWindowFeature(Window.FEATURE_NO_TITLE)
        dialog.setContentView(R.layout.dialog_accept_reservation)
        dialog.window?.apply {
            setBackgroundDrawableResource(android.R.color.transparent)
            setLayout(
                (resources.displayMetrics.widthPixels * 0.88).toInt(),
                android.view.ViewGroup.LayoutParams.WRAP_CONTENT
            )
        }
        dialog.setCanceledOnTouchOutside(true)

        dialog.findViewById<TextView>(R.id.tvAcceptDormName).text = res.dorm_name
        dialog.findViewById<TextView>(R.id.tvAcceptMoveIn).text   = res.move_in_date ?: "—"
        dialog.findViewById<TextView>(R.id.tvAcceptTotal).text    = "₱${"%,.0f".format(res.total_amount ?: 0.0)}"

        dialog.findViewById<Button>(R.id.btnAcceptDialogCancel).setOnClickListener { dialog.dismiss() }

        dialog.findViewById<Button>(R.id.btnAcceptDialogConfirm).setOnClickListener {
            // 1. Persist acceptance locally so button stays greyed out.
            saveAccepted(key, res.id)

            // 2. Tell the server (fire-and-forget — dashboard will confirm via poll).
            sendTenantAction(res.id, "accepted")

            // 3. Update the card button immediately.
            cardAcceptBtn.text      = "✓ Accepted"
            cardAcceptBtn.isEnabled = false
            cardAcceptBtn.setBackgroundResource(R.drawable.btn_accepted)
            syncBtnInSheet?.let { it.text = "✓ Accepted"; it.isEnabled = false }

            dialog.dismiss()
            Toast.makeText(this, "Reservation accepted!", Toast.LENGTH_SHORT).show()

            // 4. Navigate to TenantDashboardActivity with EXTRA_FORCE_CONFIRMED=true
            //    so the dashboard shows the confirmed state immediately, without
            //    waiting for the next 15-second poll cycle to confirm server update.
            val intent = Intent(this, TenantDashboardActivity::class.java).apply {
                flags = Intent.FLAG_ACTIVITY_CLEAR_TOP or Intent.FLAG_ACTIVITY_SINGLE_TOP
                putExtra(TenantDashboardActivity.EXTRA_FORCE_CONFIRMED, true)
                putExtra(TenantDashboardActivity.EXTRA_RESERVATION_ID, res.id)
            }
            startActivity(intent)
        }

        dialog.show()
    }

    // ─────────────────────────────────────────────────────────────────────────

    private fun sendTenantAction(reservationId: Int, action: String, cancelReason: String? = null) {
        val phone = "+63${sessionManager.getPhone().trim().filter { it.isDigit() }.takeLast(10)}"
        Log.d(TAG, "sendTenantAction: id=$reservationId action='$action' phone='$phone'")
        scope.launch {
            try {
                val response = RetrofitClient.getApiService(applicationContext)
                    .sendTenantAction(
                        reservationId,
                        TenantAction(action = action, phone = phone, cancel_reason = cancelReason)
                    )
                if (response.isSuccessful)
                    Log.d(TAG, "sendTenantAction: '$action' sent OK for id=$reservationId")
                else
                    Log.e(TAG, "sendTenantAction: failed ${response.code()} for id=$reservationId")
            } catch (e: Exception) {
                Log.e(TAG, "sendTenantAction: error — ${e.message}", e)
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
        sheetView.findViewById<TextView>(R.id.tvSheetMoveIn).text   = res.move_in_date ?: "—"
        sheetView.findViewById<TextView>(R.id.tvSheetTotal).text    = "₱${"%,.0f".format(res.total_amount ?: 0.0)}"

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
                    val cardAcceptBtn = notifContainer
                        .findViewWithTag<View>(res.id)
                        ?.findViewById<Button>(R.id.btnAccept)

                    if (cardAcceptBtn != null) {
                        showAcceptDialog(res, key, cardAcceptBtn, bsBtnAccept)
                    } else {
                        saveAccepted(key, res.id)
                        sendTenantAction(res.id, "accepted")
                        bsBtnAccept.text      = "✓ Accepted"
                        bsBtnAccept.isEnabled = false
                        Toast.makeText(this, "Reservation accepted!", Toast.LENGTH_SHORT).show()

                        // Navigate with force-confirmed flag.
                        val intent = Intent(this, TenantDashboardActivity::class.java).apply {
                            flags = Intent.FLAG_ACTIVITY_CLEAR_TOP or Intent.FLAG_ACTIVITY_SINGLE_TOP
                            putExtra(TenantDashboardActivity.EXTRA_FORCE_CONFIRMED, true)
                            putExtra(TenantDashboardActivity.EXTRA_RESERVATION_ID, res.id)
                        }
                        startActivity(intent)
                    }
                    dialog.dismiss()
                }
            }
        } else {
            bsBtnAccept.visibility = View.GONE
        }

        dialog.show()
    }
}