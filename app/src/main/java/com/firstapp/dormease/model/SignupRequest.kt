package com.firstapp.dormease.model

// FILE PATH: app/src/main/java/com/firstapp/dormease/model/SignupRequest.kt

import com.google.gson.annotations.SerializedName

data class SignupRequest(
    @SerializedName("fullName") val fullName : String,
    @SerializedName("username") val username : String,
    @SerializedName("email")    val email    : String,
    @SerializedName("password") val password : String,
    @SerializedName("otp")      val otp      : String,
    @SerializedName("phone")    val phone    : String = "",
    @SerializedName("platform") val platform : String = "mobile"
)