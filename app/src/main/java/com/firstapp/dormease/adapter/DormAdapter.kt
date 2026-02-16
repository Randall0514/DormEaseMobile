package com.firstapp.dormease.adapter

import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.Button
import android.widget.CheckBox
import android.widget.ImageView
import android.widget.TextView
import androidx.recyclerview.widget.RecyclerView
import com.firstapp.dormease.R
import com.firstapp.dormease.model.Dorm

class DormAdapter(private val dorms: List<Dorm>) : RecyclerView.Adapter<DormAdapter.DormViewHolder>() {

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): DormViewHolder {
        val view = LayoutInflater.from(parent.context).inflate(R.layout.dorm_item_layout, parent, false)
        return DormViewHolder(view)
    }

    override fun onBindViewHolder(holder: DormViewHolder, position: Int) {
        val dorm = dorms[position]
        holder.bind(dorm)
    }

    override fun getItemCount(): Int = dorms.size

    class DormViewHolder(itemView: View) : RecyclerView.ViewHolder(itemView) {
        private val tvDormName: TextView = itemView.findViewById(R.id.tvDormName)
        private val tvOwnerName: TextView = itemView.findViewById(R.id.tvOwnerName)
        private val tvPhoneNumber: TextView = itemView.findViewById(R.id.tvPhoneNumber)
        private val tvLocation: TextView = itemView.findViewById(R.id.tvLocation)
        private val tvPrice: TextView = itemView.findViewById(R.id.tvPrice)
        private val tvDeposit: TextView = itemView.findViewById(R.id.tvDeposit)
        private val tvAdvance: TextView = itemView.findViewById(R.id.tvAdvance)
        private val cbWater: CheckBox = itemView.findViewById(R.id.cbWater)
        private val cbElectricity: CheckBox = itemView.findViewById(R.id.cbElectricity)
        private val cbGas: CheckBox = itemView.findViewById(R.id.cbGas)
        private val btnViewDetails: Button = itemView.findViewById(R.id.btnViewDetails)
        private val btnReserve: Button = itemView.findViewById(R.id.btnReserve)
        // Add ImageView for dorm image if you have one

        fun bind(dorm: Dorm) {
            tvDormName.text = dorm.name
            tvOwnerName.text = dorm.ownerName
            tvPhoneNumber.text = dorm.phoneNumber
            tvLocation.text = dorm.location
            tvPrice.text = "P ${dorm.price}/month"
            tvDeposit.text = "Deposit: P${dorm.deposit}"
            tvAdvance.text = "Advance: P${dorm.advance}"

            cbWater.isChecked = dorm.utilities.contains("Water")
            cbElectricity.isChecked = dorm.utilities.contains("Electricity")
            cbGas.isChecked = dorm.utilities.contains("Gas")

            // Set click listeners for buttons if needed
            btnViewDetails.setOnClickListener { /* Handle click */ }
            btnReserve.setOnClickListener { /* Handle click */ }
        }
    }
}