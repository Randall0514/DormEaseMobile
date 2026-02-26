package com.firstapp.dormease.model

data class Reservation(
    val dorm_name: String,
    val location: String,
    val dorm_owner_id: Int,          // ← links reservation to the correct admin
    val full_name: String,
    val phone: String,
    val move_in_date: String,
    val duration_months: Int,
    val price_per_month: Int,
    val deposit: Int,
    val advance: Int,
    val total_amount: Int,
    val notes: String,
    val payment_method: String = "cash_on_move_in"
)

data class ReservationResponse(
    val message: String,
    val reservation: ReservationData?
)

data class ReservationData(
    val id: Int,
    val dorm_name: String,
    val location: String,
    val dorm_owner_id: Int,
    val full_name: String,
    val phone: String,
    val move_in_date: String,
    val duration_months: Int,
    val price_per_month: Int,
    val deposit: Int,
    val advance: Int,
    val total_amount: Int,
    val notes: String,
    val payment_method: String,
    val status: String,
    val created_at: String
)