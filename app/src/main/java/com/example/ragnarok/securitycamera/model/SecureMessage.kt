package com.example.ragnarok.securitycamera.model

import java.util.*

data class SecureMessage(val imagePath: String,
                        val size: String,
                        val tag: String,
                        override val time: Date,
                        override val senderId: String,
                        override val senderName: String,
                        override val type: String = MessageType.SECURE)
    :Message{
    constructor() : this("","", "", Date(0), "", "")
}