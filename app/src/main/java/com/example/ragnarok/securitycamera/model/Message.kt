package com.example.ragnarok.securitycamera.model

import java.util.*

object MessageType {
    const val TEXT = "TEXT"
    const val IMAGE = "IMAGE"
    const val FILE = "FILE"
    const val AUDIO = "AUDIO"
    const val SECURE = "SECURE"
}

interface Message {
    val time: Date
    val senderId: String
    val senderName: String
    val type: String
}