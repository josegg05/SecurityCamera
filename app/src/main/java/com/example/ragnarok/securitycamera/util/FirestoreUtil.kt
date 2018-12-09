package com.example.ragnarok.securitycamera.util

import android.content.Context
import android.util.Log
import com.example.ragnarok.securitycamera.MainActivity
import com.example.ragnarok.securitycamera.model.*


//import com.example.ragnarok.securitycammobile.task.ContactSetupTask
import com.google.firebase.auth.FirebaseAuth
import com.google.firebase.firestore.DocumentReference

import com.google.firebase.firestore.FirebaseFirestore
import com.google.firebase.firestore.ListenerRegistration
import com.xwray.groupie.kotlinandroidextensions.Item
import java.lang.NullPointerException



object FirestoreUtil {
    private val firestoreInstance: FirebaseFirestore by lazy {FirebaseFirestore.getInstance()}

    private val currentDeviceDocRef: DocumentReference
        get() = firestoreInstance.document("devicesChannels/$firabaseID")

    private const val firabaseID:String = "SecuCam_012777"

    fun getOrCreateDeviceChannel(onComplete: () -> Unit){
        currentDeviceDocRef.get().addOnSuccessListener {documentSnapshot ->
            if (!documentSnapshot.exists()){
                val newDevice = Device(firabaseID,"","","",mutableListOf())
                currentDeviceDocRef.set(newDevice).addOnSuccessListener {
                    onComplete()
                }
            }
            else{
                onComplete()
            }
        }
    }


    fun sendMessage(message: Message) {
        currentDeviceDocRef
                .collection("messages")
                .add(message)
    }



    //region FCM
    fun getFCMRegistrationTokens(onComplete: (tokens: MutableList<String>) -> Unit) {
        currentDeviceDocRef.get().addOnSuccessListener {
            val device = it.toObject(Device::class.java)!!
            onComplete(device.registrationTokens)
        }
    }

    fun setFCMRegistrationTokens(registrationTokens: MutableList<String>) {
        currentDeviceDocRef.update(mapOf("registrationTokens" to registrationTokens))
    }
    //endregion FCM
}