package com.firstapp.dormease

import android.content.Intent
import androidx.appcompat.app.AppCompatActivity
import android.os.Bundle
import com.firstapp.dormease.utils.SessionManager

class MainActivity : AppCompatActivity() {

    private lateinit var sessionManager: SessionManager

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_main)

        sessionManager = SessionManager(this)

        if (sessionManager.fetchAuthToken() != null && !sessionManager.isTokenExpired()) {
            // User is logged in
            val intent = Intent(this, DashboardActivity::class.java)
            startActivity(intent)
        } else {
            // User is not logged in
            val intent = Intent(this, LoginActivity::class.java)
            startActivity(intent)
        }

        finish()
    }
}