package com.firstapp.dormease.activity

import android.app.DatePickerDialog
import android.content.Context
import android.os.Bundle
import android.text.Editable
import android.text.InputFilter
import android.text.TextWatcher
import android.widget.ImageButton
import android.widget.TextView
import android.widget.Toast
import androidx.appcompat.app.AppCompatActivity
import androidx.lifecycle.lifecycleScope
import com.google.android.material.button.MaterialButton
import com.google.android.material.textfield.TextInputEditText
import com.google.android.material.textfield.TextInputLayout
import com.firstapp.dormease.R
import com.firstapp.dormease.model.Reservation
import com.firstapp.dormease.network.RetrofitClient
import com.firstapp.dormease.utils.SessionManager
import kotlinx.coroutines.launch
import java.util.Calendar

class ReservationActivity : AppCompatActivity() {

    private lateinit var btnBack: ImageButton
    private lateinit var etFullName: TextInputEditText
    private lateinit var etPhoneNumber: TextInputEditText
    private lateinit var tilPhoneNumber: TextInputLayout
    private lateinit var etMoveInDate: TextInputEditText
    private lateinit var etDuration: TextInputEditText
    private lateinit var etNotes: TextInputEditText
    private lateinit var btnSubmitReservation: MaterialButton

    private lateinit var tvSummaryRentLabel: TextView
    private lateinit var tvSummaryRentTotal: TextView
    private lateinit var tvSummaryDeposit: TextView
    private lateinit var tvSummaryAdvance: TextView
    private lateinit var tvSummaryTotal: TextView

    private lateinit var sessionManager: SessionManager

    private var dormName: String = ""
    private var dormLocation: String = ""
    private var dormOwnerId: Int = 0
    private var pricePerMonth: Int = 0
    private var depositAmount: Int = 0
    private var advanceAmount: Int = 0

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_reservation)

        sessionManager = SessionManager(this)

        dormName      = intent.getStringExtra("DORM_NAME")     ?: "N/A"
        dormLocation  = intent.getStringExtra("DORM_LOCATION") ?: "N/A"
        dormOwnerId   = intent.getIntExtra("DORM_OWNER_ID", 0)
        val dormPrice   = intent.getStringExtra("DORM_PRICE")   ?: "0"
        val dormDeposit = intent.getStringExtra("DORM_DEPOSIT") ?: "0"
        val dormAdvance = intent.getStringExtra("DORM_ADVANCE") ?: "0"

        pricePerMonth = dormPrice.replace(",", "").toIntOrNull()   ?: 0
        depositAmount = dormDeposit.replace(",", "").toIntOrNull() ?: 0
        advanceAmount = dormAdvance.replace(",", "").toIntOrNull() ?: 0

        btnBack              = findViewById(R.id.btnBack)
        etFullName           = findViewById(R.id.etFullName)
        etPhoneNumber        = findViewById(R.id.etPhoneNumber)
        tilPhoneNumber       = findViewById(R.id.tilPhoneNumber)
        etMoveInDate         = findViewById(R.id.etMoveInDate)
        etDuration           = findViewById(R.id.etDuration)
        etNotes              = findViewById(R.id.etNotes)
        btnSubmitReservation = findViewById(R.id.btnSubmitReservation)
        tvSummaryRentLabel   = findViewById(R.id.tvSummaryRentLabel)
        tvSummaryRentTotal   = findViewById(R.id.tvSummaryRentTotal)
        tvSummaryDeposit     = findViewById(R.id.tvSummaryDeposit)
        tvSummaryAdvance     = findViewById(R.id.tvSummaryAdvance)
        tvSummaryTotal       = findViewById(R.id.tvSummaryTotal)

        findViewById<TextView>(R.id.tvReserveDormName).text = dormName
        findViewById<TextView>(R.id.tvReserveLocation).text = dormLocation
        findViewById<TextView>(R.id.tvReservePrice).text    = "₱ $dormPrice/month"
        findViewById<TextView>(R.id.tvReserveDeposit).text  = "₱ $dormDeposit"
        findViewById<TextView>(R.id.tvReserveAdvance).text  = "₱ $dormAdvance"

        etFullName.setText(sessionManager.getName())

        tilPhoneNumber.prefixText = "+63"
        etPhoneNumber.filters = arrayOf(InputFilter.LengthFilter(10))

        etPhoneNumber.addTextChangedListener(object : TextWatcher {
            override fun beforeTextChanged(s: CharSequence?, start: Int, count: Int, after: Int) {}
            override fun onTextChanged(s: CharSequence?, start: Int, before: Int, count: Int) {}
            override fun afterTextChanged(s: Editable?) {
                val input      = s.toString()
                val digitsOnly = input.filter { it.isDigit() }
                if (input != digitsOnly) {
                    etPhoneNumber.setText(digitsOnly)
                    etPhoneNumber.setSelection(digitsOnly.length)
                }
                if (digitsOnly.isNotEmpty()) tilPhoneNumber.error = null
            }
        })

        updatePaymentSummary(1)

        btnBack.setOnClickListener { onBackPressedDispatcher.onBackPressed() }
        etMoveInDate.setOnClickListener { showDatePicker() }

        etDuration.addTextChangedListener(object : TextWatcher {
            override fun beforeTextChanged(s: CharSequence?, start: Int, count: Int, after: Int) {}
            override fun onTextChanged(s: CharSequence?, start: Int, before: Int, count: Int) {}
            override fun afterTextChanged(s: Editable?) {
                val duration = s.toString().toIntOrNull() ?: 1
                updatePaymentSummary(duration)
            }
        })

        btnSubmitReservation.setOnClickListener {
            if (validateForm()) submitReservation()
        }
    }

    private fun updatePaymentSummary(duration: Int) {
        val rentTotal = pricePerMonth * duration
        val total     = rentTotal + depositAmount + advanceAmount
        tvSummaryRentLabel.text = "Monthly Rent x$duration"
        tvSummaryRentTotal.text = "₱${String.format("%,d", rentTotal)}"
        tvSummaryDeposit.text   = "₱${String.format("%,d", depositAmount)}"
        tvSummaryAdvance.text   = "₱${String.format("%,d", advanceAmount)}"
        tvSummaryTotal.text     = "₱${String.format("%,d", total)}"
    }

    private fun showDatePicker() {
        val calendar = Calendar.getInstance()
        DatePickerDialog(this, { _, selectedYear, selectedMonth, selectedDay ->
            val formattedDate = "%02d/%02d/%04d".format(selectedDay, selectedMonth + 1, selectedYear)
            etMoveInDate.setText(formattedDate)
        }, calendar.get(Calendar.YEAR), calendar.get(Calendar.MONTH), calendar.get(Calendar.DAY_OF_MONTH))
            .also {
                it.datePicker.minDate = System.currentTimeMillis()
                it.show()
            }
    }

    private fun validateForm(): Boolean {
        val fullName   = etFullName.text.toString().trim()
        val phone      = etPhoneNumber.text.toString().trim()
        val moveInDate = etMoveInDate.text.toString().trim()
        val duration   = etDuration.text.toString().trim()

        if (fullName.isEmpty()) {
            etFullName.error = "Full name is required"
            etFullName.requestFocus()
            return false
        }
        if (phone.isEmpty()) {
            tilPhoneNumber.error = "Phone number is required"
            etPhoneNumber.requestFocus()
            return false
        }
        if (phone.length != 10) {
            tilPhoneNumber.error = "Phone number must be exactly 10 digits"
            etPhoneNumber.requestFocus()
            return false
        }
        if (!phone.startsWith("9")) {
            tilPhoneNumber.error = "Must start with 9 (e.g. 9XXXXXXXXX)"
            etPhoneNumber.requestFocus()
            return false
        }
        if (moveInDate.isEmpty()) {
            etMoveInDate.error = "Move-in date is required"
            etMoveInDate.requestFocus()
            return false
        }
        if (duration.isEmpty()) {
            etDuration.error = "Duration of stay is required"
            etDuration.requestFocus()
            return false
        }
        return true
    }

    private fun submitReservation() {
        val duration  = etDuration.text.toString().toIntOrNull() ?: 1
        val rentTotal = pricePerMonth * duration
        val total     = rentTotal + depositAmount + advanceAmount

        val fullPhone = "+63${etPhoneNumber.text.toString().trim()}"

        val reservation = Reservation(
            dorm_name       = dormName,
            location        = dormLocation,
            dorm_owner_id   = dormOwnerId,
            full_name       = etFullName.text.toString().trim(),
            phone           = fullPhone,
            move_in_date    = etMoveInDate.text.toString().trim(),
            duration_months = duration,
            price_per_month = pricePerMonth,
            deposit         = depositAmount,
            advance         = advanceAmount,
            total_amount    = total,
            notes           = etNotes.text.toString().trim(),
            payment_method  = "cash_on_move_in"
        )

        btnSubmitReservation.isEnabled = false
        btnSubmitReservation.text = "Submitting..."

        lifecycleScope.launch {
            try {
                val response = RetrofitClient.getApiService(applicationContext)
                    .submitReservation(reservation)

                if (response.isSuccessful) {
                    // Save phone so NotificationsActivity and DashboardActivity
                    // can fetch this tenant's reservation status via REST
                    sessionManager.savePhone(fullPhone)

                    // Also persist into NotificationState so LoginActivity can
                    // restore the phone even if DormEaseSession is cleared on logout
                    getSharedPreferences("NotificationState", Context.MODE_PRIVATE)
                        .edit()
                        .putString("last_phone", fullPhone.filter { it.isDigit() }.takeLast(10))
                        .apply()

                    Toast.makeText(
                        this@ReservationActivity,
                        "Reservation submitted! The owner will be notified.",
                        Toast.LENGTH_LONG
                    ).show()
                    finish()
                } else {
                    Toast.makeText(
                        this@ReservationActivity,
                        "Failed: ${response.message()}",
                        Toast.LENGTH_LONG
                    ).show()
                    resetButton()
                }
            } catch (e: Exception) {
                Toast.makeText(
                    this@ReservationActivity,
                    "Error: ${e.message}",
                    Toast.LENGTH_LONG
                ).show()
                resetButton()
            }
        }
    }

    private fun resetButton() {
        btnSubmitReservation.isEnabled = true
        btnSubmitReservation.text = "CONFIRM RESERVATION"
    }
}