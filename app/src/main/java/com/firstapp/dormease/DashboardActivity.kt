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

    private val badgeRunnable = object : Runnable {
        override fun run() {
            fetchNotificationCount()
            handler.postDelayed(this, 15_000L)
        }
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_dashboard)
        supportActionBar?.hide()

        sessionManager      = SessionManager(this)
        tvNotificationBadge = findViewById(R.id.tvNotificationBadge)

        rvDorms = findViewById(R.id.rvDorms)
        rvDorms.layoutManager = LinearLayoutManager(this)
        dormAdapter = DormAdapter(emptyList())
        rvDorms.adapter = dormAdapter

        fetchAvailableDorms()

        findViewById<FrameLayout>(R.id.notificationBellContainer).setOnClickListener {
            startActivity(Intent(this, NotificationsActivity::class.java))
        }
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
        handler.post(badgeRunnable)
        // Refresh dorm list so occupied counts are up to date when returning
        fetchAvailableDorms()
    }

    override fun onPause() {
        super.onPause()
        handler.removeCallbacks(badgeRunnable)
    }

    override fun onDestroy() {
        scope.cancel()
        super.onDestroy()
    }

    private fun fetchNotificationCount() {
        val rawPhone = sessionManager.getPhone().trim()
        if (rawPhone.isBlank()) { updateBadge(0); return }

        val digits     = rawPhone.filter { it.isDigit() }.takeLast(10)
        val phoneParam = "+63$digits"

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

    // occupied_count is now returned directly by GET /dorms/available from the server
    private fun fetchAvailableDorms() {
        RetrofitClient.getApiService(this).getAvailableDorms()
            .enqueue(object : Callback<List<Dorm>> {
                override fun onResponse(call: Call<List<Dorm>>, response: Response<List<Dorm>>) {
                    if (response.isSuccessful) {
                        val dorms = response.body() ?: emptyList()
                        Log.d("DashboardActivity", "Fetched ${dorms.size} dorms")
                        dormAdapter = DormAdapter(dorms)
                        rvDorms.adapter = dormAdapter
                    } else {
                        Toast.makeText(
                            this@DashboardActivity,
                            "Failed to load dorms: ${response.message()}",
                            Toast.LENGTH_SHORT
                        ).show()
                    }
                }

                override fun onFailure(call: Call<List<Dorm>>, t: Throwable) {
                    Log.e("DashboardActivity", "Network error", t)
                    Toast.makeText(
                        this@DashboardActivity,
                        "Network error: ${t.message}",
                        Toast.LENGTH_SHORT
                    ).show()
                }
            })
    }
}