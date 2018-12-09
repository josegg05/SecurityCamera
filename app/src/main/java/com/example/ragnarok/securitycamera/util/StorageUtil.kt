package com.example.ragnarok.securitycamera.util

import android.content.Context
import android.net.Uri
import android.widget.Toast
import com.google.firebase.storage.FirebaseStorage
import com.google.firebase.storage.StorageReference
import java.util.*

object StorageUtil {
    val storageInstance: FirebaseStorage by lazy { FirebaseStorage.getInstance() }
    private const val firabaseID:String = "SecuCam_012777"

    private val currentDeviceRef: StorageReference
        get() = storageInstance.reference
                .child("$firabaseID")


    fun uploadMessageImage(imageBytes: ByteArray,
                           context: Context,
                           onSuccess: (imagePath: String) -> Unit) {
        val ref = currentDeviceRef.child("${UUID.nameUUIDFromBytes(imageBytes)}")
        try {
        ref.putBytes(imageBytes)
            .addOnSuccessListener {
                onSuccess(ref.path)
            }.addOnProgressListener { taskSnapshot ->
                Toast.makeText(context, "Subiendo", Toast.LENGTH_SHORT).show()
            }
        }catch (e: Exception) {
            Toast.makeText(context, e.toString(), Toast.LENGTH_SHORT).show()
        }
    }


    fun pathToReference(path: String) = storageInstance.getReference(path)
}