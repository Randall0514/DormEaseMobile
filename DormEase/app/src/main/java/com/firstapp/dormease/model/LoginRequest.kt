package com.firstapp.dormease.model

data class LoginRequest(
    val identifier: String,
    val password: String,
    val platform: String
)
