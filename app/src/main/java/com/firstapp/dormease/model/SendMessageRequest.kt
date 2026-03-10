package com.firstapp.dormease.model

data class SendMessageRequest(
    val recipientId : Int,
    val message     : String
)