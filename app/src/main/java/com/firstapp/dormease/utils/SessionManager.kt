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
        private const val KEY_TERM_DORM    = "terminated_dorm_name"
        private const val KEY_TERM_REASON  = "terminated_reason"
        private const val KEY_TERM_PENDING = "terminated_notice_pending"
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
            .putBoolean(KEY_TERMINATED, false)
            .apply()
    }

    // ─── Individual save helpers (used during signup / profile edits) ─────────

    /** Save full name — used after signup so reservation form can auto-fill it. */
    fun saveFullName(name: String) {
        sharedPreferences.edit().putString(KEY_NAME, name).apply()
    }

    /** Save email — used after signup so reservation form can auto-fill it. */
    fun saveEmail(email: String) {
        sharedPreferences.edit().putString(KEY_EMAIL, email).apply()
    }

    // ─── Phone helpers ────────────────────────────────────────────────────────

    /**
     * Called ONLY when we have confirmed from the server that this phone belongs
     * to an active (approved/pending) reservation.
     * Writes to BOTH DormEaseSession AND NotificationState last_phone.
     */
    fun savePhone(phone: String) {
        sharedPreferences.edit().putString(KEY_PHONE, phone).apply()
        val digits = phone.filter { it.isDigit() }.takeLast(10)
        if (digits.isNotBlank()) {
            notificationState.edit().putString("last_phone", digits).apply()
        }
    }

    /**
     * Called when the user edits their phone number manually.
     * Saves to DormEaseSession ONLY — does NOT touch NotificationState last_phone.
     */
    fun savePhoneOnly(phone: String) {
        sharedPreferences.edit().putString(KEY_PHONE, phone).apply()
    }

    // ─── Termination ──────────────────────────────────────────────────────────

    fun markTerminated(dormName: String? = null, reason: String? = null) {
        sharedPreferences.edit()
            .putString(KEY_PHONE, "")
            .putBoolean(KEY_TERMINATED, true)
            .putString(KEY_TERM_DORM, dormName?.takeIf { it.isNotBlank() })
            .putString(KEY_TERM_REASON, reason?.takeIf { it.isNotBlank() })
            .putBoolean(KEY_TERM_PENDING, true)
            .apply()
        notificationState.edit()
            .remove("last_phone")
            .apply()
    }

    fun consumeTerminationNotice(): Pair<String, String>? {
        if (!sharedPreferences.getBoolean(KEY_TERM_PENDING, false)) return null

        val dormName = sharedPreferences.getString(KEY_TERM_DORM, null)
            ?.takeIf { it.isNotBlank() }
            ?: "your dorm"
        val reason = sharedPreferences.getString(KEY_TERM_REASON, null)
            ?.takeIf { it.isNotBlank() }
            ?: "No reason was provided by the owner."

        sharedPreferences.edit().putBoolean(KEY_TERM_PENDING, false).apply()
        return dormName to reason
    }

    fun isTerminated(): Boolean =
        sharedPreferences.getBoolean(KEY_TERMINATED, false)

    // ─── Getters ──────────────────────────────────────────────────────────────

    fun getName()     : String = sharedPreferences.getString(KEY_NAME,     "User")   ?: "User"
    fun getEmail()    : String = sharedPreferences.getString(KEY_EMAIL,    "")       ?: ""
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
            .remove(KEY_TERM_DORM)
            .remove(KEY_TERM_REASON)
            .remove(KEY_TERM_PENDING)
            .apply()

        notificationState.edit()
            .remove("last_phone")
            .apply()

        MessageRepository.clearAll()
    }
}