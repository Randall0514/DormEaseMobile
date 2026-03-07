package com.firstapp.dormease.model

// FILE PATH: app/src/main/java/com/firstapp/dormease/model/ContactUser.kt
// Matches the JSON shape returned by GET /messages/contacts

data class ContactUser(
    val id        : Int,
    val full_name : String?,
    val username  : String?,
    val email     : String?,
    val relation  : String?   // "tenant" or "owner"
)