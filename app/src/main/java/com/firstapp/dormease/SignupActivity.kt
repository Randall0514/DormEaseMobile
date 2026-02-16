package com.firstapp.dormease

import android.os.Bundle
import android.text.Editable
import android.text.SpannableString
import android.text.Spanned
import android.text.TextWatcher
import android.text.method.LinkMovementMethod
import android.text.style.ClickableSpan
import android.text.style.ForegroundColorSpan
import android.util.Patterns
import android.view.View
import android.widget.*
import androidx.appcompat.app.AlertDialog
import androidx.appcompat.app.AppCompatActivity
import androidx.core.content.ContextCompat
import com.firstapp.dormease.model.ApiResponse
import com.firstapp.dormease.model.SignupRequest
import com.firstapp.dormease.network.RetrofitClient
import com.firstapp.dormease.utils.SessionManager
import com.google.android.material.textfield.TextInputEditText
import com.google.android.material.textfield.TextInputLayout
import retrofit2.Call
import retrofit2.Callback
import retrofit2.Response

class SignupActivity : AppCompatActivity() {

    private lateinit var tvPasswordReqUppercase: TextView
    private lateinit var tvPasswordReqLowercase: TextView
    private lateinit var tvPasswordReqNumber: TextView
    private lateinit var tvPasswordReqLength: TextView
    private lateinit var llPasswordRequirements: LinearLayout
    private lateinit var sessionManager: SessionManager

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_signup)

        supportActionBar?.hide()

        sessionManager = SessionManager(this)

        val tilFullName = findViewById<TextInputLayout>(R.id.tilFullName)
        val tilUsername = findViewById<TextInputLayout>(R.id.tilUsername)
        val tilEmail = findViewById<TextInputLayout>(R.id.tilEmail)
        val tilPassword = findViewById<TextInputLayout>(R.id.tilPassword)
        val tilConfirmPassword = findViewById<TextInputLayout>(R.id.tilConfirmPassword)

        val etFullName = findViewById<TextInputEditText>(R.id.etFullName)
        val etUsername = findViewById<TextInputEditText>(R.id.etUsername)
        val etEmail = findViewById<TextInputEditText>(R.id.etEmail)
        val etPassword = findViewById<TextInputEditText>(R.id.etPassword)
        val etConfirmPassword = findViewById<TextInputEditText>(R.id.etConfirmPassword)

        val cbTerms = findViewById<CheckBox>(R.id.cbTerms)
        val tvTerms = findViewById<TextView>(R.id.tvTerms)
        val btnSignup = findViewById<Button>(R.id.btnSignup)
        val tvLogin = findViewById<TextView>(R.id.tvLogin)

        llPasswordRequirements = findViewById(R.id.llPasswordRequirements)
        tvPasswordReqUppercase = findViewById(R.id.tvPasswordReqUppercase)
        tvPasswordReqLowercase = findViewById(R.id.tvPasswordReqLowercase)
        tvPasswordReqNumber = findViewById(R.id.tvPasswordReqNumber)
        tvPasswordReqLength = findViewById(R.id.tvPasswordReqLength)

        tilPassword.setErrorIconDrawable(null)
        tilConfirmPassword.setErrorIconDrawable(null)

        setupTermsText(tvTerms)

        etPassword.setOnFocusChangeListener { _, hasFocus ->
            if (hasFocus) {
                llPasswordRequirements.visibility = View.VISIBLE
                tilPassword.error = null
                tilPassword.isErrorEnabled = false
            } else {
                val password = etPassword.text.toString()
                if (isPasswordValid(password)) {
                    llPasswordRequirements.visibility = View.GONE
                }
            }
        }

        etPassword.addTextChangedListener(object : TextWatcher {
            override fun beforeTextChanged(s: CharSequence?, start: Int, count: Int, after: Int) {}
            override fun onTextChanged(s: CharSequence?, start: Int, before: Int, count: Int) {}
            override fun afterTextChanged(s: Editable?) {
                validatePasswordRequirements(s.toString())
            }
        })

        // Clear errors on focus
        listOf(tilFullName, tilUsername, tilEmail, tilPassword, tilConfirmPassword).forEach { til ->
            til.editText?.setOnFocusChangeListener { _, hasFocus ->
                if (hasFocus) {
                    til.error = null
                    til.isErrorEnabled = false
                }
            }
        }

        btnSignup.setOnClickListener {
            val fullName = etFullName.text.toString().trim()
            val username = etUsername.text.toString().trim()
            val email = etEmail.text.toString().trim()
            val password = etPassword.text.toString().trim()
            val confirmPassword = etConfirmPassword.text.toString().trim()

            var isValid = true

            if (fullName.isEmpty()) {
                tilFullName.isErrorEnabled = true
                tilFullName.error = "Full name is required"
                isValid = false
            }

            if (username.isEmpty()) {
                tilUsername.isErrorEnabled = true
                tilUsername.error = "Username is required"
                isValid = false
            }

            if (email.isEmpty()) {
                tilEmail.isErrorEnabled = true
                tilEmail.error = "Email is required"
                isValid = false
            } else if (!Patterns.EMAIL_ADDRESS.matcher(email).matches()) {
                tilEmail.isErrorEnabled = true
                tilEmail.error = "Please enter a valid email"
                isValid = false
            }

            if (password.isEmpty()) {
                tilPassword.isErrorEnabled = true
                tilPassword.error = "Password is required"
                llPasswordRequirements.visibility = View.VISIBLE
                isValid = false
            } else if (!isPasswordValid(password)) {
                tilPassword.isErrorEnabled = true
                tilPassword.error = "Password does not meet all requirements"
                llPasswordRequirements.visibility = View.VISIBLE
                isValid = false
            }

            if (confirmPassword.isEmpty()) {
                tilConfirmPassword.isErrorEnabled = true
                tilConfirmPassword.error = "Please confirm your password"
                isValid = false
            } else if (password != confirmPassword) {
                tilConfirmPassword.isErrorEnabled = true
                tilConfirmPassword.error = "Passwords do not match"
                isValid = false
            }

            if (!cbTerms.isChecked) {
                Toast.makeText(this, "Please agree to the Terms of Service and Privacy Policy", Toast.LENGTH_SHORT).show()
                isValid = false
            }

            if (!isValid) return@setOnClickListener

            // ---------------- RETROFIT NETWORK CALL ----------------
            val request = SignupRequest(
                fullName = fullName,
                username = username,
                email = email,
                password = password,
                platform = "mobile"
            )

            RetrofitClient.getApiService(this).signup(request).enqueue(object : Callback<ApiResponse> {
                override fun onResponse(call: Call<ApiResponse>, response: Response<ApiResponse>) {
                    if (response.isSuccessful) {
                        response.body()?.token?.let { token ->
                            sessionManager.saveAuthToken(token)
                        }

                        Toast.makeText(
                            this@SignupActivity,
                            "Account created successfully!",
                            Toast.LENGTH_SHORT
                        ).show()
                        finish() // back to login screen
                    } else {
                        Toast.makeText(
                            this@SignupActivity,
                            "Signup failed: ${response.errorBody()?.string()}",
                            Toast.LENGTH_LONG
                        ).show()
                    }
                }

                override fun onFailure(call: Call<ApiResponse>, t: Throwable) {
                    Toast.makeText(
                        this@SignupActivity,
                        "Network error: ${t.message}",
                        Toast.LENGTH_LONG
                    ).show()
                }
            })
            // --------------------------------------------------------
        }

        tvLogin.setOnClickListener { finish() }
    }

    private fun validatePasswordRequirements(password: String) {
        updateRequirementView(tvPasswordReqUppercase, password.any { it.isUpperCase() })
        updateRequirementView(tvPasswordReqLowercase, password.any { it.isLowerCase() })
        updateRequirementView(tvPasswordReqNumber, password.any { it.isDigit() })
        updateRequirementView(tvPasswordReqLength, password.length >= 8)
    }

    private fun updateRequirementView(textView: TextView, isMet: Boolean) {
        if (isMet) {
            textView.setTextColor(ContextCompat.getColor(this, R.color.password_requirement_met))
            textView.setCompoundDrawablesWithIntrinsicBounds(R.drawable.ic_check_green, 0, 0, 0)
        } else {
            textView.setTextColor(ContextCompat.getColor(this, R.color.password_requirement_unmet))
            textView.setCompoundDrawablesWithIntrinsicBounds(R.drawable.ic_circle_gray, 0, 0, 0)
        }
    }

    private fun isPasswordValid(password: String): Boolean {
        return password.any { it.isUpperCase() } &&
                password.any { it.isLowerCase() } &&
                password.any { it.isDigit() } &&
                password.length >= 8
    }

    private fun setupTermsText(textView: TextView) {
        val fullText = "I agree to the Terms of Service and Privacy Policy"
        val spannableString = SpannableString(fullText)

        val termsStart = fullText.indexOf("Terms of Service")
        val termsEnd = termsStart + "Terms of Service".length
        spannableString.setSpan(ForegroundColorSpan(ContextCompat.getColor(this, android.R.color.holo_blue_dark)), termsStart, termsEnd, Spanned.SPAN_EXCLUSIVE_EXCLUSIVE)
        spannableString.setSpan(object : ClickableSpan() { override fun onClick(widget: View) { showTermsOfServiceDialog() } }, termsStart, termsEnd, Spanned.SPAN_EXCLUSIVE_EXCLUSIVE)

        val privacyStart = fullText.indexOf("Privacy Policy")
        val privacyEnd = privacyStart + "Privacy Policy".length
        spannableString.setSpan(ForegroundColorSpan(ContextCompat.getColor(this, android.R.color.holo_blue_dark)), privacyStart, privacyEnd, Spanned.SPAN_EXCLUSIVE_EXCLUSIVE)
        spannableString.setSpan(object : ClickableSpan() { override fun onClick(widget: View) { showPrivacyPolicyDialog() } }, privacyStart, privacyEnd, Spanned.SPAN_EXCLUSIVE_EXCLUSIVE)

        textView.text = spannableString
        textView.movementMethod = LinkMovementMethod.getInstance()
    }

    private fun showTermsOfServiceDialog() { /* same as before */ }
    private fun showPrivacyPolicyDialog() { /* same as before */ }
}
