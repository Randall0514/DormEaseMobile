package com.firstapp.dormease.activity

// FILE PATH: app/src/main/java/com/firstapp/dormease/activity/ChangeEmailActivity.kt

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

class ChangeEmailActivity : AppCompatActivity() {

    private val scope = CoroutineScope(Dispatchers.IO + SupervisorJob())

    private lateinit var etNewEmail: EditText
    private lateinit var etPassword: EditText
    private lateinit var btnSave: Button
    private lateinit var progressBar: ProgressBar
    private lateinit var tvError: TextView
    private lateinit var tvCurrentEmail: TextView
    private lateinit var session: SessionManager

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_change_email)
        supportActionBar?.hide()

        session        = SessionManager(this)
        etNewEmail     = findViewById(R.id.etNewEmail)
        etPassword     = findViewById(R.id.etPassword)
        btnSave        = findViewById(R.id.btnSaveEmail)
        progressBar    = findViewById(R.id.progressBar)
        tvError        = findViewById(R.id.tvError)
        tvCurrentEmail = findViewById(R.id.tvCurrentEmail)

        tvCurrentEmail.text = "Current: ${session.getEmail()}"

        findViewById<ImageView>(R.id.btnBack).setOnClickListener { finish() }
        btnSave.setOnClickListener { attemptChangeEmail() }
    }

    override fun onDestroy() {
        scope.cancel()
        super.onDestroy()
    }

    private fun attemptChangeEmail() {
        val newEmail = etNewEmail.text.toString().trim()
        // password field is shown for UX confirmation but not sent to backend
        // because PATCH /auth/me does not require currentPassword
        val password = etPassword.text.toString().trim()

        tvError.visibility = View.GONE

        when {
            newEmail.isBlank() ->
                showError("Please enter a new email address.")
            !android.util.Patterns.EMAIL_ADDRESS.matcher(newEmail).matches() ->
                showError("Please enter a valid email address.")
            password.isBlank() ->
                showError("Please enter your password to confirm.")
            newEmail.equals(session.getEmail(), ignoreCase = true) ->
                showError("New email is the same as your current email.")
            else -> {
                setLoading(true)
                scope.launch {
                    try {
                        val api = RetrofitClient.getApiService(applicationContext)
                        // PATCH /auth/me accepts { email: "newemail@example.com" }
                        // The backend checks uniqueness and returns 400 if already taken.
                        val response = api.changeEmail(
                            mapOf("email" to newEmail)
                        )
                        withContext(Dispatchers.Main) {
                            setLoading(false)
                            if (response.isSuccessful) {
                                // Update the locally cached email in SessionManager
                                // so Profile page reflects the change immediately
                                session.saveUserSession(
                                    token    = session.fetchAuthToken() ?: "",
                                    name     = session.getName(),
                                    email    = newEmail,
                                    username = session.getUsername(),
                                    phone    = session.getPhone(),
                                    role     = session.getRole(),
                                    userId   = session.getUserId()
                                )
                                Toast.makeText(
                                    this@ChangeEmailActivity,
                                    "Email updated successfully!",
                                    Toast.LENGTH_SHORT
                                ).show()
                                finish()
                            } else {
                                val msg = when (response.code()) {
                                    400  -> "This email is already in use by another account."
                                    401  -> "Session expired. Please log in again."
                                    else -> "Failed to change email (${response.code()}). Please try again."
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