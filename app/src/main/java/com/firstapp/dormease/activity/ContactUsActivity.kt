package com.firstapp.dormease.activity

// FILE PATH: app/src/main/java/com/firstapp/dormease/activity/ContactUsActivity.kt

import android.content.ClipData
import android.content.ClipboardManager
import android.content.Context
import android.content.Intent
import android.net.Uri
import android.os.Bundle
import android.widget.ImageView
import android.widget.LinearLayout
import android.widget.TextView
import android.widget.Toast
import androidx.appcompat.app.AppCompatActivity
import com.firstapp.dormease.R

class ContactUsActivity : AppCompatActivity() {

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_contact_us)
        supportActionBar?.hide()

        findViewById<ImageView>(R.id.btnBack).setOnClickListener { finish() }

        // Email — tap to open mail app
        val emailRow = findViewById<LinearLayout>(R.id.rowEmail)
        val tvEmail  = findViewById<TextView>(R.id.tvEmail)
        tvEmail.text = "dormease.team@gmail.com"
        emailRow.setOnClickListener {
            val intent = Intent(Intent.ACTION_SENDTO).apply {
                data = Uri.parse("mailto:dormease.team@gmail.com")
            }
            if (intent.resolveActivity(packageManager) != null) {
                startActivity(intent)
            } else {
                copyToClipboard("Email", tvEmail.text.toString())
            }
        }

        // Phone — tap to dial
        val phoneRow = findViewById<LinearLayout>(R.id.rowPhone)
        val tvPhone  = findViewById<TextView>(R.id.tvPhone)
        tvPhone.text = "+63 977 084 0806"
        phoneRow.setOnClickListener {
            val intent = Intent(Intent.ACTION_DIAL).apply {
                data = Uri.parse("tel:+639770840806")
            }
            startActivity(intent)
        }

        // Facebook — tap to open browser
        val fbRow = findViewById<LinearLayout>(R.id.rowFacebook)
        val tvFb  = findViewById<TextView>(R.id.tvFacebook)
        tvFb.text = "https://www.facebook.com/share/17a5zP9xpF/"
        fbRow.setOnClickListener {
            val intent = Intent(Intent.ACTION_VIEW, Uri.parse("https://www.facebook.com/share/17a5zP9xpF/"))
            startActivity(intent)
        }

        // Address — tap to copy
        val addressRow = findViewById<LinearLayout>(R.id.rowAddress)
        val tvAddress  = findViewById<TextView>(R.id.tvAddress)
        tvAddress.text = "Arellano Street, Downtown District, Dagupan City, 2400 Pangasinan, Philippines"
        addressRow.setOnClickListener {
            copyToClipboard("Address", tvAddress.text.toString())
        }
    }

    private fun copyToClipboard(label: String, text: String) {
        val clipboard = getSystemService(Context.CLIPBOARD_SERVICE) as ClipboardManager
        clipboard.setPrimaryClip(ClipData.newPlainText(label, text))
        Toast.makeText(this, "$label copied to clipboard.", Toast.LENGTH_SHORT).show()
    }
}