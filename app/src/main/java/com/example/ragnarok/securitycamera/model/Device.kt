package com.example.ragnarok.securitycamera.model

data class Device (
    val id: String,
    val name: String,
    val place: String,
    val email: String,
    val registrationTokens: MutableList<String>) {
        constructor(): this("", "","","",mutableListOf())
}