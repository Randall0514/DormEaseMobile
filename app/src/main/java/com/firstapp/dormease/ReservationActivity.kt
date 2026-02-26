package com.firstapp.dormease.activity

import android.app.DatePickerDialog
import android.os.Bundle
import android.text.Editable
import android.text.TextWatcher
import android.widget.ImageButton
import android.widget.TextView
import android.widget.Toast
import androidx.appcompat.app.AppCompatActivity
import androidx.lifecycle.lifecycleScope
import com.google.android.material.button.MaterialButton
import com.google.android.material.textfield.TextInputEditText
import com.firstapp.dormease.R
import com.firstapp.dormease.model.Reservation
import com.firstapp.dormease.network.RetrofitClient
import kotlinx.coroutines.launch
import java.util.Calendar

class ReservationActivity : AppCompatActivity() {

    private lateinit var btnBack: ImageButton
    private lateinit var etFullName: TextInputEditText
    private lateinit var etPhoneNumber: TextInputEditText
    private lateinit var etMoveInDate: TextInputEditText
    private lateinit var etDuration: TextInputEditText
    private lateinit var etNotes: TextInputEditText
    private lateinit var btnSubmitReservation: MaterialButton

    private lateinit var tvSummaryRentLabel: TextView
    private lateinit var tvSummaryRentTotal: TextView
    private lateinit var tvSummaryDeposit: TextView
    private lateinit var tvSummaryAdvance: TextView
    private lateinit var tvSummaryTotal: TextView

    private var dormName: String = ""
    private var dormLocation: String = ""
    private var dormOwnerId: Int = 0       // ← the owner this reservation belongs to
    private var pricePerMonth: Int = 0
    private var depositAmount: Int = 0
    private var advanceAmount: Int = 0

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_reservation)

        // Retrieve dorm data passed via Intent
        dormName      = intent.getStringExtra("DORM_NAME") ?: "N/A"
        dormLocation  = intent.getStringExtra("DORM_LOCATION") ?: "N/A"
        dormOwnerId   = intent.getIntExtra("DORM_OWNER_ID", 0)  // ← receive owner id
        val dormPrice   = intent.getStringExtra("DORM_PRICE") ?: "0"
        val dormDeposit = intent.getStringExtra("DORM_DEPOSIT") ?: "0"
        val dormAdvance = intent.getStringExtra("DORM_ADVANCE") ?: "0"

        pricePerMonth = dormPrice.replace(",", "").toIntOrNull() ?: 0
        depositAmount = dormDeposit.replace(",", "").toIntOrNull() ?: 0
        advanceAmount = dormAdvance.replace(",", "").toIntOrNull() ?: 0

        // Bind views
        btnBack              = findViewById(R.id.btnBack)
        etFullName           = findViewById(R.id.etFullName)
        etPhoneNumber        = findViewById(R.id.etPhoneNumber)
        etMoveInDate         = findViewById(R.id.etMoveInDate)
        etDuration           = findViewById(R.id.etDuration)
        etNotes              = findViewById(R.id.etNotes)
        btnSubmitReservation = findViewById(R.id.btnSubmitReservation)
        tvSummaryRentLabel   = findViewById(R.id.tvSummaryRentLabel)
        tvSummaryRentTotal   = findViewById(R.id.tvSummaryRentTotal)
        tvSummaryDeposit     = findViewById(R.id.tvSummaryDeposit)
        tvSummaryAdvance     = findViewById(R.id.tvSummaryAdvance)
        tvSummaryTotal       = findViewById(R.id.tvSummaryTotal)

        // Populate dorm summary
        findViewById<TextView>(R.id.tvReserveDormName).text = dormName
        findViewById<TextView>(R.id.tvReserveLocation).text = dormLocation
        findViewById<TextView>(R.id.tvReservePrice).text    = "₱ $dormPrice/month"
        findViewById<TextView>(R.id.tvReserveDeposit).text  = "₱ $dormDeposit"
        findViewById<TextView>(R.id.tvReserveAdvance).text  = "₱ $dormAdvance"

        updatePaymentSummary(1)

        btnBack.setOnClickListener {
            onBackPressedDispatcher.onBackPressed()
        }

        etMoveInDate.setOnClickListener {
            showDatePicker()
        }

        etDuration.addTextChangedListener(object : TextWatcher {
            override fun beforeTextChanged(s: CharSequence?, start: Int, count: Int, after: Int) {}
            override fun onTextChanged(s: CharSequence?, start: Int, before: Int, count: Int) {}
            override fun afterTextChanged(s: Editable?) {
                val duration = s.toString().toIntOrNull() ?: 1
                updatePaymentSummary(duration)
            }
        })

        btnSubmitReservation.setOnClickListener {
            if (validateForm()) {
                submitReservation()
            }
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
            etPhoneNumber.error = "Phone number is required"
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

        val reservation = Reservation(
            dorm_name       = dormName,
            location        = dormLocation,
            dorm_owner_id   = dormOwnerId,   // ← sent to backend so only correct admin is notified
            full_name       = etFullName.text.toString().trim(),
            phone           = etPhoneNumber.text.toString().trim(),
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
                val apiService = RetrofitClient.getApiService(applicationContext)
                val response = apiService.submitReservation(reservation)

                if (response.isSuccessful) {
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