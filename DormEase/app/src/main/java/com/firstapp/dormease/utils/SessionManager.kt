package com.firstapp.dormease.utils

import android.content.Context
import android.content.SharedPreferences
import java.util.Date

class SessionManager(context: Context) {

    private val sharedPreferences: SharedPreferences =
        context.getSharedPreferences("DormEaseSession", Context.MODE_PRIVATE)

    companion object {
        private const val USER_TOKEN = "user_token"
        private const val TOKEN_EXPIRATION = "token_expiration"
    }

    fun saveAuthToken(token: String) {
        val editor = sharedPreferences.edit()
        editor.putString(USER_TOKEN, token)
        val expirationTime = Date(System.currentTimeMillis() + 5 * 60 * 1000) // 5 minutes
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
}