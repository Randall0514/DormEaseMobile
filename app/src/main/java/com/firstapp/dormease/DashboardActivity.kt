package com.firstapp.dormease

import android.content.Intent
import android.os.Bundle
import android.os.Handler
import android.os.Looper
import android.util.Log
import android.view.View
import android.widget.FrameLayout
import android.widget.LinearLayout
import android.widget.TextView
import android.widget.Toast
import androidx.appcompat.app.AppCompatActivity
import androidx.recyclerview.widget.LinearLayoutManager
import androidx.recyclerview.widget.RecyclerView
import com.firstapp.dormease.adapter.DormAdapter
import com.firstapp.dormease.model.Dorm
import com.firstapp.dormease.model.TenantReservation
import com.firstapp.dormease.network.RetrofitClient
import com.firstapp.dormease.utils.SessionManager
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import retrofit2.Call
import retrofit2.Callback
import retrofit2.Response

class DashboardActivity : AppCompatActivity() {

    private lateinit var rvDorms: RecyclerView
    private lateinit var dormAdapter: DormAdapter
    private lateinit var tvNotificationBadge: TextView
    private lateinit var sessionManager: SessionManager

    private val scope   = CoroutineScope(Dispatchers.IO + SupervisorJob())
    private val handler = Handler(Looper.getMainLooper())

    // Refresh badge count every 15 s while dashboard is visible
    private val badgeRunnable = object : Runnable {
        override fun run() {
            fetchNotificationCount()
            handler.postDelayed(this, 15_000L)
        }
    }

    // ─────────────────────────────────────────────────────────────────────────
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_dashboard)
        supportActionBar?.hide()

        sessionManager       = SessionManager(this)
        tvNotificationBadge  = findViewById(R.id.tvNotificationBadge)

        rvDorms = findViewById(R.id.rvDorms)
        rvDorms.layoutManager = LinearLayoutManager(this)
        dormAdapter = DormAdapter(emptyList())
        rvDorms.adapter = dormAdapter

        fetchAvailableDorms()

        // Bell → open notifications
        findViewById<FrameLayout>(R.id.notificationBellContainer).setOnClickListener {
            startActivity(Intent(this, NotificationsActivity::class.java))
        }

        // Bottom nav
        findViewById<LinearLayout>(R.id.navMessages).setOnClickListener {
            startActivity(Intent(this, MessagesActivity::class.java))
        }
        findViewById<LinearLayout>(R.id.navSettings).setOnClickListener {
            startActivity(Intent(this, SettingsActivity::class.java))
        }
        findViewById<LinearLayout>(R.id.navProfile).setOnClickListener {
            startActivity(Intent(this, ProfileActivity::class.java))
        }
    }

    override fun onResume() {
        super.onResume()
        // Refresh badge immediately when coming back from NotificationsActivity
        handler.post(badgeRunnable)
    }

    override fun onPause() {
        super.onPause()
        handler.removeCallbacks(badgeRunnable)
    }

    override fun onDestroy() {
        scope.cancel()
        super.onDestroy()
    }

    // ─────────────────────────────────────────────────────────────────────────
    // Fetch the tenant's reservations just to update the bell badge count.
    // Same endpoint NotificationsActivity uses — no background service needed.
    // ─────────────────────────────────────────────────────────────────────────
    private fun fetchNotificationCount() {
        val rawPhone = sessionManager.getPhone().trim()
        if (rawPhone.isBlank()) {
            updateBadge(0)
            return
        }

        val digits    = rawPhone.filter { it.isDigit() }
        val last10    = digits.takeLast(10)
        val phoneParam = "+63$last10"

        scope.launch {
            try {
                val api      = RetrofitClient.getApiService(applicationContext)
                val response = api.getTenantReservations(phoneParam)

                val count = if (response.isSuccessful) {
                    response.body()
                        ?.count { it.status == "approved" || it.status == "rejected" }
                        ?: 0
                } else 0

                withContext(Dispatchers.Main) { updateBadge(count) }

            } catch (e: Exception) {
                Log.w("DashboardActivity", "Badge fetch failed: ${e.message}")
            }
        }
    }

    private fun updateBadge(count: Int) {
        if (count > 0) {
            tvNotificationBadge.visibility = View.VISIBLE
            tvNotificationBadge.text       = if (count > 9) "9+" else count.toString()
        } else {
            tvNotificationBadge.visibility = View.GONE
        }
    }

    // ─────────────────────────────────────────────────────────────────────────
    private fun fetchAvailableDorms() {
        RetrofitClient.getApiService(this).getAvailableDorms()
            .enqueue(object : Callback<List<Dorm>> {
                override fun onResponse(call: Call<List<Dorm>>, response: Response<List<Dorm>>) {
                    if (response.isSuccessful) {
                        val dorms = response.body()
                        if (dorms != null) {
                            Log.d("DashboardActivity", "Fetched ${dorms.size} dorms")
                            dormAdapter = DormAdapter(dorms)
                            rvDorms.adapter = dormAdapter
                        } else {
                            Toast.makeText(this@DashboardActivity, "No dorms available", Toast.LENGTH_SHORT).show()
                        }
                    } else {
                        Toast.makeText(this@DashboardActivity, "Failed to load dorms: ${response.message()}", Toast.LENGTH_SHORT).show()
                    }
                }

                override fun onFailure(call: Call<List<Dorm>>, t: Throwable) {
                    Log.e("DashboardActivity", "Network error", t)
                    Toast.makeText(this@DashboardActivity, "Network error: ${t.message}", Toast.LENGTH_SHORT).show()
                }
            })
    }
}