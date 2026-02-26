package com.firstapp.dormease.network

import com.firstapp.dormease.model.LoginRequest
import com.firstapp.dormease.model.ApiResponse
import com.firstapp.dormease.model.SignupRequest
import com.firstapp.dormease.model.Dorm
import com.firstapp.dormease.model.Reservation
import com.firstapp.dormease.model.ReservationResponse
import retrofit2.Call
import retrofit2.Response
import retrofit2.http.Body
import retrofit2.http.GET
import retrofit2.http.POST

interface ApiService {

    @POST("auth/signup")
    fun signup(@Body request: SignupRequest): Call<ApiResponse>

    @POST("auth/login")
    fun login(@Body request: LoginRequest): Call<ApiResponse>

    @GET("dorms/available")
    fun getAvailableDorms(): Call<List<Dorm>>

    // NEW: Submit reservation — will appear as notification on web dashboard
    @POST("reservations")
    suspend fun submitReservation(@Body reservation: Reservation): Response<ReservationResponse>
}