package com.firstapp.dormease.model

// File path: app/src/main/java/com/firstapp/dormease/model/TenantAction.kt

/**
 * Request body sent to PATCH /reservations/{id}/tenant-action
 *
 * action        — "accepted" or "cancelled"
 * email         — tenant email (primary lookup)
 * phone         — optional legacy fallback lookup
 * cancel_reason — required when action == "cancelled", null otherwise
 */
data class TenantAction(
    val action: String,
    val email: String? = null,
    val phone: String? = null,
    val cancel_reason: String? = null
)

/**
 * Response body from PATCH /reservations/{id}/tenant-action
 */
data class TenantActionResponse(
    val message: String
)