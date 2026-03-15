package com.firstapp.dormease.activity

import android.os.Bundle
import android.widget.ImageView
import android.widget.TextView
import androidx.appcompat.app.AppCompatActivity
import com.firstapp.dormease.R
import com.firstapp.dormease.utils.SessionManager

class PersonalInfoActivity : AppCompatActivity() {

    private lateinit var session: SessionManager

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_personal_info)
        supportActionBar?.hide()

        session = SessionManager(this)

        findViewById<ImageView>(R.id.btnBack).setOnClickListener { finish() }

        loadInfo()
    }

    private fun loadInfo() {
        val name     = session.getName()
        val username = session.getUsername()
        val email    = session.getEmail()
        val phone    = session.getPhone().ifBlank { "—" }

        val initial = name.firstOrNull()?.uppercaseChar()?.toString() ?: "?"

        findViewById<TextView>(R.id.tvAvatarInitial).text = initial
        findViewById<TextView>(R.id.tvFullName).text      = name
        findViewById<TextView>(R.id.tvInfoName).text      = name
        findViewById<TextView>(R.id.tvInfoUsername).text  = username
        findViewById<TextView>(R.id.tvInfoEmail).text     = email
        findViewById<TextView>(R.id.tvInfoPhone).text     = phone
    }
}