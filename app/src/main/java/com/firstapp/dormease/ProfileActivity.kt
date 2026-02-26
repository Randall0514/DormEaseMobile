package com.firstapp.dormease

import android.content.Intent
import android.os.Bundle
import android.widget.LinearLayout
import android.widget.TextView
import androidx.appcompat.app.AlertDialog
import androidx.appcompat.app.AppCompatActivity
import com.firstapp.dormease.utils.SessionManager

class ProfileActivity : AppCompatActivity() {

    private lateinit var session: SessionManager

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_profile)
        supportActionBar?.hide()

        session = SessionManager(this)

        loadUserProfile()
        setupClickListeners()
    }

    private fun loadUserProfile() {
        val name     = session.getName()
        val email    = session.getEmail()
        val username = session.getUsername()
        val role     = session.getRole()

        // Avatar initial — first letter of name, uppercase
        val initial = name.firstOrNull()?.uppercaseChar()?.toString() ?: "?"

        findViewById<TextView>(R.id.tvAvatarInitial).text  = initial
        findViewById<TextView>(R.id.tvProfileName).text    = name
        findViewById<TextView>(R.id.tvProfileEmail).text   = email
        findViewById<TextView>(R.id.tvProfileUsername).text = username
        findViewById<TextView>(R.id.tvProfileRole).text    = role
    }

    private fun setupClickListeners() {
        // Bottom Navigation
        findViewById<LinearLayout>(R.id.navHome).setOnClickListener {
            startActivity(Intent(this, DashboardActivity::class.java))
            finish()
        }

        findViewById<LinearLayout>(R.id.navSettings).setOnClickListener {
            startActivity(Intent(this, SettingsActivity::class.java))
            finish()
        }

        findViewById<LinearLayout>(R.id.navNotifications).setOnClickListener {
            startActivity(Intent(this, NotificationsActivity::class.java))
            finish()
        }

        // Menu Cards
        findViewById<androidx.cardview.widget.CardView>(R.id.btnPersonalInfo).setOnClickListener {
            // TODO: navigate to PersonalInfoActivity
        }

        findViewById<androidx.cardview.widget.CardView>(R.id.btnLoginSecurity).setOnClickListener {
            // TODO: navigate to LoginSecurityActivity
        }

        findViewById<androidx.cardview.widget.CardView>(R.id.btnPrivacy).setOnClickListener {
            // TODO: navigate to PrivacyActivity
        }

        findViewById<androidx.cardview.widget.CardView>(R.id.btnPayment).setOnClickListener {
            // TODO: navigate to PaymentActivity
        }

        // Log Out
        findViewById<androidx.cardview.widget.CardView>(R.id.btnLogout).setOnClickListener {
            AlertDialog.Builder(this)
                .setTitle("Log Out")
                .setMessage("Are you sure you want to log out?")
                .setPositiveButton("Log Out") { _, _ ->
                    session.clearSession()
                    val intent = Intent(this, MainActivity::class.java)
                    intent.flags = Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TASK
                    startActivity(intent)
                }
                .setNegativeButton("Cancel", null)
                .show()
        }
    }
}