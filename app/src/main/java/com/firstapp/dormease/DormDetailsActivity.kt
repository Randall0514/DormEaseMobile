package com.firstapp.dormease.activity

import android.content.Intent
import android.os.Bundle
import android.view.View
import android.widget.CheckBox
import android.widget.ImageButton
import android.widget.ImageView
import android.widget.LinearLayout
import android.widget.TextView
import androidx.appcompat.app.AppCompatActivity
import com.bumptech.glide.Glide
import com.google.android.material.button.MaterialButton
import com.firstapp.dormease.R

class DormDetailsActivity : AppCompatActivity() {

    private val BASE_URL = "http://192.168.68.125:3000"
    private var photoUrls: ArrayList<String> = arrayListOf()
    private var currentImageIndex = 0

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_dorm_details)

        // Retrieve data from Intent
        val dormName     = intent.getStringExtra("DORM_NAME") ?: "N/A"
        val ownerName    = intent.getStringExtra("DORM_OWNER") ?: "N/A"
        val phone        = intent.getStringExtra("DORM_PHONE") ?: "N/A"
        val location     = intent.getStringExtra("DORM_LOCATION") ?: "N/A"
        val price        = intent.getStringExtra("DORM_PRICE") ?: "N/A"
        val deposit      = intent.getStringExtra("DORM_DEPOSIT") ?: "N/A"
        val advance      = intent.getStringExtra("DORM_ADVANCE") ?: "N/A"
        val roomsLeft    = intent.getIntExtra("DORM_ROOMS_LEFT", 0)
        val utilities    = intent.getStringArrayListExtra("DORM_UTILITIES") ?: arrayListOf()
        photoUrls        = intent.getStringArrayListExtra("DORM_PHOTO_URLS") ?: arrayListOf()

        // Bind views
        val btnBack       = findViewById<ImageButton>(R.id.btnBack)
        val ivDormImage   = findViewById<ImageView>(R.id.ivDormImage)
        val btnPrevImage  = findViewById<ImageButton>(R.id.btnPrevImage)
        val btnNextImage  = findViewById<ImageButton>(R.id.btnNextImage)
        val llIndicator   = findViewById<LinearLayout>(R.id.llImageIndicator)
        val tvDormName    = findViewById<TextView>(R.id.tvDormName)
        val tvOwnerName   = findViewById<TextView>(R.id.tvOwnerName)
        val tvPhoneNumber = findViewById<TextView>(R.id.tvPhoneNumber)
        val tvLocation    = findViewById<TextView>(R.id.tvLocation)
        val tvPrice       = findViewById<TextView>(R.id.tvPrice)
        val tvDeposit     = findViewById<TextView>(R.id.tvDeposit)
        val tvAdvance     = findViewById<TextView>(R.id.tvAdvance)
        val tvRoomsLeft   = findViewById<TextView>(R.id.tvRoomsLeft)
        val cbWater       = findViewById<CheckBox>(R.id.cbWater)
        val cbElectricity = findViewById<CheckBox>(R.id.cbElectricity)
        val cbGas         = findViewById<CheckBox>(R.id.cbGas)
        val btnMessage    = findViewById<MaterialButton>(R.id.btnMessage)
        val btnReserve    = findViewById<MaterialButton>(R.id.btnReserve)

        // Populate data
        tvDormName.text    = dormName
        tvOwnerName.text   = ownerName
        tvPhoneNumber.text = "+63 $phone"
        tvLocation.text    = location
        tvPrice.text       = "₱ $price/month"
        tvDeposit.text     = "Deposit: ₱$deposit"
        tvAdvance.text     = "Advance: ₱$advance"
        tvRoomsLeft.text   = "$roomsLeft Rooms Capacity"

        cbWater.isChecked       = utilities.contains("water")
        cbElectricity.isChecked = utilities.contains("electricity")
        cbGas.isChecked         = utilities.contains("gas")

        // Load images
        if (photoUrls.isNotEmpty()) {
            loadImage(ivDormImage, photoUrls[currentImageIndex])
            updateIndicators(llIndicator, photoUrls.size, currentImageIndex)
            updateNavButtons(btnPrevImage, btnNextImage)

            btnPrevImage.setOnClickListener {
                if (currentImageIndex > 0) {
                    currentImageIndex--
                    loadImage(ivDormImage, photoUrls[currentImageIndex])
                    updateIndicators(llIndicator, photoUrls.size, currentImageIndex)
                    updateNavButtons(btnPrevImage, btnNextImage)
                }
            }

            btnNextImage.setOnClickListener {
                if (currentImageIndex < photoUrls.size - 1) {
                    currentImageIndex++
                    loadImage(ivDormImage, photoUrls[currentImageIndex])
                    updateIndicators(llIndicator, photoUrls.size, currentImageIndex)
                    updateNavButtons(btnPrevImage, btnNextImage)
                }
            }
        } else {
            btnPrevImage.visibility = View.GONE
            btnNextImage.visibility = View.GONE
            ivDormImage.setImageResource(R.drawable.dorm_image_placeholder)
        }

        // Back button
        btnBack.setOnClickListener {
            onBackPressedDispatcher.onBackPressed()
        }

        // Message button — static, no action
        // btnMessage is display only

        // Reserve button — navigate to ReservationActivity
        btnReserve.setOnClickListener {
            val intent = Intent(this, ReservationActivity::class.java).apply {
                putExtra("DORM_NAME", dormName)
                putExtra("DORM_LOCATION", location)
                putExtra("DORM_PRICE", price)
                putExtra("DORM_DEPOSIT", deposit)
                putExtra("DORM_ADVANCE", advance)
            }
            startActivity(intent)
        }
    }

    private fun loadImage(imageView: ImageView, url: String) {
        Glide.with(this)
            .load(BASE_URL + url)
            .placeholder(R.drawable.dorm_image_placeholder)
            .error(R.drawable.dorm_image_placeholder)
            .centerCrop()
            .into(imageView)
    }

    private fun updateNavButtons(btnPrev: ImageButton, btnNext: ImageButton) {
        btnPrev.visibility = if (currentImageIndex > 0) View.VISIBLE else View.INVISIBLE
        btnNext.visibility = if (currentImageIndex < photoUrls.size - 1) View.VISIBLE else View.INVISIBLE
    }

    private fun updateIndicators(container: LinearLayout, total: Int, current: Int) {
        container.removeAllViews()
        for (i in 0 until total) {
            val dot = View(this)
            val size = (8 * resources.displayMetrics.density).toInt()
            val params = LinearLayout.LayoutParams(size, size)
            params.setMargins(4, 0, 4, 0)
            dot.layoutParams = params
            dot.setBackgroundResource(
                if (i == current) R.drawable.indicator_active else R.drawable.indicator_inactive
            )
            container.addView(dot)
        }
    }
}