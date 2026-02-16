package com.firstapp.dormease.network

import com.firstapp.dormease.model.LoginRequest
import com.firstapp.dormease.model.ApiResponse
import com.firstapp.dormease.model.SignupRequest
import retrofit2.Call
import retrofit2.http.Body
import retrofit2.http.POST

interface ApiService {

    @POST("auth/signup")
    fun signup(@Body request: SignupRequest): Call<ApiResponse>

    @POST("auth/login")
    fun login(@Body request: LoginRequest): Call<ApiResponse>
}
