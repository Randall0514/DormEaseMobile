package com.firstapp.dormease.model

data class ApiResponse(
    val message: String,
    val token: String? = null,
    val user: UserData? = null
)

data class UserData(
    val id: Int? = null,
    val fullName: String? = null,   // maps to fullName from backend
    val username: String? = null,
    val email: String? = null,
    val platform: String? = null
)