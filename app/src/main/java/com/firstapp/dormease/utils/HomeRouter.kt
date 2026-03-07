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

                val reservations = when {
                    rawPhone.isNotBlank() -> {
                        val digits = rawPhone.filter { it.isDigit() }.takeLast(10)
                        val resp   = api.getTenantReservations("+63$digits")
                        if (resp.isSuccessful) resp.body() ?: emptyList() else emptyList()
                    }
                    userId > 0 -> {
                        val resp = api.getMyReservations()
                        if (resp.isSuccessful) {
                            val list = resp.body() ?: emptyList()
                            list.firstOrNull { it.phone.isNotBlank() }?.phone?.let { serverPhone ->
                                val digits = serverPhone.filter { it.isDigit() }.takeLast(10)
                                if (digits.isNotBlank()) session.savePhone("+63$digits")
                            }
                            list
                        } else emptyList()
                    }
                    else -> emptyList()
                }

                // Only approved or pending counts as "active".
                val hasActive = reservations.any {
                    it.status == "approved" || it.status == "pending"
                }

                // Terminated = has archived reservations but NO active ones.
                // Call markTerminated() which clears phone from BOTH SharedPreferences
                // stores so future navigation never routes back to TenantDashboardActivity.
                val isTerminated = reservations.isNotEmpty() &&
                        reservations.none { it.status == "approved" || it.status == "pending" } &&
                        reservations.any  { it.status == "archived" }

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
                // On network error: if already marked terminated go to browse screen.
                // Otherwise fall back based on whether phone is still set.
                withContext(Dispatchers.Main) {
                    val target = when {
                        session.isTerminated()          -> DashboardActivity::class.java
                        session.getPhone().isNotBlank() -> TenantDashboardActivity::class.java
                        else                            -> DashboardActivity::class.java
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