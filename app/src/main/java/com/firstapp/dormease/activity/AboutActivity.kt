package com.firstapp.dormease.activity

// FILE PATH: app/src/main/java/com/firstapp/dormease/activity/AboutActivity.kt

import android.os.Bundle
import android.widget.ImageView
import androidx.appcompat.app.AppCompatActivity
import com.firstapp.dormease.R

class AboutActivity : AppCompatActivity() {

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_about)
        supportActionBar?.hide()

        findViewById<ImageView>(R.id.btnBack).setOnClickListener { finish() }
    }
}