package com.firstapp.dormease

// FILE PATH: app/src/main/java/com/firstapp/dormease/SettingsActivity.kt

import android.content.Intent
import android.os.Bundle
import android.widget.LinearLayout
import androidx.appcompat.app.AppCompatActivity
import androidx.cardview.widget.CardView
import com.firstapp.dormease.activity.AboutActivity
import com.firstapp.dormease.activity.ChangeEmailActivity
import com.firstapp.dormease.activity.ChangePasswordActivity
import com.firstapp.dormease.activity.ContactUsActivity
import com.firstapp.dormease.activity.HelpCenterActivity
import com.firstapp.dormease.activity.TenantDashboardActivity
import com.firstapp.dormease.utils.SessionManager
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel

class SettingsActivity : AppCompatActivity() {

    private lateinit var session: SessionManager
    private val scope = CoroutineScope(Dispatchers.IO + SupervisorJob())

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_settings)
        supportActionBar?.hide()

        session = SessionManager(this)

        // ── Account Section ───────────────────────────────────────────────────
        // Change Password
        findViewById<CardView>(R.id.btnChangePassword).setOnClickListener {
            startActivity(Intent(this, ChangePasswordActivity::class.java))
        }

        // Change Email
        findViewById<CardView>(R.id.btnChangeEmail).setOnClickListener {
            startActivity(Intent(this, ChangeEmailActivity::class.java))
        }

        // ── Support Section ───────────────────────────────────────────────────
        // Help Center
        findViewById<CardView>(R.id.btnHelpCenter).setOnClickListener {
            startActivity(Intent(this, HelpCenterActivity::class.java))
        }

        // Contact Us
        findViewById<CardView>(R.id.btnContactUs).setOnClickListener {
            startActivity(Intent(this, ContactUsActivity::class.java))
        }

        // About DormEase
        findViewById<CardView>(R.id.btnAbout).setOnClickListener {
            startActivity(Intent(this, AboutActivity::class.java))
        }

        // ── Bottom Navigation ─────────────────────────────────────────────────
        findViewById<LinearLayout>(R.id.navHome).setOnClickListener {
            navigateHome()
        }
        findViewById<LinearLayout>(R.id.navMessages).setOnClickListener {
            startActivity(Intent(this, MessagesActivity::class.java))
            finish()
        }
        // navSettings is current page — no action
        findViewById<LinearLayout>(R.id.navProfile).setOnClickListener {
            startActivity(Intent(this, ProfileActivity::class.java))
            finish()
        }
    }

    override fun onDestroy() {
        scope.cancel()
        super.onDestroy()
    }

    /**
     * Decide the correct Home screen without a network call.
     *   1. terminated flag set → DashboardActivity
     *   2. phone saved in SessionManager → TenantDashboardActivity
     *   3. last_phone saved in NotificationState → TenantDashboardActivity
     *   4. otherwise → DashboardActivity
     */
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