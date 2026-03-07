package com.firstapp.dormease.model

// FILE PATH: app/src/main/java/com/firstapp/dormease/model/TenantReservation.kt

/**
 * Represents one reservation as returned by GET /reservations/tenant?phone=...
 * Used by the mobile app to poll for status changes and populate the Tenant Dashboard.
 */
data class TenantReservation(
    val id: Int,
    val dorm_name: String,
    val location: String?,
    val full_name: String,
    val phone: String,
    val move_in_date: String?,
    val duration_months: Int?,
    val price_per_month: Double?,
    val deposit: Double?,
    val advance: Double?,
    val total_amount: Double?,
    val notes: String?,
    val payment_method: String?,
    val status: String,                    // "pending" | "approved" | "rejected" | "archived"
    val rejection_reason: String?,         // non-null when status == "rejected"
    val termination_reason: String?,       // non-null when status == "archived"
    val created_at: String?,

    // ── Fields added for Tenant Dashboard ────────────────────────────────
    val tenant_action: String?,            // "accepted" | "cancelled" | null
    val cancel_reason: String?,
    val tenant_action_at: String?,
    val payments_paid: Int?,               // how many monthly payments have been marked paid
    val advance_used: Boolean?,            // true once advance has been applied to a payment
    val deposit_used: Boolean?,            // true once deposit has been applied to a payment
    val dorm_owner_id: Int?,               // owner's user id (for messaging)
    val owner_name: String?                // joined from users table
)