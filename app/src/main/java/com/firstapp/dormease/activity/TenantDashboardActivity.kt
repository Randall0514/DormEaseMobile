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
import com.firstapp.dormease.utils.SessionManager
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
    private val scope   = CoroutineScope(Dispatchers.IO + SupervisorJob())
    private val handler = Handler(Looper.getMainLooper())

    private var lastReservation: TenantReservation? = null

    // ── Views ─────────────────────────────────────────────────────────────────
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

    // cardConfirmed is LinearLayout in XML
    // cardPending and cardNoReservation are CardView in XML
    private lateinit var cardConfirmed       : LinearLayout
    private lateinit var cardPending         : CardView
    private lateinit var cardNoReservation   : CardView

    // ── Polling ───────────────────────────────────────────────────────────────
    private val pollRunnable = object : Runnable {
        override fun run() {
            loadReservation()
            fetchNotificationCount()
            handler.postDelayed(this, 15_000L)
        }
    }

    // ─────────────────────────────────────────────────────────────────────────
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_tenant_dashboard)
        supportActionBar?.hide()

        sessionManager = SessionManager(this)
        bindViews()
        setupGreeting()
        setupBottomNav()

        // Bell → open notifications
        findViewById<FrameLayout>(R.id.notificationBellContainer).setOnClickListener {
            startActivity(Intent(this, NotificationsActivity::class.java))
        }

        // If navigated here right after tenant tapped Accept,
        // show confirmed state immediately without waiting for network.
        if (intent.getBooleanExtra(EXTRA_FORCE_CONFIRMED, false)) {
            showState(State.CONFIRMED)
            handler.postDelayed({ loadReservation() }, 1_500L)
        }
    }

    override fun onResume() {
        super.onResume()
        lastReservation?.let { populateDashboard(it) }
        handler.post(pollRunnable)
    }

    override fun onPause() {
        super.onPause()
        handler.removeCallbacks(pollRunnable)
    }

    override fun onDestroy() {
        scope.cancel()
        super.onDestroy()
    }

    // ─────────────────────────────────────────────────────────────────────────
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

    // ─────────────────────────────────────────────────────────────────────────
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

    // ─────────────────────────────────────────────────────────────────────────
    private fun fetchNotificationCount() {
        val rawPhone = sessionManager.getPhone().trim()
        if (rawPhone.isBlank()) { updateBadge(0); return }
        val phoneParam = "+63${rawPhone.filter { it.isDigit() }.takeLast(10)}"
        scope.launch {
            try {
                val api      = RetrofitClient.getApiService(applicationContext)
                val response = api.getTenantReservations(phoneParam)
                val count = if (response.isSuccessful) {
                    response.body()
                        ?.count { it.status == "approved" || it.status == "rejected" }
                        ?: 0
                } else 0
                withContext(Dispatchers.Main) { updateBadge(count) }
            } catch (e: Exception) {
                Log.w("TenantDashboard", "Badge fetch failed: ${e.message}")
            }
        }
    }

    private fun updateBadge(count: Int) {
        if (count > 0) {
            tvNotificationBadge.visibility = View.VISIBLE
            tvNotificationBadge.text       = if (count > 9) "9+" else count.toString()
        } else {
            tvNotificationBadge.visibility = View.GONE
        }
    }

    // ─────────────────────────────────────────────────────────────────────────
    private fun loadReservation() {
        val rawPhone = sessionManager.getPhone().trim()
        val userId   = sessionManager.getUserId()
        when {
            rawPhone.isNotBlank() -> {
                val digits = rawPhone.filter { it.isDigit() }.takeLast(10)
                fetchByPhone("+63$digits")
            }
            userId > 0 -> fetchByUserId()
            else -> { if (lastReservation == null) showState(State.NO_RESERVATION) }
        }
    }

    // ─────────────────────────────────────────────────────────────────────────
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
                val list = response.body() ?: emptyList()
                withContext(Dispatchers.Main) { resolveAndShow(list) }
            } catch (e: Exception) {
                Log.e("TenantDashboard", "fetchByPhone error: ${e.message}")
                withContext(Dispatchers.Main) {
                    if (lastReservation == null) showState(State.NO_RESERVATION)
                }
            }
        }
    }

    // ─────────────────────────────────────────────────────────────────────────
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

    // ─────────────────────────────────────────────────────────────────────────
    // Core routing logic.
    //
    // Priority:
    //   1. approved + not cancelled  → show confirmed dashboard
    //   2. pending                   → show pending card
    //   3. ALL non-rejected/archived → only archived/rejected remain
    //      → tenant has been terminated, redirect to DashboardActivity (browse dorms)
    //   4. nothing at all            → show no-reservation card
    // ─────────────────────────────────────────────────────────────────────────
    private fun resolveAndShow(reservations: List<TenantReservation>) {
        val approved = reservations.firstOrNull {
            it.status == "approved" && it.tenant_action != "cancelled"
        }
        val pending = reservations.firstOrNull { it.status == "pending" }

        when {
            approved != null -> {
                // Check if the previously confirmed reservation just became archived
                // (i.e. the owner terminated tenancy while the app was open).
                if (lastReservation != null &&
                    lastReservation!!.status == "approved" &&
                    reservations.none {
                        it.id == lastReservation!!.id && it.status == "approved"
                    }
                ) {
                    handleTermination()
                    return
                }
                populateDashboard(approved)
            }

            pending != null -> showState(State.PENDING)

            else -> {
                // No approved or pending reservation remains.
                // If the tenant previously had an active reservation (lastReservation
                // was approved) but now it's gone / archived, they were terminated.
                val wasActive = lastReservation?.status == "approved" ||
                        lastReservation?.status == "pending"
                val hasArchived = reservations.any { it.status == "archived" }

                if (wasActive || hasArchived) {
                    // Terminated — send back to the browse-dorms screen.
                    handleTermination()
                } else if (lastReservation == null) {
                    showState(State.NO_RESERVATION)
                }
                // else: keep showing whatever was on screen while we wait for
                //       a definitive server answer (avoids flicker on network delay)
            }
        }
    }

    // ─────────────────────────────────────────────────────────────────────────
    // Navigate to DashboardActivity (available dorms) and clear the back stack
    // so the tenant cannot press Back and return to TenantDashboardActivity.
    // ─────────────────────────────────────────────────────────────────────────
    private fun handleTermination() {
        Log.d("TenantDashboard", "Tenancy terminated — routing to DashboardActivity")
        lastReservation = null
        // Wipe phone from both SharedPreferences stores so HomeRouter and
        // MainActivity never route back to TenantDashboardActivity again.
        sessionManager.markTerminated()
        startActivity(
            Intent(this, DashboardActivity::class.java).apply {
                flags = Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TASK
            }
        )
        finish()
    }

    // ─────────────────────────────────────────────────────────────────────────
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
        tvDeposit.text            = "₱${fmt.format(deposit)}"
        tvAdvance.text            = "₱${fmt.format(advance)}"
        tvMonthlyRentSummary.text = "₱${fmt.format(price)}.00"

        val paid  = r.payments_paid   ?: 0
        val total = r.duration_months ?: 1

        // Mirror the web dashboard logic exactly:
        // The web marks payment #N as PAID only when N <= payments_paid.
        // "Current due" = the month number whose due date has arrived today
        //   (i.e. how many monthly periods have elapsed since move-in, capped at total).
        // If payments_paid >= currentDue  → current period is paid.
        // If payments_paid < currentDue   → current period is UNPAID.
        // If payments_paid >= total       → contract fully paid.
        val currentDue = computeCurrentPaymentDue(r.move_in_date, total)

        when {
            paid >= total -> {
                // Every month in the contract has been paid
                tvPaymentStatus.text = "✓ Fully Paid"
                tvPaymentStatus.setTextColor(ContextCompat.getColor(this, R.color.payment_paid))
                tvNextDueDate.text = "—"
                tvNextDueDate.setTextColor(ContextCompat.getColor(this, R.color.text_secondary))
            }
            paid >= currentDue -> {
                // The current due payment has been marked paid by the owner
                tvPaymentStatus.text = "✓ Paid"
                tvPaymentStatus.setTextColor(ContextCompat.getColor(this, R.color.payment_paid))
                // Show the date of the NEXT unpaid payment (paid + 1)
                tvNextDueDate.text = computeNextDueDate(r.move_in_date, paid)
                tvNextDueDate.setTextColor(ContextCompat.getColor(this, R.color.due_date_orange))
            }
            else -> {
                // The owner has NOT yet marked the current due payment as paid
                tvPaymentStatus.text = "⚠ Unpaid"
                tvPaymentStatus.setTextColor(
                    ContextCompat.getColor(this, android.R.color.holo_red_dark)
                )
                // Show the due date of the unpaid payment (paid + 1 = currentDue)
                tvNextDueDate.text = computeNextDueDate(r.move_in_date, paid)
                tvNextDueDate.setTextColor(ContextCompat.getColor(this, R.color.due_date_orange))
            }
        }

        tvReservationStatus.text = "● Reservation Confirmed"
    }

    // ─────────────────────────────────────────────────────────────────────────
    private enum class State { CONFIRMED, PENDING, NO_RESERVATION }

    private fun showState(state: State) {
        cardConfirmed.visibility     = if (state == State.CONFIRMED)      View.VISIBLE else View.GONE
        cardPending.visibility       = if (state == State.PENDING)        View.VISIBLE else View.GONE
        cardNoReservation.visibility = if (state == State.NO_RESERVATION) View.VISIBLE else View.GONE
    }

    // ─────────────────────────────────────────────────────────────────────────
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
            val date   = parsers.firstNotNullOfOrNull { runCatching { it.parse(raw) }.getOrNull() }
            date?.let { outFmt.format(it) } ?: raw
        } catch (_: Exception) { raw }
    }

    // Returns the formatted date of the next payment due after paidMonths paid.
    // e.g. if paid=2 and move-in was Jan 1, returns "Apr 01, YYYY" (month 3 due date).
    // Web dashboard schedule: payment #N is due at moveInDate + (N-1) months.
    // So the next unpaid payment number is (paidMonths + 1), and its due date is
    // moveInDate + paidMonths months (NOT +paidMonths+1 — that would be one ahead).
    // Example: move-in = March 1, paid = 0 → payment #1 due = March 1 (+ 0 months).
    //          move-in = March 1, paid = 1 → payment #2 due = April 1 (+ 1 month).
    private fun computeNextDueDate(moveInDate: String?, paidMonths: Int): String {
        if (moveInDate.isNullOrBlank()) return "—"
        return try {
            val base = parseMoveInDate(moveInDate) ?: return "—"
            val outFmt = SimpleDateFormat("MMM dd, yyyy", Locale.US)
            val cal = Calendar.getInstance().apply {
                time = base
                add(Calendar.MONTH, paidMonths) // paidMonths, not paidMonths+1
            }
            outFmt.format(cal.time)
        } catch (_: Exception) { "—" }
    }

    // Mirrors the web dashboard's payment schedule logic:
    // Count how many full monthly periods have elapsed since move-in as of today.
    // That is the payment number that is currently "due" (minimum 1, max = total).
    // e.g. on the exact move-in day = payment 1 is due.
    //      one month later           = payment 2 is due.
    private fun computeCurrentPaymentDue(moveInDate: String?, totalMonths: Int): Int {
        if (moveInDate.isNullOrBlank()) return 1
        return try {
            val base = parseMoveInDate(moveInDate) ?: return 1
            val today = Calendar.getInstance().apply {
                set(Calendar.HOUR_OF_DAY, 0); set(Calendar.MINUTE, 0)
                set(Calendar.SECOND, 0);      set(Calendar.MILLISECOND, 0)
            }
            // Walk forward month by month from move-in until we pass today
            val cal = Calendar.getInstance().apply { time = base }
            var paymentNumber = 1
            while (paymentNumber < totalMonths) {
                val nextDue = Calendar.getInstance().apply {
                    time = base
                    add(Calendar.MONTH, paymentNumber) // payment N+1 is due N months after move-in
                }
                if (nextDue.after(today)) break   // next due hasn't arrived yet → current = paymentNumber
                paymentNumber++
            }
            paymentNumber
        } catch (_: Exception) { 1 }
    }

    // Shared date parser — matches all formats the server returns.
    private fun parseMoveInDate(raw: String): java.util.Date? {
        val parsers = listOf(
            SimpleDateFormat("yyyy-MM-dd'T'HH:mm:ss.SSS'Z'", Locale.US).apply { isLenient = false },
            SimpleDateFormat("yyyy-MM-dd'T'HH:mm:ss'Z'",     Locale.US).apply { isLenient = false },
            SimpleDateFormat("yyyy-MM-dd",                    Locale.US).apply { isLenient = false },
            SimpleDateFormat("dd/MM/yyyy",                    Locale.US).apply { isLenient = false }
        )
        return parsers.firstNotNullOfOrNull { runCatching { it.parse(raw) }.getOrNull() }
    }

    // ─────────────────────────────────────────────────────────────────────────
    private fun setupBottomNav() {
        // Home = this screen, no action needed
        findViewById<LinearLayout>(R.id.navHome).setOnClickListener { /* already here */ }

        findViewById<LinearLayout>(R.id.navMessages).setOnClickListener {
            startActivity(Intent(this, MessagesActivity::class.java))
        }
        findViewById<LinearLayout>(R.id.navSettings).setOnClickListener {
            startActivity(Intent(this, SettingsActivity::class.java))
        }
        findViewById<LinearLayout>(R.id.navProfile).setOnClickListener {
            startActivity(Intent(this, ProfileActivity::class.java))
        }
    }
}