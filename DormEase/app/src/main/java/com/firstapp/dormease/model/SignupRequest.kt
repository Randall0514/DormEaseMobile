package com.firstapp.dormease.model

data class SignupRequest(
    val fullName: String,
    val username: String,
    val email: String,
    val password: String,
    val platform: String
)
