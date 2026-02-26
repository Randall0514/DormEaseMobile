package com.firstapp.dormease

import android.content.Intent
import android.os.Bundle
import android.util.Log
import android.widget.LinearLayout
import android.widget.Toast
import androidx.appcompat.app.AppCompatActivity
import androidx.recyclerview.widget.LinearLayoutManager
import androidx.recyclerview.widget.RecyclerView
import com.firstapp.dormease.adapter.DormAdapter
import com.firstapp.dormease.model.Dorm
import com.firstapp.dormease.network.RetrofitClient
import retrofit2.Call
import retrofit2.Callback
import retrofit2.Response

class DashboardActivity : AppCompatActivity() {

    private lateinit var rvDorms: RecyclerView
    private lateinit var dormAdapter: DormAdapter

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_dashboard)

        supportActionBar?.hide()

        // Initialize RecyclerView
        rvDorms = findViewById(R.id.rvDorms)
        rvDorms.layoutManager = LinearLayoutManager(this)

        // Initialize adapter with empty list
        dormAdapter = DormAdapter(emptyList())
        rvDorms.adapter = dormAdapter

        // Fetch dorms from API
        fetchAvailableDorms()

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

    private fun fetchAvailableDorms() {
        val apiService = RetrofitClient.getApiService(this)

        apiService.getAvailableDorms().enqueue(object : Callback<List<Dorm>> {
            override fun onResponse(call: Call<List<Dorm>>, response: Response<List<Dorm>>) {
                if (response.isSuccessful) {
                    val dorms = response.body()
                    if (dorms != null) {
                        Log.d("DashboardActivity", "Fetched ${dorms.size} dorms")
                        dormAdapter = DormAdapter(dorms)
                        rvDorms.adapter = dormAdapter
                    } else {
                        Log.w("DashboardActivity", "Response body is null")
                        Toast.makeText(
                            this@DashboardActivity,
                            "No dorms available",
                            Toast.LENGTH_SHORT
                        ).show()
                    }
                } else {
                    Log.e("DashboardActivity", "Error: ${response.code()} - ${response.message()}")
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