package com.firstapp.dormease.model

/**
 * Represents one reservation as returned by GET /reservations/tenant?phone=...
 * Used by the mobile app to poll for status changes (approved / rejected).
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
    val status: String,               // "pending" | "approved" | "rejected"
    val rejection_reason: String?,    // non-null when status == "rejected"
    val created_at: String
)