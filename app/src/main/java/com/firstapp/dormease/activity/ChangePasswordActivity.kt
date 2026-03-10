package com.firstapp.dormease.activity

// FILE PATH: app/src/main/java/com/firstapp/dormease/activity/ChangePasswordActivity.kt

import android.os.Bundle
import android.view.View
import android.widget.Button
import android.widget.EditText
import android.widget.ImageView
import android.widget.ProgressBar
import android.widget.TextView
import android.widget.Toast
import androidx.appcompat.app.AppCompatActivity
import com.firstapp.dormease.R
import com.firstapp.dormease.network.RetrofitClient
import com.firstapp.dormease.utils.SessionManager
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

class ChangePasswordActivity : AppCompatActivity() {

    private val scope = CoroutineScope(Dispatchers.IO + SupervisorJob())

    private lateinit var etCurrentPassword: EditText
    private lateinit var etNewPassword: EditText
    private lateinit var etConfirmPassword: EditText
    private lateinit var btnSave: Button
    private lateinit var progressBar: ProgressBar
    private lateinit var tvError: TextView
    private lateinit var session: SessionManager

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_change_password)
        supportActionBar?.hide()

        session            = SessionManager(this)
        etCurrentPassword  = findViewById(R.id.etCurrentPassword)
        etNewPassword      = findViewById(R.id.etNewPassword)
        etConfirmPassword  = findViewById(R.id.etConfirmPassword)
        btnSave            = findViewById(R.id.btnSavePassword)
        progressBar        = findViewById(R.id.progressBar)
        tvError            = findViewById(R.id.tvError)

        findViewById<ImageView>(R.id.btnBack).setOnClickListener { finish() }
        btnSave.setOnClickListener { attemptChangePassword() }
    }

    override fun onDestroy() {
        scope.cancel()
        super.onDestroy()
    }

    private fun attemptChangePassword() {
        val current = etCurrentPassword.text.toString().trim()
        val newPass  = etNewPassword.text.toString().trim()
        val confirm  = etConfirmPassword.text.toString().trim()

        tvError.visibility = View.GONE

        when {
            current.isBlank() -> showError("Please enter your current password.")
            newPass.isBlank()  -> showError("Please enter a new password.")
            newPass.length < 6 -> showError("New password must be at least 6 characters.")
            newPass != confirm  -> showError("Passwords do not match.")
            newPass == current  -> showError("New password must be different from your current password.")
            else -> {
                setLoading(true)
                scope.launch {
                    try {
                        val api = RetrofitClient.getApiService(applicationContext)
                        // PATCH /auth/me accepts { password: "newPassword" }
                        // and bcrypt-hashes it on the server automatically.
                        val response = api.changePassword(
                            mapOf("password" to newPass)
                        )
                        withContext(Dispatchers.Main) {
                            setLoading(false)
                            if (response.isSuccessful) {
                                Toast.makeText(
                                    this@ChangePasswordActivity,
                                    "Password changed successfully!",
                                    Toast.LENGTH_SHORT
                                ).show()
                                finish()
                            } else {
                                val msg = when (response.code()) {
                                    400  -> "Invalid request. Please try again."
                                    401  -> "Session expired. Please log in again."
                                    else -> "Failed to change password (${response.code()}). Please try again."
                                }
                                showError(msg)
                            }
                        }
                    } catch (e: Exception) {
                        withContext(Dispatchers.Main) {
                            setLoading(false)
                            showError("Network error. Please check your connection.")
                        }
                    }
                }
            }
        }
    }

    private fun showError(message: String) {
        tvError.text       = message
        tvError.visibility = View.VISIBLE
    }

    private fun setLoading(loading: Boolean) {
        progressBar.visibility = if (loading) View.VISIBLE else View.GONE
        btnSave.isEnabled      = !loading
        btnSave.text           = if (loading) "Saving…" else "Save Changes"
    }
}