package com.firstapp.dormease.utils

// File path: app/src/main/java/com/firstapp/dormease/utils/SessionManager.kt

import android.content.Context
import android.content.SharedPreferences
import com.firstapp.dormease.network.MessageRepository
import java.util.Date

class SessionManager(context: Context) {

    private val sharedPreferences: SharedPreferences =
        context.getSharedPreferences("DormEaseSession", Context.MODE_PRIVATE)

    private val notificationState: SharedPreferences =
        context.getSharedPreferences("NotificationState", Context.MODE_PRIVATE)

    companion object {
        private const val USER_TOKEN       = "user_token"
        private const val TOKEN_EXPIRATION = "token_expiration"
        private const val KEY_NAME         = "user_name"
        private const val KEY_EMAIL        = "user_email"
        private const val KEY_USERNAME     = "user_username"
        private const val KEY_PHONE        = "user_phone"
        private const val KEY_ROLE         = "user_role"
        private const val KEY_USER_ID      = "user_id"
        private const val KEY_TERMINATED   = "account_terminated"
    }

    // ─── Token ────────────────────────────────────────────────────────────────

    fun saveAuthToken(token: String) {
        val expirationTime = Date(System.currentTimeMillis() + 7L * 24 * 60 * 60 * 1000)
        sharedPreferences.edit()
            .putString(USER_TOKEN, token)
            .putLong(TOKEN_EXPIRATION, expirationTime.time)
            .apply()
    }

    fun fetchAuthToken(): String? = sharedPreferences.getString(USER_TOKEN, null)

    fun isTokenExpired(): Boolean {
        val expiration = sharedPreferences.getLong(TOKEN_EXPIRATION, 0)
        return Date().after(Date(expiration))
    }

    fun clearAuthToken() {
        sharedPreferences.edit()
            .remove(USER_TOKEN)
            .remove(TOKEN_EXPIRATION)
            .apply()
    }

    // ─── User session ─────────────────────────────────────────────────────────

    fun saveUserSession(
        token: String,
        name: String,
        email: String,
        username: String,
        phone: String = "",
        role: String = "Tenant",
        userId: Int = -1
    ) {
        saveAuthToken(token)
        sharedPreferences.edit()
            .putString(KEY_NAME,     name)
            .putString(KEY_EMAIL,    email)
            .putString(KEY_USERNAME, username)
            .putString(KEY_PHONE,    phone)
            .putString(KEY_ROLE,     role)
            .putInt(KEY_USER_ID,     userId)
            .putBoolean(KEY_TERMINATED, false)  // reset terminated flag on new session
            .apply()
    }

    fun savePhone(phone: String) {
        sharedPreferences.edit().putString(KEY_PHONE, phone).apply()
        val digits = phone.filter { it.isDigit() }.takeLast(10)
        if (digits.isNotBlank()) {
            notificationState.edit().putString("last_phone", digits).apply()
        }
    }

    // ─── Termination ──────────────────────────────────────────────────────────
    //
    // Call this when the server confirms the tenant's reservation is archived.
    // Clears the phone from BOTH SharedPreferences stores so HomeRouter and
    // MainActivity stop routing back to TenantDashboardActivity.
    //
    fun markTerminated() {
        sharedPreferences.edit()
            .putString(KEY_PHONE, "")
            .putBoolean(KEY_TERMINATED, true)
            .apply()
        // Also wipe from NotificationState — this is the key that was causing
        // the phone to survive across navigation and re-route to TenantDashboard.
        notificationState.edit()
            .remove("last_phone")
            .apply()
    }

    fun isTerminated(): Boolean =
        sharedPreferences.getBoolean(KEY_TERMINATED, false)

    // ─── Getters ──────────────────────────────────────────────────────────────

    fun getName()     : String = sharedPreferences.getString(KEY_NAME,     "User")   ?: "User"
    fun getEmail()    : String = sharedPreferences.getString(KEY_EMAIL,    "—")      ?: "—"
    fun getUsername() : String = sharedPreferences.getString(KEY_USERNAME, "—")      ?: "—"
    fun getPhone()    : String = sharedPreferences.getString(KEY_PHONE,    "")       ?: ""
    fun getRole()     : String = sharedPreferences.getString(KEY_ROLE,     "Tenant") ?: "Tenant"
    fun getUserId()   : Int    = sharedPreferences.getInt(KEY_USER_ID,     -1)

    fun isLoggedIn(): Boolean = !fetchAuthToken().isNullOrBlank() && !isTokenExpired()

    // ─── Logout ───────────────────────────────────────────────────────────────

    fun clearSession() {
        sharedPreferences.edit()
            .remove(USER_TOKEN)
            .remove(TOKEN_EXPIRATION)
            .remove(KEY_NAME)
            .remove(KEY_EMAIL)
            .remove(KEY_USERNAME)
            .remove(KEY_ROLE)
            .remove(KEY_USER_ID)
            .remove(KEY_PHONE)
            .remove(KEY_TERMINATED)
            .apply()

        notificationState.edit()
            .remove("last_phone")
            .apply()

        MessageRepository.clearAll()
    }
}