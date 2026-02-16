package com.firstapp.dormease

import android.content.Intent
import android.os.Bundle
import android.widget.LinearLayout
import androidx.appcompat.app.AppCompatActivity
import androidx.recyclerview.widget.LinearLayoutManager
import androidx.recyclerview.widget.RecyclerView
import com.firstapp.dormease.adapter.DormAdapter
import com.firstapp.dormease.model.Dorm

class DashboardActivity : AppCompatActivity() {

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_dashboard)

        supportActionBar?.hide()

        val rvDorms = findViewById<RecyclerView>(R.id.rvDorms)
        rvDorms.layoutManager = LinearLayoutManager(this)

        val dorms = listOf(
            Dorm(
                id = "1",
                name = "Salvador's Dorm",
                ownerName = "Randall Salvador",
                phoneNumber = "+63 9123456789",
                location = "Dagupan City, Pangasinan",
                price = 3500.0,
                deposit = 3500.0,
                advance = 3500.0,
                utilities = listOf("Water", "Electricity", "Gas"),
                imageUrls = listOf("")
            )
        )

        rvDorms.adapter = DormAdapter(dorms)

        // Bottom Navigation
        findViewById<LinearLayout>(R.id.navSettings).setOnClickListener {
            startActivity(Intent(this, SettingsActivity::class.java))
        }

        findViewById<LinearLayout>(R.id.navNotifications).setOnClickListener {
            startActivity(Intent(this, NotificationsActivity::class.java))
        }

        findViewById<LinearLayout>(R.id.navProfile).setOnClickListener {
            startActivity(Intent(this, ProfileActivity::class.java))
        }
    }
}