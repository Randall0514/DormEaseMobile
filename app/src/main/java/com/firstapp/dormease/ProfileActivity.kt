package com.firstapp.dormease

// FILE PATH: app/src/main/java/com/firstapp/dormease/ProfileActivity.kt

import android.content.Intent
import android.os.Bundle
import android.widget.LinearLayout
import android.widget.TextView
import androidx.appcompat.app.AlertDialog
import androidx.appcompat.app.AppCompatActivity
import com.firstapp.dormease.activity.TenantDashboardActivity
import com.firstapp.dormease.network.SocketManager
import com.firstapp.dormease.utils.SessionManager
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel

class ProfileActivity : AppCompatActivity() {

    private lateinit var session: SessionManager
    private val scope = CoroutineScope(Dispatchers.IO + SupervisorJob())

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_profile)
        supportActionBar?.hide()

        session = SessionManager(this)

        loadUserProfile()
        setupClickListeners()
    }

    override fun onResume() {
        super.onResume()
        loadUserProfile()
    }

    override fun onDestroy() {
        scope.cancel()
        super.onDestroy()
    }

    private fun loadUserProfile() {
        val name     = session.getName()
        val email    = session.getEmail()
        val username = session.getUsername()
        val role     = session.getRole()

        val initial = name.firstOrNull()?.uppercaseChar()?.toString() ?: "?"

        findViewById<TextView>(R.id.tvAvatarInitial).text   = initial
        findViewById<TextView>(R.id.tvProfileName).text     = name
        findViewById<TextView>(R.id.tvProfileEmail).text    = email
        findViewById<TextView>(R.id.tvProfileUsername).text = username
        findViewById<TextView>(R.id.tvProfileRole).text     = role
    }

    private fun setupClickListeners() {
        // ── Bottom Navigation ─────────────────────────────────────────────────
        findViewById<LinearLayout>(R.id.navHome).setOnClickListener {
            navigateHome()
        }
        findViewById<LinearLayout>(R.id.navMessages).setOnClickListener {
            startActivity(Intent(this, MessagesActivity::class.java))
            finish()
        }
        findViewById<LinearLayout>(R.id.navSettings).setOnClickListener {
            startActivity(Intent(this, SettingsActivity::class.java))
            finish()
        }
        // navProfile is current page — no action

        // ── Log Out ───────────────────────────────────────────────────────────
        findViewById<androidx.cardview.widget.CardView>(R.id.btnLogout).setOnClickListener {
            AlertDialog.Builder(this)
                .setTitle("Log Out")
                .setMessage("Are you sure you want to log out?")
                .setPositiveButton("Log Out") { _, _ ->
                    SocketManager.disconnect()
                    session.clearSession()
                    val intent = Intent(this, MainActivity::class.java)
                    intent.flags = Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TASK
                    startActivity(intent)
                }
                .setNegativeButton("Cancel", null)
                .show()
        }
    }

    private fun navigateHome() {
        val target = resolveHomeTarget()
        startActivity(Intent(this, target).apply {
            flags = Intent.FLAG_ACTIVITY_REORDER_TO_FRONT
        })
    }

    private fun resolveHomeTarget(): Class<*> {
        if (session.isTerminated()) return DashboardActivity::class.java

        val phone = session.getPhone().trim()
        if (phone.isNotBlank()) return TenantDashboardActivity::class.java

        val notifPrefs = getSharedPreferences("NotificationState", MODE_PRIVATE)
        val lastPhone  = (notifPrefs.getString("last_phone", "") ?: "").trim()
        if (lastPhone.isNotBlank()) {
            val digits = lastPhone.filter { it.isDigit() }.takeLast(10)
            if (digits.isNotBlank()) session.savePhone("+63$digits")
            return TenantDashboardActivity::class.java
        }

        return DashboardActivity::class.java
    }
}