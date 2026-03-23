package com.firstapp.dormease

// FILE PATH: app/src/main/java/com/firstapp/dormease/LoginActivity.kt

import android.content.Context
import android.content.Intent
import android.os.Bundle
import android.util.Log
import android.util.Patterns
import android.widget.*
import androidx.appcompat.app.AlertDialog
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

                            val backendPhoneDigits = body?.user?.phoneNumber
                                ?.filter { it.isDigit() }
                                ?.takeLast(10)
                                .orEmpty()
                            val savedPhoneDigits = getSharedPreferences(
                                "NotificationState", Context.MODE_PRIVATE
                            ).getString("last_phone", "")
                                ?.filter { it.isDigit() }
                                ?.takeLast(10)
                                .orEmpty()
                            val resolvedPhoneDigits = if (backendPhoneDigits.isNotBlank()) {
                                backendPhoneDigits
                            } else {
                                savedPhoneDigits
                            }

                            sessionManager.saveUserSession(
                                token    = body?.token          ?: "",
                                name     = body?.user?.fullName ?: identifier,
                                email    = body?.user?.email    ?: "",
                                username = body?.user?.username ?: identifier,
                                // NOTE: saveUserSession resets the terminated flag,
                                // so a fresh login always re-checks the server.
                                phone    = if (resolvedPhoneDigits.isNotBlank()) "+63$resolvedPhoneDigits" else "",
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

        val tvForgotPassword = findViewById<TextView>(R.id.tvForgotPassword)
        tvForgotPassword?.setOnClickListener { showForgotPasswordStep1() }
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

        val userId = sessionManager.getUserId()
        Log.d("LoginActivity", "routeAfterLogin: userId=$userId")
        checkReservationAndRoute()
    }

    private fun checkReservationAndRoute() {
        scope.launch {
            try {
                val api = RetrofitClient.getApiService(applicationContext)

                val resp = api.getMyReservations()
                val reservations = if (resp.isSuccessful) {
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
                } else {
                    emptyList()
                }

                Log.d("LoginActivity", "Got ${reservations.size} reservations")
                reservations.forEach {
                    Log.d("LoginActivity", "  id=${it.id} status=${it.status} tenant_action=${it.tenant_action}")
                }

                val hasActiveReservation = reservations.any {
                    (it.status == "approved" || it.status == "pending") && it.tenant_action != "cancelled"
                }

                // ── Termination check ─────────────────────────────────────────
                val isTerminated = reservations.isNotEmpty() &&
                        !hasActiveReservation &&
                        reservations.any  { it.status == "archived" }

                val archivedReservation = reservations.firstOrNull { it.status == "archived" }

                if (isTerminated) {
                    Log.d("LoginActivity", "Terminated — calling markTerminated()")
                    sessionManager.markTerminated(
                        archivedReservation?.dorm_name,
                        archivedReservation?.termination_reason
                    )
                }

                // Only approved or pending = active
                val hasApprovedOrPending = hasActiveReservation

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

    private fun showForgotPasswordStep1() {
        val dialogView = layoutInflater.inflate(R.layout.dialog_forgot_password_email, null)
        val etEmail = dialogView.findViewById<EditText>(R.id.etDialogEmail)
        val tvError = dialogView.findViewById<TextView>(R.id.tvDialogEmailError)
        val btnCancel = dialogView.findViewById<Button>(R.id.btnDialogEmailCancel)
        val btnSend = dialogView.findViewById<Button>(R.id.btnDialogSendOtp)

        val dialog = AlertDialog.Builder(this)
            .setView(dialogView)
            .setCancelable(true)
            .create()

        btnCancel.setOnClickListener { dialog.dismiss() }
        btnSend.setOnClickListener {
            val email = etEmail.text.toString().trim()
            val errorMessage = when {
                email.isBlank() -> "Please enter your email"
                !Patterns.EMAIL_ADDRESS.matcher(email).matches() -> "Please enter a valid email"
                else -> null
            }

            if (errorMessage != null) {
                tvError.text = errorMessage
                tvError.visibility = android.view.View.VISIBLE
                return@setOnClickListener
            }

            tvError.visibility = android.view.View.GONE
            btnSend.isEnabled = false
            btnSend.text = "Sending..."
            dialog.dismiss()
            sendForgotPasswordOtp(email)
        }

        dialog.setOnShowListener {
            dialog.window?.setBackgroundDrawableResource(android.R.color.transparent)
        }
        dialog.show()
    }

    private fun sendForgotPasswordOtp(email: String) {
        scope.launch {
            try {
                val api = RetrofitClient.getApiService(applicationContext)
                val response = api.requestPasswordReset(mapOf("email" to email))

                withContext(Dispatchers.Main) {
                    if (response.isSuccessful) {
                        Toast.makeText(this@LoginActivity, "OTP sent! Check your email.", Toast.LENGTH_SHORT).show()
                        showForgotPasswordStep2(email)
                    } else {
                        val msg = response.errorBody()?.string()?.let {
                            runCatching { org.json.JSONObject(it).getString("message") }.getOrNull()
                        } ?: "Failed to send OTP"
                        Toast.makeText(this@LoginActivity, msg, Toast.LENGTH_LONG).show()
                    }
                }
            } catch (e: Exception) {
                withContext(Dispatchers.Main) {
                    Toast.makeText(this@LoginActivity, "Network error: ${e.message}", Toast.LENGTH_LONG).show()
                }
            }
        }
    }

    private fun showForgotPasswordStep2(email: String) {
        val dialogView = layoutInflater.inflate(R.layout.dialog_forgot_password_otp, null)
        val tvSubtitle = dialogView.findViewById<TextView>(R.id.tvDialogOtpSubtitle)
        val etOtp = dialogView.findViewById<EditText>(R.id.etDialogOtpCode)
        val tvError = dialogView.findViewById<TextView>(R.id.tvDialogOtpError)
        val tvResend = dialogView.findViewById<TextView>(R.id.tvDialogResendCode)
        val btnCancel = dialogView.findViewById<Button>(R.id.btnDialogOtpCancel)
        val btnVerify = dialogView.findViewById<Button>(R.id.btnDialogVerifyOtp)

        tvSubtitle.text = "A 6-digit code was sent to $email."

        val dialog = AlertDialog.Builder(this)
            .setView(dialogView)
            .setCancelable(true)
            .create()

        btnCancel.setOnClickListener { dialog.dismiss() }
        tvResend.setOnClickListener {
            dialog.dismiss()
            sendForgotPasswordOtp(email)
        }
        btnVerify.setOnClickListener {
            val otp = etOtp.text.toString().trim()
            val errorMessage = when {
                otp.length != 6 -> "OTP must be 6 digits"
                !otp.all { it.isDigit() } -> "OTP must contain numbers only"
                else -> null
            }

            if (errorMessage != null) {
                tvError.text = errorMessage
                tvError.visibility = android.view.View.VISIBLE
                return@setOnClickListener
            }

            tvError.visibility = android.view.View.GONE
            btnVerify.isEnabled = false
            btnVerify.text = "Verifying..."
            dialog.dismiss()
            showForgotPasswordStep3(email, otp)
        }

        dialog.setOnShowListener {
            dialog.window?.setBackgroundDrawableResource(android.R.color.transparent)
        }
        dialog.show()
    }

    private fun showForgotPasswordStep3(email: String, otp: String) {
        val dialogView = layoutInflater.inflate(R.layout.dialog_set_new_password, null)
        val tvSubtitle = dialogView.findViewById<TextView>(R.id.tvDialogSubtitle)
        val etNew = dialogView.findViewById<EditText>(R.id.etDialogNewPassword)
        val etConfirm = dialogView.findViewById<EditText>(R.id.etDialogConfirmPassword)
        val tvError = dialogView.findViewById<TextView>(R.id.tvDialogPasswordError)
        val btnCancel = dialogView.findViewById<Button>(R.id.btnDialogCancel)
        val btnReset = dialogView.findViewById<Button>(R.id.btnDialogResetPassword)

        tvSubtitle.text = "Choose a new password for $email."

        val dialog = AlertDialog.Builder(this)
            .setView(dialogView)
            .setCancelable(true)
            .create()

        btnCancel.setOnClickListener { dialog.dismiss() }
        btnReset.setOnClickListener {
            val newPwd = etNew.text.toString().trim()
            val confirm = etConfirm.text.toString().trim()

            val errorMessage = when {
                newPwd.length < 6 -> "Password must be at least 6 characters"
                newPwd != confirm -> "Passwords do not match"
                else -> null
            }

            if (errorMessage != null) {
                tvError.text = errorMessage
                tvError.visibility = android.view.View.VISIBLE
                return@setOnClickListener
            }

            tvError.visibility = android.view.View.GONE
            btnReset.isEnabled = false
            btnReset.text = "Resetting..."
            dialog.dismiss()
            doResetPassword(email, otp, newPwd)
        }

        dialog.setOnShowListener {
            dialog.window?.setBackgroundDrawableResource(android.R.color.transparent)
        }
        dialog.show()
    }

    private fun doResetPassword(email: String, otp: String, newPassword: String) {
        scope.launch {
            try {
                val api = RetrofitClient.getApiService(applicationContext)
                val response = api.resetPassword(
                    mapOf("email" to email, "otp" to otp, "newPassword" to newPassword)
                )

                withContext(Dispatchers.Main) {
                    if (response.isSuccessful) {
                        Toast.makeText(
                            this@LoginActivity,
                            "Password reset successful. Please log in.",
                            Toast.LENGTH_LONG
                        ).show()
                    } else {
                        val body = response.errorBody()?.string()
                        val msg = body?.let {
                            runCatching { org.json.JSONObject(it).getString("message") }.getOrNull()
                        } ?: "Failed to reset password"

                        if (msg.contains("otp", ignoreCase = true)) {
                            Toast.makeText(this@LoginActivity, msg, Toast.LENGTH_LONG).show()
                            showForgotPasswordStep2(email)
                        } else {
                            Toast.makeText(this@LoginActivity, msg, Toast.LENGTH_LONG).show()
                        }
                    }
                }
            } catch (e: Exception) {
                withContext(Dispatchers.Main) {
                    Toast.makeText(this@LoginActivity, "Network error: ${e.message}", Toast.LENGTH_LONG).show()
                }
            }
        }
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