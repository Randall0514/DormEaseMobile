package com.firstapp.dormease.activity

// FILE PATH: app/src/main/java/com/firstapp/dormease/activity/HelpCenterActivity.kt

import android.os.Bundle
import android.view.View
import android.widget.ImageView
import android.widget.LinearLayout
import android.widget.TextView
import androidx.appcompat.app.AppCompatActivity
import androidx.cardview.widget.CardView
import com.firstapp.dormease.R

class HelpCenterActivity : AppCompatActivity() {

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_help_center)
        supportActionBar?.hide()

        findViewById<ImageView>(R.id.btnBack).setOnClickListener { finish() }

        // Expandable FAQ cards
        setupFaq(
            R.id.faqCard1, R.id.faqQuestion1, R.id.faqAnswer1,
            "How do I make a reservation?",
            "Browse available dorms on the Home screen, tap a dorm to view details, then tap \"Reserve\" and fill in your information."
        )
        setupFaq(
            R.id.faqCard2, R.id.faqQuestion2, R.id.faqAnswer2,
            "How do I cancel my reservation?",
            "Go to your Tenant Dashboard, find your active reservation, and tap \"Cancel Reservation\". You will be asked to provide a reason."
        )
        setupFaq(
            R.id.faqCard3, R.id.faqQuestion3, R.id.faqAnswer3,
            "Why is my reservation still pending?",
            "Reservations are reviewed by the dorm owner. Pending means your request has been received and is awaiting approval. You will be notified once a decision is made."
        )
        setupFaq(
            R.id.faqCard4, R.id.faqQuestion4, R.id.faqAnswer4,
            "How do I contact my dorm owner?",
            "Open the Messages tab at the bottom of the screen. Your dorm owner will appear in your contacts once a reservation is active."
        )
        setupFaq(
            R.id.faqCard5, R.id.faqQuestion5, R.id.faqAnswer5,
            "How do I change my account details?",
            "Go to Settings → Change Password or Change Email. For profile updates, go to Profile → Edit Profile."
        )
    }

    private fun setupFaq(
        cardId: Int,
        questionId: Int,
        answerId: Int,
        question: String,
        answer: String
    ) {
        val card     = findViewById<CardView>(cardId)
        val tvQ      = findViewById<TextView>(questionId)
        val tvA      = findViewById<TextView>(answerId)

        tvQ.text = question
        tvA.text = answer

        card.setOnClickListener {
            tvA.visibility = if (tvA.visibility == View.VISIBLE) View.GONE else View.VISIBLE
        }
    }
}