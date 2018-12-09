package com.example.ragnarok.securitycamera

import android.content.Intent
import com.google.firebase.messaging.FirebaseMessagingService
import com.google.firebase.messaging.RemoteMessage
import android.support.v4.content.LocalBroadcastManager



class MessageService: FirebaseMessagingService() {

    override public fun onMessageReceived(remoteMessage: RemoteMessage) {
        sendMessage()
    }

    private fun sendMessage() {
        // The string "my-integer" will be used to filer the intent
        val intent = Intent("message")
        // Adding some data
        intent.putExtra("message", 1)
        LocalBroadcastManager.getInstance(this).sendBroadcast(intent)
    }
}