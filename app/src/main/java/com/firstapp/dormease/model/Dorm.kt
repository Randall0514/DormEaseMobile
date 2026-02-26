package com.firstapp.dormease.model

import com.google.gson.annotations.SerializedName

data class Dorm(
    val id: Int,
    @SerializedName("owner_id")
    val ownerId: Int?,                  // ← NEW: links dorm to its owner/admin
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
    val ownerName: String?
)