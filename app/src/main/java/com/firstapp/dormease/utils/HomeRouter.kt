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
        onStart: (() -> Unit)? = null
    ) {
        onStart?.invoke()

        // ── Short-circuit: if we already know this tenant is terminated,
        //    go straight to DashboardActivity without a network call.
        if (session.isTerminated()) {
            Log.d(TAG, "navigate: isTerminated flag set — going to DashboardActivity immediately")
            context.startActivity(
                Intent(context, DashboardActivity::class.java).apply {
                    flags = Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TASK
                }
            )
            return
        }

        val rawPhone = session.getPhone().trim()
        val userId   = session.getUserId()

        scope.launch {
            try {
                val api = RetrofitClient.getApiService(context)

                // ── Resolve the best phone to query with ─────────────────────
                // FIX: We only use NotificationState last_phone as a fallback.
                // The presence of a phone number alone is NOT enough to route to
                // TenantDashboard — we MUST verify against actual server reservations.
                val resolvedPhone: String = when {
                    rawPhone.isNotBlank() -> {
                        rawPhone.filter { it.isDigit() }.takeLast(10)
                    }
                    else -> {
                        // Fallback: check NotificationState for last_phone.
                        // This is only set when a real reservation was confirmed, so it's safe.
                        val notifPrefs = context.getSharedPreferences("NotificationState", Context.MODE_PRIVATE)
                        val lastPhone  = notifPrefs.getString("last_phone", "") ?: ""
                        lastPhone.filter { it.isDigit() }.takeLast(10)
                    }
                }

                val reservations = when {
                    resolvedPhone.isNotBlank() -> {
                        val resp = api.getTenantReservations("+63$resolvedPhone")
                        if (resp.isSuccessful) {
                            val list = resp.body() ?: emptyList()
                            // Sync phone back into SessionManager ONLY if there are active reservations
                            val hasActive = list.any { r ->
                                r.status == "approved" || r.status == "pending"
                            }
                            if (rawPhone.isBlank() && hasActive) {
                                val serverPhone = list.firstOrNull { it.phone.isNotBlank() }?.phone
                                if (!serverPhone.isNullOrBlank()) {
                                    val digits = serverPhone.filter { it.isDigit() }.takeLast(10)
                                    if (digits.isNotBlank()) {
                                        // savePhone() writes to NotificationState too — safe here
                                        // because we just confirmed an active reservation exists.
                                        session.savePhone("+63$digits")
                                        Log.d(TAG, "navigate: synced phone from server: +63$digits")
                                    }
                                }
                            }
                            list
                        } else emptyList()
                    }
                    userId > 0 -> {
                        val resp = api.getMyReservations()
                        if (resp.isSuccessful) {
                            val list = resp.body() ?: emptyList()
                            // Save phone ONLY if there is an active reservation
                            val hasActive = list.any { r ->
                                r.status == "approved" || r.status == "pending"
                            }
                            if (hasActive) {
                                list.firstOrNull { it.phone.isNotBlank() }?.phone?.let { serverPhone ->
                                    val digits = serverPhone.filter { it.isDigit() }.takeLast(10)
                                    if (digits.isNotBlank()) {
                                        session.savePhone("+63$digits")
                                        Log.d(TAG, "navigate: saved phone from userId lookup: +63$digits")
                                    }
                                }
                            }
                            list
                        } else emptyList()
                    }
                    else -> emptyList()
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

                if (isTerminated) {
                    Log.d(TAG, "navigate: terminated — calling markTerminated()")
                    session.markTerminated()
                }

                Log.d(TAG, "navigate: total=${reservations.size} hasActive=$hasActive isTerminated=$isTerminated")

                withContext(Dispatchers.Main) {
                    val target = if (hasActive) TenantDashboardActivity::class.java
                    else           DashboardActivity::class.java
                    context.startActivity(
                        Intent(context, target).apply {
                            flags = Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TASK
                        }
                    )
                }
            } catch (e: Exception) {
                Log.e(TAG, "navigate error: ${e.message}")
                // On network error: ALWAYS fall back to DashboardActivity unless
                // NotificationState last_phone exists (meaning a real prior reservation was seen).
                // Do NOT use raw session phone alone — the user might have just typed it in Personal Info.
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
                            flags = Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TASK
                        }
                    )
                }
            }
        }
    }
}