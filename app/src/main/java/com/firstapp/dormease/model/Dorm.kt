package com.firstapp.dormease.model

data class Dorm(
    val id: String,
    val name: String,
    val ownerName: String,
    val phoneNumber: String,
    val location: String,
    val price: Double,
    val deposit: Double,
    val advance: Double,
    val utilities: List<String>,
    val imageUrls: List<String>
)