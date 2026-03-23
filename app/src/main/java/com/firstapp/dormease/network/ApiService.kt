package com.firstapp.dormease.network

// FILE PATH: app/src/main/java/com/firstapp/dormease/network/ApiService.kt

import com.firstapp.dormease.model.LoginRequest
import com.firstapp.dormease.model.ApiResponse
import com.firstapp.dormease.model.MessageHistoryItem
import com.firstapp.dormease.model.SignupRequest
import com.firstapp.dormease.model.ContactUser
import com.firstapp.dormease.model.Dorm
import com.firstapp.dormease.model.Reservation
import com.firstapp.dormease.model.ReservationResponse
import com.firstapp.dormease.model.TenantAction
import com.firstapp.dormease.model.TenantActionResponse
import com.firstapp.dormease.model.TenantReservation
import com.firstapp.dormease.model.SendMessageRequest
import okhttp3.ResponseBody
import retrofit2.Call
import retrofit2.Response
import retrofit2.http.Body
import retrofit2.http.DELETE
import retrofit2.http.GET
import retrofit2.http.PATCH
import retrofit2.http.POST
import retrofit2.http.Path
import retrofit2.http.Query

interface ApiService {

    // ── Auth ──────────────────────────────────────────────────────────────────

    @POST("auth/request-signup-otp")
    suspend fun requestSignupOtp(
        @Body body: Map<String, String>
    ): Response<ResponseBody>

    /** Forgot password — request an OTP to the given email */
    @POST("auth/forgot-password")
    suspend fun requestPasswordReset(
        @Body body: Map<String, String>
    ): Response<ResponseBody>

    /** Reset password — verify OTP and set new password */
    @POST("auth/reset-password")
    suspend fun resetPassword(
        @Body body: Map<String, String>
    ): Response<ResponseBody>

    @POST("auth/signup")
    fun signup(@Body request: SignupRequest): Call<ApiResponse>

    @POST("auth/login")
    fun login(@Body request: LoginRequest): Call<ApiResponse>

    // ── Profile updates — all call PATCH /auth/me ─────────────────────────────
    // Accepts any combination of: { fullName, username, email, password }

    /** Update full name and/or username — Personal Info screen */
    @PATCH("auth/me")
    suspend fun updateProfile(
        @Body body: Map<String, String>
    ): Response<ResponseBody>

    /** Change password: send { password: "newPassword" } */
    @PATCH("auth/me")
    suspend fun changePassword(
        @Body body: Map<String, String>
    ): Response<ResponseBody>

    /** Change email: send { email: "new@email.com" } */
    @PATCH("auth/me")
    suspend fun changeEmail(
        @Body body: Map<String, String>
    ): Response<ResponseBody>

    // ── Dorms ─────────────────────────────────────────────────────────────────

    @GET("dorms/available")
    fun getAvailableDorms(): Call<List<Dorm>>

    // ── Reservations ──────────────────────────────────────────────────────────

    @POST("reservations")
    suspend fun submitReservation(@Body reservation: Reservation): Response<ReservationResponse>

    @GET("reservations/tenant")
    suspend fun getTenantReservations(
        @Query("phone") phone: String
    ): Response<List<TenantReservation>>

    @GET("reservations/tenant/me")
    suspend fun getMyReservations(): Response<List<TenantReservation>>

    @PATCH("reservations/{id}/tenant-action")
    suspend fun sendTenantAction(
        @Path("id") reservationId: Int,
        @Body action: TenantAction
    ): Response<TenantActionResponse>

    /** Submit a termination appeal — phone-based auth, no Bearer token needed */
    @POST("reservations/{id}/appeal")
    suspend fun submitAppeal(
        @Path("id") reservationId: Int,
        @Body body: Map<String, String>
    ): Response<ResponseBody>

    // ── Messages ──────────────────────────────────────────────────────────────

    @GET("messages/contacts")
    fun getMessageContacts(): Call<List<ContactUser>>

    @GET("messages/{contactId}/history")
    suspend fun getMessageHistory(
        @Path("contactId") contactId: Int
    ): Response<List<MessageHistoryItem>>

    @DELETE("messages/{contactId}")
    suspend fun deleteConversation(
        @Path("contactId") contactId: Int
    ): Response<Void>

    @POST("messages/send")
    suspend fun sendMessage(
        @Body body: SendMessageRequest
    ): Response<ResponseBody>
}