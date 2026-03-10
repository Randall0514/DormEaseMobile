package com.firstapp.dormease.model

// FILE PATH: app/src/main/java/com/firstapp/dormease/model/TenantReservation.kt

data class TenantReservation(
    val id                 : Int,
    val dorm_name          : String,
    val location           : String?,
    val full_name          : String,
    val phone              : String,
    val move_in_date       : String?,
    val duration_months    : Int?,
    val price_per_month    : String?,
    val deposit            : String?,
    val advance            : String?,
    val total_amount       : String?,
    val notes              : String?,
    val payment_method     : String?,
    val status             : String,
    val created_at         : String?,
    val tenant_action      : String?,
    val tenant_action_at   : String?,
    val cancel_reason      : String?,
    val rejection_reason   : String?,
    val termination_reason : String?,
    val room_capacity      : Int?,
    val dorm_id            : Int?,
    val payments_paid      : Int?,
    val advance_used       : Boolean?,
    val deposit_used       : Boolean?,
    val owner_name         : String?,
    val dorm_owner_id      : Int?,
    val tenant_email       : String?
)