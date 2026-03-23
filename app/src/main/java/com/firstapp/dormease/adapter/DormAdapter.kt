package com.firstapp.dormease.adapter

// FILE PATH: app/src/main/java/com/firstapp/dormease/adapter/DormAdapter.kt

import android.content.Context
import android.content.Intent
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.Button
import android.widget.CheckBox
import android.widget.ImageButton
import android.widget.ImageView
import android.widget.LinearLayout
import android.widget.TextView
import androidx.appcompat.app.AlertDialog
import androidx.recyclerview.widget.RecyclerView
import com.firstapp.dormease.DashboardActivity
import com.bumptech.glide.Glide
import com.firstapp.dormease.R
import com.firstapp.dormease.activity.DormDetailsActivity
import com.firstapp.dormease.activity.ReservationActivity
import com.firstapp.dormease.model.Dorm
import com.firstapp.dormease.network.Constants

class DormAdapter(private val dorms: List<Dorm>) :
    RecyclerView.Adapter<DormAdapter.DormViewHolder>() {

    private val currentImageIndex = mutableMapOf<Int, Int>()

    inner class DormViewHolder(itemView: View) : RecyclerView.ViewHolder(itemView) {
        val ivDormImage: ImageView         = itemView.findViewById(R.id.ivDormImage)
        val llImageIndicator: LinearLayout = itemView.findViewById(R.id.llImageIndicator)
        val btnPrevImage: ImageButton      = itemView.findViewById(R.id.btnPrevImage)
        val btnNextImage: ImageButton      = itemView.findViewById(R.id.btnNextImage)
        val tvDormName: TextView           = itemView.findViewById(R.id.tvDormName)
        val tvOwnerName: TextView          = itemView.findViewById(R.id.tvOwnerName)
        val tvPhoneNumber: TextView        = itemView.findViewById(R.id.tvPhoneNumber)
        val tvLocation: TextView           = itemView.findViewById(R.id.tvLocation)
        val tvPrice: TextView              = itemView.findViewById(R.id.tvPrice)
        val tvDeposit: TextView            = itemView.findViewById(R.id.tvDeposit)
        val tvAdvance: TextView            = itemView.findViewById(R.id.tvAdvance)
        val btnViewDetails: Button         = itemView.findViewById(R.id.btnViewDetails)
        val btnReserve: Button             = itemView.findViewById(R.id.btnReserve)
        val cbWater: CheckBox              = itemView.findViewById(R.id.cbWater)
        val cbElectricity: CheckBox        = itemView.findViewById(R.id.cbElectricity)
        val cbWifi: CheckBox               = itemView.findViewById(R.id.cbWifi)
        val cbBedFrame: CheckBox           = itemView.findViewById(R.id.cbBedFrame)
        val cbFoam: CheckBox               = itemView.findViewById(R.id.cbFoam)
        val cbKitchen: CheckBox            = itemView.findViewById(R.id.cbKitchen)
        val cbRestroom: CheckBox           = itemView.findViewById(R.id.cbRestroom)
        val cbNoCurfew: CheckBox           = itemView.findViewById(R.id.cbNoCurfew)
        val cbVisitors: CheckBox           = itemView.findViewById(R.id.cbVisitors)
    }

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): DormViewHolder {
        val view = LayoutInflater.from(parent.context)
            .inflate(R.layout.item_dorm, parent, false)
        return DormViewHolder(view)
    }

    override fun onBindViewHolder(holder: DormViewHolder, position: Int) {
        val dorm = dorms[position]
        if (!currentImageIndex.containsKey(position)) currentImageIndex[position] = 0

        // Use server-supplied occupied count directly
        val occupiedBeds  = dorm.occupiedCount
        val availableBeds = (dorm.roomCapacity - occupiedBeds).coerceAtLeast(0)
        val isFull        = availableBeds == 0

        holder.tvDormName.text    = dorm.dormName
        holder.tvOwnerName.text   = dorm.ownerName ?: "Unknown Owner"
        holder.tvPhoneNumber.text = "+63 ${dorm.phone}"
        holder.tvLocation.text    = dorm.address
        holder.tvPrice.text       = "₱ ${dorm.price}/month"
        holder.tvDeposit.text     = "Deposit: ₱${dorm.deposit ?: "N/A"}"
        holder.tvAdvance.text     = "Advance: ₱${dorm.advance ?: "N/A"}"

        holder.btnReserve.text = if (isFull) "Dorm Full" else "Reserve"

        // Utilities
        val u = dorm.utilities
        holder.cbWater.isChecked       = u.contains("water")
        holder.cbElectricity.isChecked = u.contains("electricity")
        holder.cbWifi.isChecked        = u.contains("wifi")
        holder.cbBedFrame.isChecked    = u.contains("bedFrame")
        holder.cbFoam.isChecked        = u.contains("foam")
        holder.cbKitchen.isChecked     = u.contains("kitchen")
        holder.cbRestroom.isChecked    = u.contains("restroom")
        holder.cbNoCurfew.isChecked    = u.contains("noCurfew")
        holder.cbVisitors.isChecked    = u.contains("visitorsAllowed")
        listOf(
            holder.cbWater, holder.cbElectricity, holder.cbWifi,
            holder.cbBedFrame, holder.cbFoam, holder.cbKitchen,
            holder.cbRestroom, holder.cbNoCurfew, holder.cbVisitors
        ).forEach { it.isEnabled = false }

        // Images
        if (!dorm.photoUrls.isNullOrEmpty()) {
            val currentIndex = currentImageIndex[position] ?: 0
            loadImage(holder, dorm.photoUrls, currentIndex)
            updateIndicators(holder, dorm.photoUrls.size, currentIndex)
            holder.btnPrevImage.visibility =
                if (currentIndex > 0) View.VISIBLE else View.INVISIBLE
            holder.btnNextImage.visibility =
                if (currentIndex < dorm.photoUrls.size - 1) View.VISIBLE else View.INVISIBLE
            holder.btnPrevImage.setOnClickListener {
                currentImageIndex[position] = (currentIndex - 1).coerceAtLeast(0)
                notifyItemChanged(position)
            }
            holder.btnNextImage.setOnClickListener {
                currentImageIndex[position] = (currentIndex + 1).coerceAtMost(dorm.photoUrls.size - 1)
                notifyItemChanged(position)
            }
        } else {
            holder.btnPrevImage.visibility = View.GONE
            holder.btnNextImage.visibility = View.GONE
            holder.ivDormImage.setImageResource(R.drawable.dorm_image_placeholder)
        }

        // View Details — passes real occupied count from server
        holder.btnViewDetails.setOnClickListener {
            val context = holder.itemView.context
            val intent = Intent(context, DormDetailsActivity::class.java).apply {
                putExtra("DORM_NAME",          dorm.dormName)
                putExtra("DORM_OWNER",         dorm.ownerName ?: "Unknown Owner")
                putExtra("DORM_OWNER_ID",      dorm.ownerId ?: 0)
                putExtra("DORM_PHONE",         dorm.phone)
                putExtra("DORM_LOCATION",      dorm.address)
                putExtra("DORM_PRICE",         dorm.price.toString())
                putExtra("DORM_DEPOSIT",       dorm.deposit?.toString() ?: "0")
                putExtra("DORM_ADVANCE",       dorm.advance?.toString() ?: "0")
                putExtra("DORM_ROOMS_LEFT",    dorm.roomCapacity)
                putExtra("DORM_OCCUPIED_BEDS", occupiedBeds)
                putStringArrayListExtra("DORM_UTILITIES",  ArrayList(dorm.utilities))
                putStringArrayListExtra("DORM_PHOTO_URLS", ArrayList(dorm.photoUrls ?: emptyList()))
            }
            context.startActivity(intent)
        }

        // Reserve
        holder.btnReserve.setOnClickListener {
            val context = holder.itemView.context

            if (availableBeds <= 0) {
                showDormFullDialog(context)
                return@setOnClickListener
            }

            val intent = Intent(context, ReservationActivity::class.java).apply {
                putExtra("DORM_NAME",      dorm.dormName)
                putExtra("DORM_OWNER_ID",  dorm.ownerId ?: 0)
                putExtra("DORM_LOCATION",  dorm.address)
                putExtra("DORM_PRICE",     dorm.price.toString())
                putExtra("DORM_DEPOSIT",   dorm.deposit?.toString() ?: "0")
                putExtra("DORM_ADVANCE",   dorm.advance?.toString() ?: "0")
            }
            context.startActivity(intent)
        }
    }

    private fun showDormFullDialog(context: Context) {
        AlertDialog.Builder(context)
            .setTitle("Dorm No Longer Accepting Tenants")
            .setMessage(
                "This dorm is no longer accepting a tenant. " +
                    "You will be taken back to available dorms so you can browse a different dorm."
            )
            .setCancelable(false)
            .setPositiveButton("Browse Dorms") { _, _ ->
                goToAvailableDorms(context)
            }
            .show()
    }

    private fun goToAvailableDorms(context: Context) {
        if (context is DashboardActivity) return

        val intent = Intent(context, DashboardActivity::class.java).apply {
            addFlags(Intent.FLAG_ACTIVITY_CLEAR_TOP or Intent.FLAG_ACTIVITY_SINGLE_TOP)
        }
        context.startActivity(intent)
    }

    private fun loadImage(holder: DormViewHolder, photoUrls: List<String>, index: Int) {
        Glide.with(holder.itemView.context)
            .load(Constants.SOCKET_URL + photoUrls[index])
            .placeholder(R.drawable.dorm_image_placeholder)
            .error(R.drawable.dorm_image_placeholder)
            .centerCrop()
            .into(holder.ivDormImage)
    }

    private fun updateIndicators(holder: DormViewHolder, totalImages: Int, currentIndex: Int) {
        holder.llImageIndicator.removeAllViews()
        for (i in 0 until totalImages) {
            val indicator = View(holder.itemView.context)
            val size = 8.dpToPx(holder.itemView.context)
            val params = LinearLayout.LayoutParams(size, size)
            params.setMargins(4.dpToPx(holder.itemView.context), 0, 4.dpToPx(holder.itemView.context), 0)
            indicator.layoutParams = params
            indicator.setBackgroundResource(
                if (i == currentIndex) R.drawable.indicator_active
                else R.drawable.indicator_inactive
            )
            holder.llImageIndicator.addView(indicator)
        }
    }

    override fun getItemCount(): Int = dorms.size

    private fun Int.dpToPx(context: android.content.Context): Int =
        (this * context.resources.displayMetrics.density).toInt()
}