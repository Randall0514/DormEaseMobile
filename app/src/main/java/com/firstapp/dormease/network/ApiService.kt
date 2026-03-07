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

    @POST("auth/signup")
    fun signup(@Body request: SignupRequest): Call<ApiResponse>

    @POST("auth/login")
    fun login(@Body request: LoginRequest): Call<ApiResponse>

    @GET("dorms/available")
    fun getAvailableDorms(): Call<List<Dorm>>

    // Submit reservation — will appear as notification on web dashboard
    @POST("reservations")
    suspend fun submitReservation(@Body reservation: Reservation): Response<ReservationResponse>

    // Poll for the current tenant's reservation status updates using their phone number
    @GET("reservations/tenant")
    suspend fun getTenantReservations(
        @Query("phone") phone: String
    ): Response<List<TenantReservation>>

    // ── NEW: fallback lookup using the auth token (no phone needed) ──────────
    // Used by TenantDashboardActivity when no phone is saved locally.
    // Calls GET /reservations/tenant/me — requires Bearer token in header
    // (AuthInterceptor adds it automatically).
    @GET("reservations/tenant/me")
    suspend fun getMyReservations(): Response<List<TenantReservation>>

    // Report back to the server when the tenant accepts or cancels
    @PATCH("reservations/{id}/tenant-action")
    suspend fun sendTenantAction(
        @Path("id") reservationId: Int,
        @Body action: TenantAction
    ): Response<TenantActionResponse>

    // Load messaging contacts — only users with approved+accepted reservations
    @GET("messages/contacts")
    fun getMessageContacts(): Call<List<ContactUser>>

    // Load full chat history with a specific contact (syncs with web)
    @GET("messages/{contactId}/history")
    suspend fun getMessageHistory(
        @Path("contactId") contactId: Int
    ): Response<List<MessageHistoryItem>>

    // Delete (soft-delete) an entire conversation with a contact
    @DELETE("messages/{contactId}")
    suspend fun deleteConversation(
        @Path("contactId") contactId: Int
    ): Response<Void>
}