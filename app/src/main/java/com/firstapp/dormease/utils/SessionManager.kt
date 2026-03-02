package com.firstapp.dormease.utils

// File path: app/src/main/java/com/firstapp/dormease/utils/SessionManager.kt

import android.content.Context
import android.content.SharedPreferences
import java.util.Date

class SessionManager(context: Context) {

    private val sharedPreferences: SharedPreferences =
        context.getSharedPreferences("DormEaseSession", Context.MODE_PRIVATE)

    companion object {
        private const val USER_TOKEN       = "user_token"
        private const val TOKEN_EXPIRATION = "token_expiration"
        private const val KEY_NAME         = "user_name"
        private const val KEY_EMAIL        = "user_email"
        private const val KEY_USERNAME     = "user_username"
        private const val KEY_PHONE        = "user_phone"
        private const val KEY_ROLE         = "user_role"
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
        role: String = "Tenant"
    ) {
        saveAuthToken(token)
        sharedPreferences.edit()
            .putString(KEY_NAME,     name)
            .putString(KEY_EMAIL,    email)
            .putString(KEY_USERNAME, username)
            .putString(KEY_PHONE,    phone)
            .putString(KEY_ROLE,     role)
            .apply()
    }

    fun savePhone(phone: String) {
        sharedPreferences.edit().putString(KEY_PHONE, phone).apply()
    }

    // ─── Getters ──────────────────────────────────────────────────────────────

    fun getName()     : String = sharedPreferences.getString(KEY_NAME,     "User")   ?: "User"
    fun getEmail()    : String = sharedPreferences.getString(KEY_EMAIL,    "—")      ?: "—"
    fun getUsername() : String = sharedPreferences.getString(KEY_USERNAME, "—")      ?: "—"
    fun getPhone()    : String = sharedPreferences.getString(KEY_PHONE,    "")       ?: ""
    fun getRole()     : String = sharedPreferences.getString(KEY_ROLE,     "Tenant") ?: "Tenant"

    fun isLoggedIn(): Boolean = !fetchAuthToken().isNullOrBlank() && !isTokenExpired()

    // ─── Logout ───────────────────────────────────────────────────────────────
    //
    // RULE: Never call .clear() here. Remove each key individually.
    //
    // KEY_PHONE is intentionally NOT removed.
    // NotificationsActivity saves the phone into a separate "NotificationState"
    // SharedPreferences file as KEY_LAST_PHONE, but keeping it here too means
    // resolveUserKey() in NotificationsActivity has two ways to find the phone.
    //
    fun clearSession() {
        sharedPreferences.edit()
            .remove(USER_TOKEN)
            .remove(TOKEN_EXPIRATION)
            .remove(KEY_NAME)
            .remove(KEY_EMAIL)
            .remove(KEY_USERNAME)
            .remove(KEY_ROLE)
            // KEY_PHONE → intentionally kept
            .apply()
    }
}