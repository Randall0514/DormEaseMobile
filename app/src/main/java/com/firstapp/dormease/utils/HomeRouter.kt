package com.firstapp.dormease.utils

// FILE PATH: app/src/main/java/com/firstapp/dormease/utils/HomeRouter.kt

import android.content.Context
import android.content.Intent
import android.util.Log
import com.firstapp.dormease.DashboardActivity
import com.firstapp.dormease.activity.TenantDashboardActivity
import com.firstapp.dormease.network.RetrofitClient
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

object HomeRouter {

    private const val TAG = "HomeRouter"

    fun navigate(
        context: Context,
        scope: CoroutineScope,
        session: SessionManager,
            onStart: (() -> Unit)? = null,
            intentFlags: Int = Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TASK
    ) {
        onStart?.invoke()

        // ── Short-circuit: if we already know this tenant is terminated,
        //    go straight to DashboardActivity without a network call.
        if (session.isTerminated()) {
            Log.d(TAG, "navigate: isTerminated flag set — going to DashboardActivity immediately")
            context.startActivity(
                Intent(context, DashboardActivity::class.java).apply {
                        flags = intentFlags
                }
            )
            return
        }

        scope.launch {
            try {
                val api = RetrofitClient.getApiService(context)

                val resp = api.getMyReservations()
                val reservations = if (resp.isSuccessful) {
                    val list = resp.body() ?: emptyList()
                    val hasActive = list.any { r ->
                        r.status == "approved" || r.status == "pending"
                    }
                    if (hasActive) {
                        list.firstOrNull { it.phone.isNotBlank() }?.phone?.let { serverPhone ->
                            val digits = serverPhone.filter { it.isDigit() }.takeLast(10)
                            if (digits.isNotBlank()) {
                                session.savePhone("+63$digits")
                                Log.d(TAG, "navigate: saved phone from token lookup: +63$digits")
                            }
                        }
                    }
                    list
                } else {
                    emptyList()
                }

                // ── Determine if tenant has an active reservation ─────────────
                // "active" = approved AND not cancelled by tenant, OR pending
                val hasActive = reservations.any { r ->
                    when (r.status) {
                        "approved" -> r.tenant_action != "cancelled"
                        "pending"  -> true
                        else       -> false
                    }
                }

                // Terminated = has archived reservations but NO active ones.
                val isTerminated = reservations.isNotEmpty() &&
                        !hasActive &&
                        reservations.any { it.status == "archived" }

                val archivedReservation = reservations.firstOrNull { it.status == "archived" }

                if (isTerminated) {
                    Log.d(TAG, "navigate: terminated — calling markTerminated()")
                    session.markTerminated(
                        archivedReservation?.dorm_name,
                        archivedReservation?.termination_reason
                    )
                }

                Log.d(TAG, "navigate: total=${reservations.size} hasActive=$hasActive isTerminated=$isTerminated")

                withContext(Dispatchers.Main) {
                    val target = if (hasActive) TenantDashboardActivity::class.java
                    else           DashboardActivity::class.java
                    context.startActivity(
                        Intent(context, target).apply {
                                flags = intentFlags
                        }
                    )
                }
            } catch (e: Exception) {
                Log.e(TAG, "navigate error: ${e.message}")
                // On network error: fall back to prior verified reservation hint in NotificationState.
                withContext(Dispatchers.Main) {
                    val target = when {
                        session.isTerminated() -> DashboardActivity::class.java
                        else -> {
                            // Check NotificationState last_phone (only set by savePhone() after real reservation)
                            val notifPrefs = context.getSharedPreferences("NotificationState", Context.MODE_PRIVATE)
                            val lastPhone  = notifPrefs.getString("last_phone", "") ?: ""
                            if (lastPhone.isNotBlank()) TenantDashboardActivity::class.java
                            else DashboardActivity::class.java
                        }
                    }
                    context.startActivity(
                        Intent(context, target).apply {
                                flags = intentFlags
                        }
                    )
                }
            }
        }
    }
}