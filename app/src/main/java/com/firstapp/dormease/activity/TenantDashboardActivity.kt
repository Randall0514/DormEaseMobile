package com.firstapp.dormease.activity

// FILE PATH: app/src/main/java/com/firstapp/dormease/activity/TenantDashboardActivity.kt

import android.content.Intent
import android.os.Bundle
import android.os.Handler
import android.os.Looper
import android.util.Log
import android.view.View
import android.widget.FrameLayout
import android.widget.LinearLayout
import android.widget.TextView
import androidx.appcompat.app.AlertDialog
import androidx.appcompat.app.AppCompatActivity
import androidx.cardview.widget.CardView
import androidx.core.content.ContextCompat
import com.firstapp.dormease.DashboardActivity
import com.firstapp.dormease.MessagesActivity
import com.firstapp.dormease.NotificationsActivity
import com.firstapp.dormease.ProfileActivity
import com.firstapp.dormease.R
import com.firstapp.dormease.SettingsActivity
import com.firstapp.dormease.model.TenantReservation
import com.firstapp.dormease.network.RetrofitClient
import com.firstapp.dormease.utils.NavBadgeHelper
import com.firstapp.dormease.utils.SessionManager
import com.google.gson.Gson
import com.google.gson.reflect.TypeToken
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.text.NumberFormat
import java.text.SimpleDateFormat
import java.util.Calendar
import java.util.Locale

class TenantDashboardActivity : AppCompatActivity() {

    companion object {
        const val EXTRA_FORCE_CONFIRMED = "force_confirmed"
        const val EXTRA_RESERVATION_ID  = "reservation_id"
    }

    private lateinit var sessionManager: SessionManager
    private val badgeHelper = NavBadgeHelper()
    private val scope   = CoroutineScope(Dispatchers.IO + SupervisorJob())
    private val handler = Handler(Looper.getMainLooper())

    private var lastReservation: TenantReservation? = null
    private var terminationDialogShowing = false

    private lateinit var tvGreeting          : TextView
    private lateinit var tvWelcomeName       : TextView
    private lateinit var tvAvatarInitial     : TextView
    private lateinit var tvReservationStatus : TextView
    private lateinit var tvDormName          : TextView
    private lateinit var tvDormLocation      : TextView
    private lateinit var tvTenantName        : TextView
    private lateinit var tvCheckInDate       : TextView
    private lateinit var tvMonthlyRent       : TextView
    private lateinit var tvDeposit           : TextView
    private lateinit var tvAdvance           : TextView
    private lateinit var tvMonthlyRentSummary: TextView
    private lateinit var tvPaymentStatus     : TextView
    private lateinit var tvNextDueDate       : TextView
    private lateinit var tvNotificationBadge : TextView
    private lateinit var cardConfirmed       : LinearLayout
    private lateinit var cardPending         : CardView
    private lateinit var cardNoReservation   : CardView

    private val pollRunnable = object : Runnable {
        override fun run() {
            loadReservation()
            fetchNotificationCount()
            handler.postDelayed(this, 15_000L)
        }
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_tenant_dashboard)
        supportActionBar?.hide()

        sessionManager = SessionManager(this)
        bindViews()
        setupGreeting()
        setupBottomNav()

        findViewById<FrameLayout>(R.id.notificationBellContainer).setOnClickListener {
            startActivity(Intent(this, NotificationsActivity::class.java).apply {
                flags = Intent.FLAG_ACTIVITY_REORDER_TO_FRONT
            })
        }

        if (intent.getBooleanExtra(EXTRA_FORCE_CONFIRMED, false)) {
            showState(State.CONFIRMED)
            handler.postDelayed({ loadReservation() }, 1_500L)
        }
    }

    override fun onResume() {
        super.onResume()
        badgeHelper.attach(this)
        loadReservation()
        fetchNotificationCount()
        handler.removeCallbacks(pollRunnable)
        handler.postDelayed(pollRunnable, 15_000L)
    }

    override fun onPause() {
        super.onPause()
        badgeHelper.detach()
        handler.removeCallbacks(pollRunnable)
    }

    override fun onDestroy() {
        scope.cancel()
        super.onDestroy()
    }

    private fun bindViews() {
        tvGreeting           = findViewById(R.id.tvGreeting)
        tvWelcomeName        = findViewById(R.id.tvWelcomeName)
        tvAvatarInitial      = findViewById(R.id.tvAvatarInitial)
        tvReservationStatus  = findViewById(R.id.tvReservationStatus)
        tvDormName           = findViewById(R.id.tvDormName)
        tvDormLocation       = findViewById(R.id.tvDormLocation)
        tvTenantName         = findViewById(R.id.tvTenantName)
        tvCheckInDate        = findViewById(R.id.tvCheckInDate)
        tvMonthlyRent        = findViewById(R.id.tvMonthlyRent)
        tvDeposit            = findViewById(R.id.tvDeposit)
        tvAdvance            = findViewById(R.id.tvAdvance)
        tvMonthlyRentSummary = findViewById(R.id.tvMonthlyRentSummary)
        tvPaymentStatus      = findViewById(R.id.tvPaymentStatus)
        tvNextDueDate        = findViewById(R.id.tvNextDueDate)
        tvNotificationBadge  = findViewById(R.id.tvNotificationBadge)
        cardConfirmed        = findViewById(R.id.cardConfirmed)
        cardPending          = findViewById(R.id.cardPending)
        cardNoReservation    = findViewById(R.id.cardNoReservation)
    }

    private fun setupGreeting() {
        val hour = Calendar.getInstance().get(Calendar.HOUR_OF_DAY)
        tvGreeting.text = when {
            hour < 12 -> "Good morning 👋"
            hour < 17 -> "Good afternoon 👋"
            else      -> "Good evening 👋"
        }
        val name = sessionManager.getName()
        tvWelcomeName.text   = "Welcome, $name!"
        tvAvatarInitial.text = name.firstOrNull()?.uppercaseChar()?.toString() ?: "T"
    }

    private fun fetchNotificationCount() {
        val rawPhone = sessionManager.getPhone().trim()
        if (rawPhone.isBlank()) { updateBadge(0); return }
        val phoneParam = "+63${rawPhone.filter { it.isDigit() }.takeLast(10)}"
        scope.launch {
            try {
                val response = RetrofitClient.getApiService(applicationContext)
                    .getTenantReservations(phoneParam)
                val count = if (response.isSuccessful)
                    response.body()?.count { it.status == "approved" || it.status == "rejected" } ?: 0
                else 0
                withContext(Dispatchers.Main) { updateBadge(count) }
            } catch (e: Exception) {
                Log.w("TenantDashboard", "Badge fetch failed: ${e.message}")
            }
        }
    }

    private fun updateBadge(count: Int) {
        tvNotificationBadge.visibility = if (count > 0) View.VISIBLE else View.GONE
        if (count > 0) tvNotificationBadge.text = if (count > 9) "9+" else count.toString()
    }

    private fun loadReservation() {
        val rawPhone = sessionManager.getPhone().trim()
        val userId   = sessionManager.getUserId()
        when {
            rawPhone.isNotBlank() -> {
                fetchByPhone("+63${rawPhone.filter { it.isDigit() }.takeLast(10)}")
            }
            userId > 0 -> fetchByUserId()
            else -> {
                val notifPrefs = getSharedPreferences("NotificationState", MODE_PRIVATE)
                val lastPhone  = (notifPrefs.getString("last_phone", "") ?: "").trim()
                if (lastPhone.isNotBlank()) {
                    val digits = lastPhone.filter { it.isDigit() }.takeLast(10)
                    sessionManager.savePhone("+63$digits")
                    fetchByPhone("+63$digits")
                } else if (lastReservation == null) {
                    showState(State.NO_RESERVATION)
                }
            }
        }
    }

    private fun fetchByPhone(phoneParam: String) {
        scope.launch {
            try {
                val response = RetrofitClient.getApiService(applicationContext)
                    .getTenantReservations(phoneParam)
                if (!response.isSuccessful) {
                    withContext(Dispatchers.Main) {
                        if (lastReservation == null) showState(State.NO_RESERVATION)
                    }
                    return@launch
                }
                withContext(Dispatchers.Main) { resolveAndShow(response.body() ?: emptyList()) }
            } catch (e: Exception) {
                Log.e("TenantDashboard", "fetchByPhone error: ${e.message}")
                withContext(Dispatchers.Main) {
                    if (lastReservation == null) showState(State.NO_RESERVATION)
                }
            }
        }
    }

    private fun fetchByUserId() {
        scope.launch {
            try {
                val response = RetrofitClient.getApiService(applicationContext)
                    .getMyReservations()
                if (!response.isSuccessful) {
                    withContext(Dispatchers.Main) {
                        if (lastReservation == null) showState(State.NO_RESERVATION)
                    }
                    return@launch
                }
                val list = response.body() ?: emptyList()
                list.firstOrNull { it.phone.isNotBlank() }?.phone?.let { serverPhone ->
                    val digits = serverPhone.filter { it.isDigit() }.takeLast(10)
                    if (digits.isNotBlank()) sessionManager.savePhone("+63$digits")
                }
                withContext(Dispatchers.Main) { resolveAndShow(list) }
            } catch (e: Exception) {
                Log.e("TenantDashboard", "fetchByUserId error: ${e.message}")
                withContext(Dispatchers.Main) {
                    if (lastReservation == null) showState(State.NO_RESERVATION)
                }
            }
        }
    }

    private fun resolveAndShow(reservations: List<TenantReservation>) {
        val approved = reservations.firstOrNull { r ->
            r.status == "approved" && r.tenant_action != "cancelled"
        }
        val pending = reservations.firstOrNull { it.status == "pending" }

        when {
            approved != null -> {
                if (lastReservation != null &&
                    lastReservation!!.status == "approved" &&
                    reservations.none { it.id == lastReservation!!.id && it.status == "approved" }
                ) {
                    handleTermination(reservations.firstOrNull {
                        it.id == lastReservation!!.id && it.status == "archived"
                    })
                    return
                }
                populateDashboard(approved)
            }
            pending != null -> showState(State.PENDING)
            else -> {
                val wasActive = lastReservation?.status == "approved" ||
                        lastReservation?.status == "pending"
                val archived = reservations.firstOrNull { it.status == "archived" }
                if (wasActive || archived != null) {
                    handleTermination(archived)
                } else if (lastReservation == null) {
                    showState(State.NO_RESERVATION)
                }
            }
        }
    }

    private fun handleTermination(archivedReservation: TenantReservation? = null) {
        if (terminationDialogShowing) return
        terminationDialogShowing = true
        handler.removeCallbacks(pollRunnable)

        val dormName = archivedReservation?.dorm_name?.takeIf { it.isNotBlank() } ?: "your dorm"
        val reason   = archivedReservation?.termination_reason?.trim()
            ?.takeIf { it.isNotBlank() } ?: "No reason was provided."

        if (archivedReservation != null) {
            val phone = sessionManager.getPhone().trim().filter { it.isDigit() }.takeLast(10)
            if (phone.isNotBlank()) {
                val notifPrefs = getSharedPreferences("NotificationState", MODE_PRIVATE)
                val gson       = Gson()
                val cacheKey   = "cache_$phone"
                val existing = try {
                    val json = notifPrefs.getString(cacheKey, null)
                    if (!json.isNullOrBlank()) gson.fromJson<List<TenantReservation>>(
                        json, object : TypeToken<List<TenantReservation>>() {}.type
                    ) ?: emptyList() else emptyList()
                } catch (e: Exception) { emptyList() }
                val merged = existing.filter { it.id != archivedReservation.id }.toMutableList()
                merged.add(0, archivedReservation)
                notifPrefs.edit()
                    .putString(cacheKey, gson.toJson(merged))
                    .putString("last_phone", phone)
                    .apply()
            }
        }

        AlertDialog.Builder(this)
            .setTitle("⚠ Tenancy Terminated")
            .setMessage("Your tenancy at $dormName has been terminated by the owner.\n\nReason:\n$reason")
            .setCancelable(false)
            .setPositiveButton("OK") { dialog, _ ->
                dialog.dismiss()
                terminationDialogShowing = false
                lastReservation = null
                sessionManager.markTerminated()
                startActivity(Intent(this, DashboardActivity::class.java).apply {
                    flags = Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TASK
                })
                finish()
            }
            .show()
    }

    private fun populateDashboard(r: TenantReservation) {
        lastReservation = r
        showState(State.CONFIRMED)

        tvDormName.text     = r.dorm_name
        tvDormLocation.text = r.location ?: "—"
        tvTenantName.text   = r.full_name
        tvCheckInDate.text  = formatDate(r.move_in_date)

        val fmt     = NumberFormat.getNumberInstance(Locale.US).apply { minimumFractionDigits = 0 }
        val price   = r.price_per_month?.toLong() ?: 0L
        val deposit = r.deposit?.toLong()         ?: 0L
        val advance = r.advance?.toLong()         ?: 0L

        tvMonthlyRent.text        = "₱${fmt.format(price)}"
        tvMonthlyRentSummary.text = "₱${fmt.format(price)}.00"

        val depositUsed = r.deposit_used == true
        tvDeposit.text       = if (depositUsed) "₱${fmt.format(deposit)} (Used)" else "₱${fmt.format(deposit)}"
        tvDeposit.paintFlags = if (depositUsed)
            tvDeposit.paintFlags or android.graphics.Paint.STRIKE_THRU_TEXT_FLAG
        else tvDeposit.paintFlags and android.graphics.Paint.STRIKE_THRU_TEXT_FLAG.inv()
        tvDeposit.setTextColor(ContextCompat.getColor(this,
            if (depositUsed) android.R.color.darker_gray else android.R.color.black))

        val advanceUsed = r.advance_used == true
        tvAdvance.text       = if (advanceUsed) "₱${fmt.format(advance)} (Used)" else "₱${fmt.format(advance)}"
        tvAdvance.paintFlags = if (advanceUsed)
            tvAdvance.paintFlags or android.graphics.Paint.STRIKE_THRU_TEXT_FLAG
        else tvAdvance.paintFlags and android.graphics.Paint.STRIKE_THRU_TEXT_FLAG.inv()
        tvAdvance.setTextColor(ContextCompat.getColor(this,
            if (advanceUsed) android.R.color.darker_gray else android.R.color.black))

        val paid  = r.payments_paid   ?: 0
        val total = r.duration_months ?: 1
        val currentDue          = computeCurrentPaymentDue(r.move_in_date, total)
        val currentDueDateStr   = computeNextDueDate(r.move_in_date, currentDue - 1)
        val nextUpcomingDateStr = computeNextDueDate(r.move_in_date, paid)

        when {
            paid >= total -> {
                tvPaymentStatus.text = "✓ Fully Paid"
                tvPaymentStatus.setTextColor(ContextCompat.getColor(this, R.color.payment_paid))
                tvNextDueDate.text = "—"
                tvNextDueDate.setTextColor(ContextCompat.getColor(this, R.color.text_secondary))
            }
            paid >= currentDue -> {
                tvPaymentStatus.text = "✓ Paid"
                tvPaymentStatus.setTextColor(ContextCompat.getColor(this, R.color.payment_paid))
                tvNextDueDate.text = nextUpcomingDateStr
                tvNextDueDate.setTextColor(ContextCompat.getColor(this, R.color.due_date_orange))
            }
            else -> {
                tvPaymentStatus.text = "⚠ Unpaid"
                tvPaymentStatus.setTextColor(ContextCompat.getColor(this, android.R.color.holo_red_dark))
                tvNextDueDate.text = currentDueDateStr
                tvNextDueDate.setTextColor(ContextCompat.getColor(this, android.R.color.holo_red_dark))
            }
        }

        tvReservationStatus.text = "● Reservation Confirmed"
    }

    private enum class State { CONFIRMED, PENDING, NO_RESERVATION }

    private fun showState(state: State) {
        cardConfirmed.visibility     = if (state == State.CONFIRMED)      View.VISIBLE else View.GONE
        cardPending.visibility       = if (state == State.PENDING)        View.VISIBLE else View.GONE
        cardNoReservation.visibility = if (state == State.NO_RESERVATION) View.VISIBLE else View.GONE
    }

    private fun formatDate(raw: String?): String {
        if (raw.isNullOrBlank()) return "—"
        return try {
            val parsers = listOf(
                SimpleDateFormat("yyyy-MM-dd'T'HH:mm:ss.SSS'Z'", Locale.US),
                SimpleDateFormat("yyyy-MM-dd'T'HH:mm:ss'Z'",     Locale.US),
                SimpleDateFormat("yyyy-MM-dd",                    Locale.US),
                SimpleDateFormat("dd/MM/yyyy",                    Locale.US)
            )
            val outFmt = SimpleDateFormat("MMM dd, yyyy", Locale.US)
            parsers.firstNotNullOfOrNull { runCatching { it.parse(raw) }.getOrNull() }
                ?.let { outFmt.format(it) } ?: raw
        } catch (_: Exception) { raw }
    }

    private fun computeNextDueDate(moveInDate: String?, paidMonths: Int): String {
        if (moveInDate.isNullOrBlank()) return "—"
        return try {
            val base   = parseMoveInDate(moveInDate) ?: return "—"
            val outFmt = SimpleDateFormat("MMM dd, yyyy", Locale.US)
            val cal    = Calendar.getInstance().apply { time = base; add(Calendar.MONTH, paidMonths) }
            outFmt.format(cal.time)
        } catch (_: Exception) { "—" }
    }

    private fun computeCurrentPaymentDue(moveInDate: String?, totalMonths: Int): Int {
        if (moveInDate.isNullOrBlank()) return 1
        return try {
            val base  = parseMoveInDate(moveInDate) ?: return 1
            val today = Calendar.getInstance().apply {
                set(Calendar.HOUR_OF_DAY, 0); set(Calendar.MINUTE, 0)
                set(Calendar.SECOND, 0);      set(Calendar.MILLISECOND, 0)
            }
            var n = 1
            while (n < totalMonths) {
                val due = Calendar.getInstance().apply { time = base; add(Calendar.MONTH, n) }
                if (due.after(today)) break
                n++
            }
            n
        } catch (_: Exception) { 1 }
    }

    private fun parseMoveInDate(raw: String): java.util.Date? {
        val parsers = listOf(
            SimpleDateFormat("yyyy-MM-dd'T'HH:mm:ss.SSS'Z'", Locale.US).apply { isLenient = false },
            SimpleDateFormat("yyyy-MM-dd'T'HH:mm:ss'Z'",     Locale.US).apply { isLenient = false },
            SimpleDateFormat("yyyy-MM-dd",                    Locale.US).apply { isLenient = false },
            SimpleDateFormat("dd/MM/yyyy",                    Locale.US).apply { isLenient = false }
        )
        return parsers.firstNotNullOfOrNull { runCatching { it.parse(raw) }.getOrNull() }
    }

    private fun setupBottomNav() {
        findViewById<LinearLayout>(R.id.navHome).setOnClickListener { /* already on home */ }

        // FIX: navMessages is now a FrameLayout in the XML
        findViewById<FrameLayout>(R.id.navMessages).setOnClickListener {
            startActivity(Intent(this, MessagesActivity::class.java).apply {
                flags = Intent.FLAG_ACTIVITY_REORDER_TO_FRONT
            })
        }
        findViewById<LinearLayout>(R.id.navSettings).setOnClickListener {
            startActivity(Intent(this, SettingsActivity::class.java).apply {
                flags = Intent.FLAG_ACTIVITY_REORDER_TO_FRONT
            })
        }
        findViewById<LinearLayout>(R.id.navProfile).setOnClickListener {
            startActivity(Intent(this, ProfileActivity::class.java).apply {
                flags = Intent.FLAG_ACTIVITY_REORDER_TO_FRONT
            })
        }
    }
}