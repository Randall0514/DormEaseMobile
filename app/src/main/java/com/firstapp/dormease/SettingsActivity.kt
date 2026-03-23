package com.firstapp.dormease

// FILE PATH: app/src/main/java/com/firstapp/dormease/SettingsActivity.kt

import android.content.Intent
import android.os.Bundle
import android.widget.FrameLayout
import android.widget.LinearLayout
import androidx.appcompat.app.AppCompatActivity
import androidx.cardview.widget.CardView
import com.firstapp.dormease.activity.AboutActivity
import com.firstapp.dormease.activity.ChangeEmailActivity
import com.firstapp.dormease.activity.ChangePasswordActivity
import com.firstapp.dormease.activity.ContactUsActivity
import com.firstapp.dormease.activity.HelpCenterActivity
import com.firstapp.dormease.utils.HomeRouter
import com.firstapp.dormease.utils.NavBadgeHelper
import com.firstapp.dormease.utils.SessionManager
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel

class SettingsActivity : AppCompatActivity() {

    private lateinit var session: SessionManager
    private val badgeHelper = NavBadgeHelper()
    private val scope = CoroutineScope(Dispatchers.IO + SupervisorJob())

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_settings)
        supportActionBar?.hide()

        session = SessionManager(this)

        // ── Account Section ───────────────────────────────────────────────────
        findViewById<CardView>(R.id.btnChangePassword).setOnClickListener {
            startActivity(Intent(this, ChangePasswordActivity::class.java))
        }
        findViewById<CardView>(R.id.btnChangeEmail).setOnClickListener {
            startActivity(Intent(this, ChangeEmailActivity::class.java))
        }

        // ── Support Section ───────────────────────────────────────────────────
        findViewById<CardView>(R.id.btnHelpCenter).setOnClickListener {
            startActivity(Intent(this, HelpCenterActivity::class.java))
        }
        findViewById<CardView>(R.id.btnContactUs).setOnClickListener {
            startActivity(Intent(this, ContactUsActivity::class.java))
        }
        findViewById<CardView>(R.id.btnAbout).setOnClickListener {
            startActivity(Intent(this, AboutActivity::class.java))
        }

        // ── Bottom Navigation ─────────────────────────────────────────────────
        findViewById<LinearLayout>(R.id.navHome).setOnClickListener {
            navigateHome()
        }
        // FIX: navMessages is now a FrameLayout in the XML
        findViewById<FrameLayout>(R.id.navMessages).setOnClickListener {
            startActivity(Intent(this, MessagesActivity::class.java))
            finish()
        }
        // navSettings is current page — no action
        findViewById<LinearLayout>(R.id.navProfile).setOnClickListener {
            startActivity(Intent(this, ProfileActivity::class.java))
            finish()
        }
    }

    override fun onResume() {
        super.onResume()
        badgeHelper.attach(this)
    }

    override fun onPause() {
        badgeHelper.detach()
        super.onPause()
    }

    override fun onDestroy() {
        scope.cancel()
        super.onDestroy()
    }

    private fun navigateHome() {
        HomeRouter.navigate(
            context = this,
            scope = scope,
            session = session,
            intentFlags = Intent.FLAG_ACTIVITY_REORDER_TO_FRONT
        )
    }
}