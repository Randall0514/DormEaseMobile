package com.firstapp.dormease.model

/**
 * Represents one reservation as returned by GET /reservations/tenant?phone=...
 * Used by the mobile app to poll for status changes (approved / rejected / terminated).
 */
data class TenantReservation(
    val id: Int,
    val dorm_name: String,
    val location: String?,
    val full_name: String,
    val phone: String,
    val move_in_date: String,
    val duration_months: Int,
    val price_per_month: Double,
    val deposit: Double,
    val advance: Double,
    val total_amount: Double,
    val notes: String?,
    val payment_method: String,
    val status: String,                    // "pending" | "approved" | "rejected" | "terminated"
    val rejection_reason: String?,         // non-null when status == "rejected"
    val termination_reason: String?,       // non-null when status == "terminated"
    val created_at: String
)