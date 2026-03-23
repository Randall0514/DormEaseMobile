package com.firstapp.dormease

// FILE PATH: app/src/main/java/com/firstapp/dormease/MainActivity.kt

import android.content.Intent
import android.os.Bundle
import android.util.Log
import androidx.appcompat.app.AppCompatActivity
import com.firstapp.dormease.activity.TenantDashboardActivity
import com.firstapp.dormease.network.RetrofitClient
import com.firstapp.dormease.network.SocketManager
import com.firstapp.dormease.utils.NotificationHelper
import com.firstapp.dormease.utils.SessionManager
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import org.json.JSONObject

class MainActivity : AppCompatActivity() {

    private lateinit var sessionManager: SessionManager
    private val scope = CoroutineScope(Dispatchers.IO + SupervisorJob())

    private val onReservationUpdate: (JSONObject) -> Unit = { data ->
        val message = data.optString("message", "Reservation status changed")
        val status  = data.optString("tenantAction", data.optString("status", ""))
        NotificationHelper.show(
            context = applicationContext,
            title   = "Reservation Update",
            body    = message
        )
        runOnUiThread {
            Log.d("MainActivity", "Reservation updated [$status]: $data")
        }
    }

    private val onNotification: (JSONObject) -> Unit = { data ->
        val message = data.optString("message", "New notification")
        val type    = data.optString("type", "info")
        NotificationHelper.show(
            context = applicationContext,
            title   = "DormEase",
            body    = message
        )
        runOnUiThread {
            Log.d("MainActivity", "Notification [$type]: $message")
        }
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        sessionManager = SessionManager(this)

        val token = sessionManager.fetchAuthToken()

        if (token != null && !sessionManager.isTokenExpired()) {
            SocketManager.connect(token)
            SocketManager.addReservationUpdateListener(onReservationUpdate)
            SocketManager.addNotificationListener(onNotification)
            routeLoggedInUser()
        } else {
            goTo(LoginActivity::class.java)
        }
    }

    override fun onDestroy() {
        scope.cancel()
        SocketManager.removeReservationUpdateListener(onReservationUpdate)
        SocketManager.removeNotificationListener(onNotification)
        super.onDestroy()
    }

    private fun routeLoggedInUser() {
        // ── If already marked terminated, skip network call entirely ──────────
        if (sessionManager.isTerminated()) {
            Log.d("MainActivity", "isTerminated=true → DashboardActivity")
            goTo(DashboardActivity::class.java)
            return
        }

        val userId = sessionManager.getUserId()
        Log.d("MainActivity", "routeLoggedInUser: userId=$userId")

        scope.launch {
            try {
                val api = RetrofitClient.getApiService(applicationContext)

                val resp = api.getMyReservations()
                val reservations = if (resp.isSuccessful) {
                    val list = resp.body() ?: emptyList()
                    list.firstOrNull { it.phone.isNotBlank() }?.phone?.let { serverPhone ->
                        val digits = serverPhone.filter { it.isDigit() }.takeLast(10)
                        if (digits.isNotBlank()) {
                            sessionManager.savePhone("+63$digits")
                            getSharedPreferences("NotificationState", MODE_PRIVATE)
                                .edit().putString("last_phone", digits).apply()
                        }
                    }
                    list
                } else {
                    emptyList()
                }

                Log.d("MainActivity", "Got ${reservations.size} reservations")
                reservations.forEach {
                    Log.d("MainActivity", "  id=${it.id} status=${it.status} tenant_action=${it.tenant_action}")
                }

                val hasActiveReservation = reservations.any {
                    (it.status == "approved" || it.status == "pending") && it.tenant_action != "cancelled"
                }

                // ── Termination check BEFORE deciding route ───────────────────
                val isTerminated = reservations.isNotEmpty() &&
                        !hasActiveReservation &&
                        reservations.any  { it.status == "archived" }

                val archivedReservation = reservations.firstOrNull { it.status == "archived" }

                if (isTerminated) {
                    Log.d("MainActivity", "Terminated — calling markTerminated()")
                    sessionManager.markTerminated(
                        archivedReservation?.dorm_name,
                        archivedReservation?.termination_reason
                    )
                }

                // Only approved or pending = active
                val hasApprovedOrPending = hasActiveReservation

                withContext(Dispatchers.Main) {
                    if (hasApprovedOrPending) {
                        Log.d("MainActivity", "→ TenantDashboardActivity")
                        goTo(TenantDashboardActivity::class.java)
                    } else {
                        Log.d("MainActivity", "→ DashboardActivity")
                        goTo(DashboardActivity::class.java)
                    }
                }

            } catch (e: Exception) {
                Log.e("MainActivity", "routeLoggedInUser error: ${e.message}")
                withContext(Dispatchers.Main) {
                    // On error: respect terminated flag first
                    if (sessionManager.isTerminated()) {
                        goTo(DashboardActivity::class.java)
                    } else if (sessionManager.getPhone().isNotBlank()) {
                        goTo(TenantDashboardActivity::class.java)
                    } else {
                        goTo(DashboardActivity::class.java)
                    }
                }
            }
        }
    }

    private fun goTo(cls: Class<*>) {
        startActivity(
            Intent(this, cls).apply {
                flags = Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TASK
            }
        )
        finish()
    }
}