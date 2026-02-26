package com.firstapp.dormease.utils

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
        private const val KEY_ROLE         = "user_role"
    }

    // ─── Token (unchanged) ────────────────────────────────────────────────────

    fun saveAuthToken(token: String) {
        val editor = sharedPreferences.edit()
        editor.putString(USER_TOKEN, token)
        val expirationTime = Date(System.currentTimeMillis() + 60 * 60 * 1000) // 1 hour
        editor.putLong(TOKEN_EXPIRATION, expirationTime.time)
        editor.apply()
    }

    fun fetchAuthToken(): String? {
        return sharedPreferences.getString(USER_TOKEN, null)
    }

    fun isTokenExpired(): Boolean {
        val expirationTime = sharedPreferences.getLong(TOKEN_EXPIRATION, 0)
        return Date().after(Date(expirationTime))
    }

    fun clearAuthToken() {
        val editor = sharedPreferences.edit()
        editor.remove(USER_TOKEN)
        editor.remove(TOKEN_EXPIRATION)
        editor.apply()
    }

    // ─── User credentials ─────────────────────────────────────────────────────

    /**
     * Call this right after a successful login/signup.
     * Pass the token here too so everything is saved in one shot.
     */
    fun saveUserSession(
        token: String,
        name: String,
        email: String,
        username: String,
        role: String = "Tenant"
    ) {
        saveAuthToken(token) // reuses existing token logic (sets expiry too)
        sharedPreferences.edit().apply {
            putString(KEY_NAME,     name)
            putString(KEY_EMAIL,    email)
            putString(KEY_USERNAME, username)
            putString(KEY_ROLE,     role)
            apply()
        }
    }

    fun getName(): String     = sharedPreferences.getString(KEY_NAME,     "User")   ?: "User"
    fun getEmail(): String    = sharedPreferences.getString(KEY_EMAIL,    "—")      ?: "—"
    fun getUsername(): String = sharedPreferences.getString(KEY_USERNAME, "—")      ?: "—"
    fun getRole(): String     = sharedPreferences.getString(KEY_ROLE,     "Tenant") ?: "Tenant"

    /** Wipes token AND user info — use this on log out */
    fun clearSession() {
        sharedPreferences.edit().clear().apply()
    }
}