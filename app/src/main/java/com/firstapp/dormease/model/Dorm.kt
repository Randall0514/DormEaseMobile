package com.firstapp.dormease.model

// FILE PATH: app/src/main/java/com/firstapp/dormease/model/Dorm.kt

import com.google.gson.annotations.SerializedName

data class Dorm(
    val id: Int,
    @SerializedName("owner_id")
    val ownerId: Int?,
    @SerializedName("dorm_name")
    val dormName: String,
    val email: String,
    val phone: String,
    val price: String,
    val deposit: String?,
    val advance: String?,
    val address: String,
    @SerializedName("room_capacity")
    val roomCapacity: Int,
    val utilities: List<String>,
    @SerializedName("photo_urls")
    val photoUrls: List<String>?,
    @SerializedName("owner_name")
    val ownerName: String?,
    @SerializedName("occupied_count")
    val occupiedCount: Int = 0,   // ← NEW: approved+accepted tenants from server
)