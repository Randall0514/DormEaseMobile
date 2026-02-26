package com.firstapp.dormease

import android.content.Intent
import android.os.Bundle
import android.widget.*
import androidx.appcompat.app.AppCompatActivity
import com.firstapp.dormease.model.LoginRequest
import com.firstapp.dormease.model.ApiResponse
import com.firstapp.dormease.network.RetrofitClient
import com.firstapp.dormease.utils.SessionManager
import com.google.android.material.textfield.TextInputEditText
import com.google.android.material.textfield.TextInputLayout
import retrofit2.Call
import retrofit2.Callback
import retrofit2.Response

class LoginActivity : AppCompatActivity() {

    private lateinit var sessionManager: SessionManager

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_login)

        sessionManager = SessionManager(this)

        supportActionBar?.hide()

        val tilUsername = findViewById<TextInputLayout>(R.id.tilUsername)
        val tilPassword = findViewById<TextInputLayout>(R.id.tilPassword)
        val etUsername  = findViewById<TextInputEditText>(R.id.etUsername)
        val etPassword  = findViewById<TextInputEditText>(R.id.etPassword)
        val btnLogin    = findViewById<Button>(R.id.btnLogin)
        val tvSignup    = findViewById<TextView>(R.id.tvSignup)

        tilPassword.setErrorIconDrawable(null)

        etUsername.setOnFocusChangeListener { _, hasFocus ->
            if (hasFocus) {
                tilUsername.error = null
                tilUsername.isErrorEnabled = false
            }
        }

        etPassword.setOnFocusChangeListener { _, hasFocus ->
            if (hasFocus) {
                tilPassword.error = null
                tilPassword.isErrorEnabled = false
            }
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

            val request = LoginRequest(
                identifier = identifier,
                password   = password,
                platform   = "mobile"
            )

            RetrofitClient.getApiService(this).login(request).enqueue(object : Callback<ApiResponse> {
                override fun onResponse(call: Call<ApiResponse>, response: Response<ApiResponse>) {
                    if (response.isSuccessful) {
                        val body = response.body()

                        // Backend returns: { message, token, user: { id, fullName, username, email, platform } }
                        sessionManager.saveUserSession(
                            token    = body?.token              ?: "",
                            name     = body?.user?.fullName     ?: identifier,
                            email    = body?.user?.email        ?: "",
                            username = body?.user?.username     ?: identifier,
                            role     = "Tenant"
                        )

                        Toast.makeText(
                            this@LoginActivity,
                            "Login successful!",
                            Toast.LENGTH_SHORT
                        ).show()

                        val intent = Intent(this@LoginActivity, DashboardActivity::class.java)
                        startActivity(intent)
                        finish()

                    } else {
                        Toast.makeText(
                            this@LoginActivity,
                            "Login failed: ${response.errorBody()?.string()}",
                            Toast.LENGTH_LONG
                        ).show()
                        tilPassword.isErrorEnabled = true
                        tilPassword.error = "Invalid username or password"
                    }
                }

                override fun onFailure(call: Call<ApiResponse>, t: Throwable) {
                    Toast.makeText(
                        this@LoginActivity,
                        "Network error: ${t.message}",
                        Toast.LENGTH_LONG
                    ).show()
                }
            })
        }

        tvSignup.setOnClickListener {
            startActivity(Intent(this, SignupActivity::class.java))
        }
    }
}