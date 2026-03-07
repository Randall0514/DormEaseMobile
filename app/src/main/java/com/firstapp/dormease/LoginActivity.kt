package com.firstapp.dormease

// FILE PATH: app/src/main/java/com/firstapp/dormease/LoginActivity.kt

import android.content.Context
import android.content.Intent
import android.os.Bundle
import android.util.Log
import android.widget.*
import androidx.appcompat.app.AppCompatActivity
import com.firstapp.dormease.activity.TenantDashboardActivity
import com.firstapp.dormease.model.LoginRequest
import com.firstapp.dormease.model.ApiResponse
import com.firstapp.dormease.network.RetrofitClient
import com.firstapp.dormease.utils.SessionManager
import com.google.android.material.textfield.TextInputEditText
import com.google.android.material.textfield.TextInputLayout
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import retrofit2.Call
import retrofit2.Callback
import retrofit2.Response

class LoginActivity : AppCompatActivity() {

    private lateinit var sessionManager: SessionManager
    private lateinit var btnLogin: Button
    private val scope = CoroutineScope(Dispatchers.IO + SupervisorJob())

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_login)

        sessionManager = SessionManager(this)
        supportActionBar?.hide()

        if (sessionManager.isLoggedIn()) {
            routeAfterLogin()
            return
        }

        val tilUsername = findViewById<TextInputLayout>(R.id.tilUsername)
        val tilPassword = findViewById<TextInputLayout>(R.id.tilPassword)
        val etUsername  = findViewById<TextInputEditText>(R.id.etUsername)
        val etPassword  = findViewById<TextInputEditText>(R.id.etPassword)
        btnLogin        = findViewById(R.id.btnLogin)
        val tvSignup    = findViewById<TextView>(R.id.tvSignup)

        tilPassword.setErrorIconDrawable(null)

        etUsername.setOnFocusChangeListener { _, hasFocus ->
            if (hasFocus) { tilUsername.error = null; tilUsername.isErrorEnabled = false }
        }
        etPassword.setOnFocusChangeListener { _, hasFocus ->
            if (hasFocus) { tilPassword.error = null; tilPassword.isErrorEnabled = false }
        }

        btnLogin.setOnClickListener {
            val identifier = etUsername.text.toString().trim()
            val password   = etPassword.text.toString().trim()

            tilUsername.error = null
            tilPassword.error = null
            tilUsername.isErrorEnabled = false
            tilPassword.isErrorEnabled = false

            var hasError = false
            if (identifier.isEmpty()) {
                tilUsername.isErrorEnabled = true
                tilUsername.error = "Username or email is required"
                etUsername.requestFocus()
                hasError = true
            }
            if (password.isEmpty()) {
                tilPassword.isErrorEnabled = true
                tilPassword.error = "Password is required"
                if (!hasError) etPassword.requestFocus()
                return@setOnClickListener
            }
            if (hasError) return@setOnClickListener

            btnLogin.isEnabled = false
            btnLogin.text      = "Logging in..."

            val request = LoginRequest(
                identifier = identifier,
                password   = password,
                platform   = "mobile"
            )

            RetrofitClient.getApiService(this).login(request)
                .enqueue(object : Callback<ApiResponse> {
                    override fun onResponse(call: Call<ApiResponse>, response: Response<ApiResponse>) {
                        if (response.isSuccessful) {
                            val body = response.body()

                            // Restore phone saved from a previous reservation
                            val savedPhone = getSharedPreferences(
                                "NotificationState", Context.MODE_PRIVATE
                            ).getString("last_phone", "") ?: ""

                            sessionManager.saveUserSession(
                                token    = body?.token          ?: "",
                                name     = body?.user?.fullName ?: identifier,
                                email    = body?.user?.email    ?: "",
                                username = body?.user?.username ?: identifier,
                                // NOTE: saveUserSession resets the terminated flag,
                                // so a fresh login always re-checks the server.
                                phone    = if (savedPhone.isNotBlank()) "+63$savedPhone" else "",
                                role     = "Tenant",
                                userId   = body?.user?.id       ?: -1
                            )

                            Log.d("LoginActivity", "Login OK — checking reservation to route...")
                            Toast.makeText(this@LoginActivity, "Login successful!", Toast.LENGTH_SHORT).show()
                            routeAfterLogin()

                        } else {
                            btnLogin.isEnabled = true
                            btnLogin.text      = "Login"
                            tilPassword.isErrorEnabled = true
                            tilPassword.error  = "Invalid username or password"
                            Toast.makeText(
                                this@LoginActivity,
                                "Login failed: ${response.errorBody()?.string()}",
                                Toast.LENGTH_LONG
                            ).show()
                        }
                    }

                    override fun onFailure(call: Call<ApiResponse>, t: Throwable) {
                        btnLogin.isEnabled = true
                        btnLogin.text      = "Login"
                        Toast.makeText(this@LoginActivity, "Network error: ${t.message}", Toast.LENGTH_LONG).show()
                    }
                })
        }

        tvSignup.setOnClickListener {
            startActivity(Intent(this, SignupActivity::class.java))
        }
    }

    override fun onDestroy() {
        scope.cancel()
        super.onDestroy()
    }

    private fun routeAfterLogin() {
        // ── Terminated flag is reset by saveUserSession on fresh login,
        //    so this only fires if somehow we reach routeAfterLogin while
        //    already marked terminated (e.g. isLoggedIn() path at top).
        if (sessionManager.isTerminated()) {
            Log.d("LoginActivity", "isTerminated=true → DashboardActivity")
            goToDashboard()
            return
        }

        val rawPhone = sessionManager.getPhone().trim()
        val userId   = sessionManager.getUserId()

        Log.d("LoginActivity", "routeAfterLogin: phone='$rawPhone' userId=$userId")

        when {
            rawPhone.isNotBlank() -> checkReservationAndRoute(phoneParam = "+63${rawPhone.filter { it.isDigit() }.takeLast(10)}", byPhone = true)
            userId > 0            -> checkReservationAndRoute(phoneParam = null, byPhone = false)
            else                  -> goToDashboard()
        }
    }

    private fun checkReservationAndRoute(phoneParam: String?, byPhone: Boolean) {
        scope.launch {
            try {
                val api = RetrofitClient.getApiService(applicationContext)

                val reservations = if (byPhone && phoneParam != null) {
                    val resp = api.getTenantReservations(phoneParam)
                    if (resp.isSuccessful) resp.body() ?: emptyList() else emptyList()
                } else {
                    val resp = api.getMyReservations()
                    if (resp.isSuccessful) {
                        val list = resp.body() ?: emptyList()
                        list.firstOrNull { it.phone.isNotBlank() }?.phone?.let { serverPhone ->
                            val digits = serverPhone.filter { it.isDigit() }.takeLast(10)
                            if (digits.isNotBlank()) {
                                sessionManager.savePhone("+63$digits")
                                getSharedPreferences("NotificationState", Context.MODE_PRIVATE)
                                    .edit().putString("last_phone", digits).apply()
                            }
                        }
                        list
                    } else emptyList()
                }

                Log.d("LoginActivity", "Got ${reservations.size} reservations")
                reservations.forEach {
                    Log.d("LoginActivity", "  id=${it.id} status=${it.status} tenant_action=${it.tenant_action}")
                }

                // ── Termination check ─────────────────────────────────────────
                val isTerminated = reservations.isNotEmpty() &&
                        reservations.none { it.status == "approved" || it.status == "pending" } &&
                        reservations.any  { it.status == "archived" }

                if (isTerminated) {
                    Log.d("LoginActivity", "Terminated — calling markTerminated()")
                    sessionManager.markTerminated()
                }

                // Only approved or pending = active
                val hasApprovedOrPending = reservations.any {
                    it.status == "approved" || it.status == "pending"
                }

                withContext(Dispatchers.Main) {
                    if (hasApprovedOrPending) {
                        Log.d("LoginActivity", "→ TenantDashboardActivity")
                        goToTenantDashboard()
                    } else {
                        Log.d("LoginActivity", "→ DashboardActivity")
                        goToDashboard()
                    }
                }

            } catch (e: Exception) {
                Log.e("LoginActivity", "checkReservationAndRoute error: ${e.message}")
                withContext(Dispatchers.Main) {
                    if (sessionManager.isTerminated()) {
                        goToDashboard()
                    } else if (sessionManager.getPhone().isNotBlank()) {
                        goToTenantDashboard()
                    } else {
                        goToDashboard()
                    }
                }
            }
        }
    }

    private fun goToTenantDashboard() {
        startActivity(
            Intent(this, TenantDashboardActivity::class.java).apply {
                flags = Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TASK
            }
        )
        finish()
    }

    private fun goToDashboard() {
        startActivity(
            Intent(this, DashboardActivity::class.java).apply {
                flags = Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TASK
            }
        )
        finish()
    }
}