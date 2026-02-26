package com.firstapp.dormease.model

data class LoginResponse(
    val message: String,
    val token: String?,
    val user: User?
)

data class User(
    val id: Int,
    val username: String,
    val email: String,
    val platform: String,
    val fullName: String
)