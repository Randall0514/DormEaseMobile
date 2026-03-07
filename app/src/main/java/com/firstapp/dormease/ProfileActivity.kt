package com.firstapp.dormease

// FILE PATH: app/src/main/java/com/firstapp/dormease/ProfileActivity.kt

import android.content.Intent
import android.os.Bundle
import android.widget.LinearLayout
import android.widget.TextView
import androidx.appcompat.app.AlertDialog
import androidx.appcompat.app.AppCompatActivity
import com.firstapp.dormease.network.SocketManager
import com.firstapp.dormease.utils.HomeRouter
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
        // Bottom Navigation
        findViewById<LinearLayout>(R.id.navHome).setOnClickListener {
            HomeRouter.navigate(this, scope, session)
        }

        findViewById<LinearLayout>(R.id.navMessages).setOnClickListener {
            startActivity(Intent(this, MessagesActivity::class.java))
            finish()
        }

        findViewById<LinearLayout>(R.id.navSettings).setOnClickListener {
            startActivity(Intent(this, SettingsActivity::class.java))
            finish()
        }

        // navProfile is the current page — no action needed

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
}