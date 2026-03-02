package com.firstapp.dormease.model

// File path: app/src/main/java/com/firstapp/dormease/model/TenantAction.kt

/**
 * Request body sent to PATCH /reservations/{id}/tenant-action
 *
 * action        — "accepted" or "cancelled"
 * phone         — the tenant's phone (used server-side to verify ownership)
 * cancel_reason — required when action == "cancelled", null otherwise
 */
data class TenantAction(
    val action: String,
    val phone: String,
    val cancel_reason: String? = null
)

/**
 * Response body from PATCH /reservations/{id}/tenant-action
 */
data class TenantActionResponse(
    val message: String
)