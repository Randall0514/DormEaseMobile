package com.firstapp.dormease

// FILE PATH: app/src/main/java/com/firstapp/dormease/SignupActivity.kt

import android.os.Bundle
import android.text.Editable
import android.text.InputType
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
import androidx.lifecycle.lifecycleScope
import com.firstapp.dormease.model.ApiResponse
import com.firstapp.dormease.model.SignupRequest
import com.firstapp.dormease.network.RetrofitClient
import com.firstapp.dormease.utils.SessionManager
import com.google.android.material.textfield.TextInputEditText
import com.google.android.material.textfield.TextInputLayout
import kotlinx.coroutines.launch
import retrofit2.Call
import retrofit2.Callback
import retrofit2.Response

class SignupActivity : AppCompatActivity() {

    private lateinit var tvPasswordReqUppercase : TextView
    private lateinit var tvPasswordReqLowercase : TextView
    private lateinit var tvPasswordReqNumber    : TextView
    private lateinit var tvPasswordReqLength    : TextView
    private lateinit var llPasswordRequirements : LinearLayout
    private lateinit var sessionManager         : SessionManager

    // Fields cached so we can use them after OTP confirmation
    private var pendingFullName = ""
    private var pendingUsername = ""
    private var pendingEmail    = ""
    private var pendingPassword = ""
    private var pendingPhone    = ""   // e.g. "+639XXXXXXXXX"

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_signup)
        supportActionBar?.hide()

        sessionManager = SessionManager(this)

        val tilFullName        = findViewById<TextInputLayout>(R.id.tilFullName)
        val tilUsername        = findViewById<TextInputLayout>(R.id.tilUsername)
        val tilEmail           = findViewById<TextInputLayout>(R.id.tilEmail)
        val tilPhone           = findViewById<TextInputLayout>(R.id.tilPhone)
        val tilPassword        = findViewById<TextInputLayout>(R.id.tilPassword)
        val tilConfirmPassword = findViewById<TextInputLayout>(R.id.tilConfirmPassword)

        val etFullName        = findViewById<TextInputEditText>(R.id.etFullName)
        val etUsername        = findViewById<TextInputEditText>(R.id.etUsername)
        val etEmail           = findViewById<TextInputEditText>(R.id.etEmail)
        val etPhone           = findViewById<TextInputEditText>(R.id.etPhone)
        val etPassword        = findViewById<TextInputEditText>(R.id.etPassword)
        val etConfirmPassword = findViewById<TextInputEditText>(R.id.etConfirmPassword)

        val cbTerms   = findViewById<CheckBox>(R.id.cbTerms)
        val tvTerms   = findViewById<TextView>(R.id.tvTerms)
        val btnSignup = findViewById<Button>(R.id.btnSignup)
        val tvLogin   = findViewById<TextView>(R.id.tvLogin)

        llPasswordRequirements = findViewById(R.id.llPasswordRequirements)
        tvPasswordReqUppercase = findViewById(R.id.tvPasswordReqUppercase)
        tvPasswordReqLowercase = findViewById(R.id.tvPasswordReqLowercase)
        tvPasswordReqNumber    = findViewById(R.id.tvPasswordReqNumber)
        tvPasswordReqLength    = findViewById(R.id.tvPasswordReqLength)

        tilPassword.setErrorIconDrawable(null)
        tilConfirmPassword.setErrorIconDrawable(null)

        setupTermsText(tvTerms)

        etPassword.setOnFocusChangeListener { _, hasFocus ->
            if (hasFocus) {
                llPasswordRequirements.visibility = View.VISIBLE
                tilPassword.error = null
                tilPassword.isErrorEnabled = false
            } else {
                if (isPasswordValid(etPassword.text.toString())) {
                    llPasswordRequirements.visibility = View.GONE
                }
            }
        }

        etPassword.addTextChangedListener(object : TextWatcher {
            override fun beforeTextChanged(s: CharSequence?, start: Int, count: Int, after: Int) {}
            override fun onTextChanged(s: CharSequence?, start: Int, before: Int, count: Int) {}
            override fun afterTextChanged(s: Editable?) { validatePasswordRequirements(s.toString()) }
        })

        // Clear errors on focus
        listOf(tilFullName, tilUsername, tilEmail, tilPhone, tilPassword, tilConfirmPassword).forEach { til ->
            til.editText?.setOnFocusChangeListener { _, hasFocus ->
                if (hasFocus) { til.error = null; til.isErrorEnabled = false }
            }
        }

        btnSignup.setOnClickListener {
            val fullName        = etFullName.text.toString().trim()
            val username        = etUsername.text.toString().trim()
            val email           = etEmail.text.toString().trim()
            val rawPhone        = etPhone.text.toString().trim()
            val password        = etPassword.text.toString().trim()
            val confirmPassword = etConfirmPassword.text.toString().trim()

            var isValid = true

            if (fullName.isEmpty()) {
                tilFullName.isErrorEnabled = true; tilFullName.error = "Full name is required"; isValid = false
            }
            if (username.isEmpty()) {
                tilUsername.isErrorEnabled = true; tilUsername.error = "Username is required"; isValid = false
            }
            if (email.isEmpty()) {
                tilEmail.isErrorEnabled = true; tilEmail.error = "Email is required"; isValid = false
            } else if (!Patterns.EMAIL_ADDRESS.matcher(email).matches()) {
                tilEmail.isErrorEnabled = true; tilEmail.error = "Please enter a valid email"; isValid = false
            }
            // Phone: required, must be exactly 10 digits (local PH format: 9XXXXXXXXX)
            val phoneDigits = rawPhone.filter { it.isDigit() }
            if (rawPhone.isEmpty()) {
                tilPhone.isErrorEnabled = true; tilPhone.error = "Mobile number is required"; isValid = false
            } else if (phoneDigits.length != 10 || !phoneDigits.startsWith("9")) {
                tilPhone.isErrorEnabled = true; tilPhone.error = "Enter a valid 10-digit number starting with 9"; isValid = false
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
                tilConfirmPassword.isErrorEnabled = true; tilConfirmPassword.error = "Please confirm your password"; isValid = false
            } else if (password != confirmPassword) {
                tilConfirmPassword.isErrorEnabled = true; tilConfirmPassword.error = "Passwords do not match"; isValid = false
            }
            if (!cbTerms.isChecked) {
                Toast.makeText(this, "Please agree to the Terms of Service and Privacy Policy", Toast.LENGTH_SHORT).show()
                isValid = false
            }
            if (!isValid) return@setOnClickListener

            // Cache form values; format phone as +639XXXXXXXXX
            pendingFullName = fullName
            pendingUsername = username
            pendingEmail    = email
            pendingPhone    = "+63$phoneDigits"
            pendingPassword = password

            btnSignup.isEnabled = false
            btnSignup.text      = "Sending OTP..."
            requestOtpAndShowDialog(btnSignup)
        }

        tvLogin.setOnClickListener { finish() }
    }

    // ── Step 1: Request OTP from backend ─────────────────────────────────────

    private fun requestOtpAndShowDialog(btnSignup: Button) {
        lifecycleScope.launch {
            try {
                val response = RetrofitClient.getApiService(this@SignupActivity)
                    .requestSignupOtp(
                        mapOf("email" to pendingEmail, "username" to pendingUsername)
                    )

                btnSignup.isEnabled = true
                btnSignup.text      = "Create Account"

                if (response.isSuccessful) {
                    showOtpDialog(btnSignup)
                } else {
                    val errorBody = response.errorBody()?.string() ?: "Failed to send OTP"
                    val message = try {
                        org.json.JSONObject(errorBody).optString("message", errorBody)
                    } catch (e: Exception) { errorBody }
                    Toast.makeText(this@SignupActivity, message, Toast.LENGTH_LONG).show()
                }
            } catch (e: Exception) {
                btnSignup.isEnabled = true
                btnSignup.text      = "Create Account"
                Toast.makeText(this@SignupActivity, "Network error: ${e.message}", Toast.LENGTH_LONG).show()
            }
        }
    }

    // ── Step 2: Show OTP dialog ───────────────────────────────────────────────

    private fun showOtpDialog(btnSignup: Button) {
        val layout = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            setPadding(60, 20, 60, 0)
        }
        val etOtp = EditText(this).apply {
            hint          = "Enter 6-digit OTP"
            inputType     = InputType.TYPE_CLASS_NUMBER
            maxLines      = 1
            letterSpacing = 0.3f
        }
        layout.addView(etOtp)

        AlertDialog.Builder(this)
            .setTitle("Verify Your Email")
            .setMessage("We sent a 6-digit code to $pendingEmail. Enter it below to complete your registration.")
            .setView(layout)
            .setCancelable(false)
            .setPositiveButton("Verify & Sign Up") { dialog, _ ->
                val otp = etOtp.text.toString().trim()
                if (otp.length != 6) {
                    Toast.makeText(this, "Please enter the 6-digit OTP", Toast.LENGTH_SHORT).show()
                    return@setPositiveButton
                }
                dialog.dismiss()
                submitSignup(otp, btnSignup)
            }
            .setNegativeButton("Resend OTP") { dialog, _ ->
                dialog.dismiss()
                btnSignup.isEnabled = false
                btnSignup.text      = "Sending OTP..."
                requestOtpAndShowDialog(btnSignup)
            }
            .show()
    }

    // ── Step 3: Submit signup with OTP ────────────────────────────────────────

    private fun submitSignup(otp: String, btnSignup: Button) {
        btnSignup.isEnabled = false
        btnSignup.text      = "Creating account..."

        val request = SignupRequest(
            fullName = pendingFullName,
            username = pendingUsername,
            email    = pendingEmail,
            password = pendingPassword,
            otp      = otp,
            phone    = pendingPhone,
            platform = "mobile"
        )

        RetrofitClient.getApiService(this).signup(request)
            .enqueue(object : Callback<ApiResponse> {
                override fun onResponse(call: Call<ApiResponse>, response: Response<ApiResponse>) {
                    btnSignup.isEnabled = true
                    btnSignup.text      = "Create Account"

                    if (response.isSuccessful) {
                        response.body()?.token?.let { token ->
                            sessionManager.saveAuthToken(token)
                        }
                        // Save phone so reservation form auto-fills it
                        if (pendingPhone.isNotBlank()) {
                            sessionManager.savePhone(pendingPhone)
                        }
                        // Save full name and email for reservation auto-fill
                        sessionManager.saveFullName(pendingFullName)
                        sessionManager.saveEmail(pendingEmail)

                        Toast.makeText(
                            this@SignupActivity,
                            "Account created successfully!",
                            Toast.LENGTH_SHORT
                        ).show()
                        finish()
                    } else {
                        val errorBody = response.errorBody()?.string() ?: "Signup failed"
                        val message = try {
                            org.json.JSONObject(errorBody).optString("message", errorBody)
                        } catch (e: Exception) { errorBody }
                        Toast.makeText(this@SignupActivity, message, Toast.LENGTH_LONG).show()

                        if (message.contains("OTP", ignoreCase = true)) {
                            showOtpDialog(btnSignup)
                        }
                    }
                }

                override fun onFailure(call: Call<ApiResponse>, t: Throwable) {
                    btnSignup.isEnabled = true
                    btnSignup.text      = "Create Account"
                    Toast.makeText(this@SignupActivity, "Network error: ${t.message}", Toast.LENGTH_LONG).show()
                }
            })
    }

    // ── Password validation ───────────────────────────────────────────────────

    private fun validatePasswordRequirements(password: String) {
        updateRequirementView(tvPasswordReqUppercase, password.any { it.isUpperCase() })
        updateRequirementView(tvPasswordReqLowercase, password.any { it.isLowerCase() })
        updateRequirementView(tvPasswordReqNumber,    password.any { it.isDigit() })
        updateRequirementView(tvPasswordReqLength,    password.length >= 8)
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

    private fun isPasswordValid(password: String): Boolean =
        password.any { it.isUpperCase() } &&
                password.any { it.isLowerCase() } &&
                password.any { it.isDigit() } &&
                password.length >= 8

    // ── Terms & Privacy links ─────────────────────────────────────────────────

    private fun setupTermsText(textView: TextView) {
        val fullText  = "I agree to the Terms of Service and Privacy Policy"
        val spannable = SpannableString(fullText)

        val termsStart = fullText.indexOf("Terms of Service")
        val termsEnd   = termsStart + "Terms of Service".length
        spannable.setSpan(ForegroundColorSpan(ContextCompat.getColor(this, android.R.color.holo_blue_dark)), termsStart, termsEnd, Spanned.SPAN_EXCLUSIVE_EXCLUSIVE)
        spannable.setSpan(object : ClickableSpan() { override fun onClick(widget: View) { showTermsOfServiceDialog() } }, termsStart, termsEnd, Spanned.SPAN_EXCLUSIVE_EXCLUSIVE)

        val privacyStart = fullText.indexOf("Privacy Policy")
        val privacyEnd   = privacyStart + "Privacy Policy".length
        spannable.setSpan(ForegroundColorSpan(ContextCompat.getColor(this, android.R.color.holo_blue_dark)), privacyStart, privacyEnd, Spanned.SPAN_EXCLUSIVE_EXCLUSIVE)
        spannable.setSpan(object : ClickableSpan() { override fun onClick(widget: View) { showPrivacyPolicyDialog() } }, privacyStart, privacyEnd, Spanned.SPAN_EXCLUSIVE_EXCLUSIVE)

        textView.text           = spannable
        textView.movementMethod = LinkMovementMethod.getInstance()
    }

    private fun showTermsOfServiceDialog() {
        AlertDialog.Builder(this)
            .setTitle("Terms of Service")
            .setMessage(
                "By using DormEase, you agree to the following:\n\n" +
                        "1. You will provide accurate information when creating your account.\n\n" +
                        "2. You are responsible for maintaining the confidentiality of your account.\n\n" +
                        "3. DormEase is a platform connecting tenants and dorm owners. We are not a party to any rental agreement.\n\n" +
                        "4. Misuse of the platform, including fraudulent reservations, may result in account termination.\n\n" +
                        "5. DormEase reserves the right to update these terms at any time."
            )
            .setPositiveButton("Close", null)
            .show()
    }

    private fun showPrivacyPolicyDialog() {
        AlertDialog.Builder(this)
            .setTitle("Privacy Policy")
            .setMessage(
                "Your privacy is important to us.\n\n" +
                        "1. We collect your name, email, and phone number to facilitate reservations.\n\n" +
                        "2. Your information is shared only with dorm owners you choose to reserve with.\n\n" +
                        "3. We do not sell your personal data to third parties.\n\n" +
                        "4. You may request deletion of your account and associated data at any time by contacting support.\n\n" +
                        "5. We use industry-standard security measures to protect your data."
            )
            .setPositiveButton("Close", null)
            .show()
    }
}