package com.firstapp.dormease.activity

// FILE PATH: app/src/main/java/com/firstapp/dormease/activity/DormDetailsActivity.kt

import android.content.Intent
import android.app.Dialog
import android.graphics.Color
import android.os.Bundle
import android.text.TextUtils
import android.view.Gravity
import android.view.View
import android.view.ViewGroup
import android.widget.CheckBox
import android.widget.FrameLayout
import android.widget.ImageButton
import android.widget.ImageView
import android.widget.LinearLayout
import android.widget.ProgressBar
import android.widget.TextView
import androidx.appcompat.app.AppCompatActivity
import com.bumptech.glide.Glide
import com.google.android.material.button.MaterialButton
import com.firstapp.dormease.ChatActivity
import com.firstapp.dormease.R
import com.firstapp.dormease.network.Constants

class DormDetailsActivity : AppCompatActivity() {

    private var photoUrls: ArrayList<String> = arrayListOf()
    private var currentImageIndex = 0

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_dorm_details)

        // ── Intent data ──────────────────────────────────────────────────────
        val dormName     = intent.getStringExtra("DORM_NAME")        ?: "N/A"
        val ownerName    = intent.getStringExtra("DORM_OWNER")       ?: "N/A"
        val ownerId      = intent.getIntExtra("DORM_OWNER_ID", -1)
        val phone        = intent.getStringExtra("DORM_PHONE")       ?: "N/A"
        val location     = intent.getStringExtra("DORM_LOCATION")    ?: "N/A"
        val price        = intent.getStringExtra("DORM_PRICE")       ?: "N/A"
        val deposit      = intent.getStringExtra("DORM_DEPOSIT")     ?: "N/A"
        val advance      = intent.getStringExtra("DORM_ADVANCE")     ?: "N/A"
        val totalBeds    = intent.getIntExtra("DORM_ROOMS_LEFT", 0)   // room_capacity
        val occupiedBeds = intent.getIntExtra("DORM_OCCUPIED_BEDS", 0)
        val utilities    = intent.getStringArrayListExtra("DORM_UTILITIES") ?: arrayListOf()
        photoUrls        = intent.getStringArrayListExtra("DORM_PHOTO_URLS") ?: arrayListOf()

        // ── Views ────────────────────────────────────────────────────────────
        val btnBack            = findViewById<ImageButton>(R.id.btnBack)
        val ivDormImage        = findViewById<ImageView>(R.id.ivDormImage)
        val btnPrevImage       = findViewById<ImageButton>(R.id.btnPrevImage)
        val btnNextImage       = findViewById<ImageButton>(R.id.btnNextImage)
        val llIndicator        = findViewById<LinearLayout>(R.id.llImageIndicator)
        val tvDormName         = findViewById<TextView>(R.id.tvDormName)
        val tvOwnerName        = findViewById<TextView>(R.id.tvOwnerName)
        val tvPhoneNumber      = findViewById<TextView>(R.id.tvPhoneNumber)
        val tvLocation         = findViewById<TextView>(R.id.tvLocation)
        val tvPrice            = findViewById<TextView>(R.id.tvPrice)
        val tvDeposit          = findViewById<TextView>(R.id.tvDeposit)
        val tvAdvance          = findViewById<TextView>(R.id.tvAdvance)
        val btnMessage         = findViewById<MaterialButton>(R.id.btnMessage)
        val btnReserve         = findViewById<MaterialButton>(R.id.btnReserve)

        // Capacity views
        val tvAvailabilityBadge = findViewById<TextView>(R.id.tvAvailabilityBadge)
        val tvOccupiedCount    = findViewById<TextView>(R.id.tvOccupiedCount)
        val tvAvailableCount   = findViewById<TextView>(R.id.tvAvailableCount)
        val tvTotalCount       = findViewById<TextView>(R.id.tvTotalCount)
        val tvOccupancyPercent = findViewById<TextView>(R.id.tvOccupancyPercent)
        val progressOccupancy  = findViewById<ProgressBar>(R.id.progressOccupancy)
        val llBedGrid          = findViewById<LinearLayout>(R.id.llBedGrid)

        // Utilities
        val cbWater       = findViewById<CheckBox>(R.id.cbWater)
        val cbElectricity = findViewById<CheckBox>(R.id.cbElectricity)
        val cbWifi        = findViewById<CheckBox>(R.id.cbWifi)
        val cbBedFrame    = findViewById<CheckBox>(R.id.cbBedFrame)
        val cbFoam        = findViewById<CheckBox>(R.id.cbFoam)
        val cbKitchen     = findViewById<CheckBox>(R.id.cbKitchen)
        val cbRestroom    = findViewById<CheckBox>(R.id.cbRestroom)
        val cbNoCurfew    = findViewById<CheckBox>(R.id.cbNoCurfew)
        val cbVisitors    = findViewById<CheckBox>(R.id.cbVisitors)

        // ── Populate basic info ───────────────────────────────────────────────
        tvDormName.text    = dormName
        tvOwnerName.text   = ownerName
        tvPhoneNumber.text = "+63 $phone"
        tvLocation.text    = location
        tvPrice.text       = "₱ $price/month"
        tvDeposit.text     = "Deposit: ₱$deposit"
        tvAdvance.text     = "Advance: ₱$advance"

        // ── Room Availability ─────────────────────────────────────────────────
        val availableBeds = (totalBeds - occupiedBeds).coerceAtLeast(0)
        val isFull        = availableBeds == 0

        tvOccupiedCount.text  = occupiedBeds.toString()
        tvAvailableCount.text = availableBeds.toString()
        tvTotalCount.text     = totalBeds.toString()

        val percent = if (totalBeds > 0) (occupiedBeds * 100 / totalBeds) else 0
        tvOccupancyPercent.text = "$percent%"
        progressOccupancy.progress = percent
        progressOccupancy.progressTintList = android.content.res.ColorStateList.valueOf(
            if (isFull) Color.parseColor("#E74C3C") else Color.parseColor("#2979FF")
        )

        if (isFull) {
            tvAvailabilityBadge.text = "Full"
            tvAvailabilityBadge.backgroundTintList =
                android.content.res.ColorStateList.valueOf(Color.parseColor("#E74C3C"))
        } else {
            tvAvailabilityBadge.text = "Available"
            tvAvailabilityBadge.backgroundTintList =
                android.content.res.ColorStateList.valueOf(Color.parseColor("#27AE60"))
        }

        buildBedGrid(llBedGrid, totalBeds, occupiedBeds)

        if (isFull) {
            btnReserve.isEnabled = false
            btnReserve.alpha = 0.75f
            btnReserve.text = "No Beds Available"
            btnReserve.isAllCaps = false
            btnReserve.icon = null
            btnReserve.maxLines = 2
            btnReserve.ellipsize = TextUtils.TruncateAt.END
        }

        // ── Message button — open ChatActivity with the dorm owner ────────────
        if (ownerId != -1) {
            btnMessage.isEnabled = true
            btnMessage.alpha = 1f
            btnMessage.setOnClickListener {
                val intent = Intent(this, ChatActivity::class.java).apply {
                    putExtra("EXTRA_RECIPIENT_ID",   ownerId)
                    putExtra("EXTRA_RECIPIENT_NAME", ownerName)
                }
                startActivity(intent)
            }
        } else {
            // Owner ID not available — hide the button gracefully
            btnMessage.isEnabled = false
            btnMessage.alpha = 0.4f
        }

        // ── Utilities ────────────────────────────────────────────────────────
        cbWater.isChecked       = utilities.contains("water")
        cbElectricity.isChecked = utilities.contains("electricity")
        cbWifi.isChecked        = utilities.contains("wifi")
        cbBedFrame.isChecked    = utilities.contains("bedFrame")
        cbFoam.isChecked        = utilities.contains("foam")
        cbKitchen.isChecked     = utilities.contains("kitchen")
        cbRestroom.isChecked    = utilities.contains("restroom")
        cbNoCurfew.isChecked    = utilities.contains("noCurfew")
        cbVisitors.isChecked    = utilities.contains("visitorsAllowed")

        listOf(cbWater, cbElectricity, cbWifi, cbBedFrame, cbFoam,
            cbKitchen, cbRestroom, cbNoCurfew, cbVisitors)
            .forEach { it.isEnabled = false }

        // ── Images ───────────────────────────────────────────────────────────
        if (photoUrls.isNotEmpty()) {
            loadImage(ivDormImage, photoUrls[currentImageIndex])
            updateIndicators(llIndicator, photoUrls.size, currentImageIndex)
            updateNavButtons(btnPrevImage, btnNextImage)
            ivDormImage.setOnClickListener {
                showExpandedImage(photoUrls[currentImageIndex])
            }

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

        // ── Back ─────────────────────────────────────────────────────────────
        btnBack.setOnClickListener { onBackPressedDispatcher.onBackPressed() }

        // ── Reserve ──────────────────────────────────────────────────────────
        btnReserve.setOnClickListener {
            val intent = Intent(this, com.firstapp.dormease.activity.ReservationActivity::class.java).apply {
                putExtra("DORM_NAME",     dormName)
                putExtra("DORM_OWNER_ID", ownerId)
                putExtra("DORM_LOCATION", location)
                putExtra("DORM_PRICE",    price)
                putExtra("DORM_DEPOSIT",  deposit)
                putExtra("DORM_ADVANCE",  advance)
            }
            startActivity(intent)
        }
    }

    private fun buildBedGrid(container: LinearLayout, total: Int, occupied: Int) {
        container.removeAllViews()
        if (total <= 0) return

        val dp  = resources.displayMetrics.density
        val slotSize  = (36 * dp).toInt()
        val margin    = (4 * dp).toInt()
        val maxSlots  = 20

        if (total <= maxSlots) {
            val cols = 5
            var row: LinearLayout? = null

            for (i in 0 until total) {
                if (i % cols == 0) {
                    row = LinearLayout(this).apply {
                        orientation = LinearLayout.HORIZONTAL
                        val rowParams = LinearLayout.LayoutParams(
                            LinearLayout.LayoutParams.WRAP_CONTENT,
                            LinearLayout.LayoutParams.WRAP_CONTENT
                        )
                        rowParams.bottomMargin = margin
                        layoutParams = rowParams
                    }
                    container.addView(row)
                }

                val isOccupied = i < occupied
                val slot = TextView(this).apply {
                    val p = LinearLayout.LayoutParams(slotSize, slotSize)
                    p.setMargins(margin, margin, margin, margin)
                    layoutParams = p
                    gravity = Gravity.CENTER
                    textSize = 8f
                    text = if (isOccupied) "✕" else "✓"
                    setTextColor(if (isOccupied) Color.WHITE else Color.parseColor("#27AE60"))
                    setBackgroundResource(
                        if (isOccupied) R.drawable.bed_occupied else R.drawable.bed_available
                    )
                }
                row?.addView(slot)
            }
        } else {
            val summary = TextView(this).apply {
                val available = (total - occupied).coerceAtLeast(0)
                text = "$occupied occupied  •  $available available  •  $total total beds"
                textSize = 13f
                setTextColor(Color.parseColor("#555555"))
            }
            container.addView(summary)
        }

        val legendLayout = LinearLayout(this).apply {
            orientation = LinearLayout.HORIZONTAL
            gravity = Gravity.CENTER_VERTICAL
            val p = LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.WRAP_CONTENT,
                LinearLayout.LayoutParams.WRAP_CONTENT
            )
            p.topMargin = (10 * resources.displayMetrics.density).toInt()
            layoutParams = p
        }

        val legendDot = { color: Int, label: String ->
            val dot = View(this).apply {
                val dp2 = resources.displayMetrics.density
                layoutParams = LinearLayout.LayoutParams((10 * dp2).toInt(), (10 * dp2).toInt()).also {
                    it.marginEnd = (4 * dp2).toInt()
                    it.marginStart = (8 * dp2).toInt()
                }
                setBackgroundColor(color)
            }
            val txt = TextView(this).apply {
                text = label
                textSize = 11f
                setTextColor(Color.parseColor("#888888"))
            }
            listOf(dot, txt)
        }

        legendDot(Color.parseColor("#E74C3C"), "Occupied").forEach { legendLayout.addView(it) }
        legendDot(Color.parseColor("#27AE60"), "Available").forEach { legendLayout.addView(it) }
        container.addView(legendLayout)
    }

    private fun loadImage(imageView: ImageView, url: String) {
        Glide.with(this)
            .load(Constants.SOCKET_URL + url)
            .placeholder(R.drawable.dorm_image_placeholder)
            .error(R.drawable.dorm_image_placeholder)
            .centerCrop()
            .into(imageView)
    }

    private fun showExpandedImage(url: String) {
        val dialog = Dialog(this, android.R.style.Theme_Black_NoTitleBar_Fullscreen)
        val root = FrameLayout(this).apply {
            layoutParams = FrameLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT,
                ViewGroup.LayoutParams.MATCH_PARENT
            )
            setBackgroundColor(Color.BLACK)
            setOnClickListener { dialog.dismiss() }
        }

        val fullImage = ImageView(this).apply {
            layoutParams = FrameLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT,
                ViewGroup.LayoutParams.MATCH_PARENT
            ).also { it.gravity = Gravity.CENTER }
            scaleType = ImageView.ScaleType.FIT_CENTER
            adjustViewBounds = true
            setOnClickListener { }
        }

        Glide.with(this)
            .load(Constants.SOCKET_URL + url)
            .placeholder(R.drawable.dorm_image_placeholder)
            .error(R.drawable.dorm_image_placeholder)
            .fitCenter()
            .into(fullImage)

        root.addView(fullImage)

        val closeButton = ImageButton(this).apply {
            layoutParams = FrameLayout.LayoutParams(
                ViewGroup.LayoutParams.WRAP_CONTENT,
                ViewGroup.LayoutParams.WRAP_CONTENT
            ).also {
                it.gravity = Gravity.TOP or Gravity.END
                val margin = (16 * resources.displayMetrics.density).toInt()
                it.setMargins(margin, margin, margin, margin)
            }
            setImageResource(android.R.drawable.ic_menu_close_clear_cancel)
            contentDescription = "Close image preview"
            background = null
            setColorFilter(Color.WHITE)
            setOnClickListener { dialog.dismiss() }
        }

        root.addView(closeButton)
        dialog.setContentView(root)
        dialog.show()
    }

    private fun updateNavButtons(btnPrev: ImageButton, btnNext: ImageButton) {
        btnPrev.visibility = if (currentImageIndex > 0) View.VISIBLE else View.INVISIBLE
        btnNext.visibility =
            if (currentImageIndex < photoUrls.size - 1) View.VISIBLE else View.INVISIBLE
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
                if (i == current) R.drawable.indicator_active
                else R.drawable.indicator_inactive
            )
            container.addView(dot)
        }
    }
}