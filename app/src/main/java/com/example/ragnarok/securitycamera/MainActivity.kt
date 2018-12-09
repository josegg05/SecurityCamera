package com.example.ragnarok.securitycamera

import android.app.Activity
import android.content.BroadcastReceiver
import android.content.ContentValues.TAG
import android.content.Context
import android.content.Intent
import android.content.IntentFilter
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.graphics.Matrix
import android.media.ImageReader
import android.os.Bundle
import android.os.Handler
import android.os.HandlerThread
import android.support.v4.content.LocalBroadcastManager
import android.util.Log
import android.widget.Toast
import com.example.ragnarok.securitycamera.model.SecureMessage
import com.example.ragnarok.securitycamera.util.FirestoreUtil
import com.example.ragnarok.securitycamera.util.StorageUtil
import com.google.android.gms.nearby.Nearby
import com.google.android.gms.nearby.connection.*
import com.google.android.things.pio.Gpio
import com.google.android.things.pio.GpioCallback
import com.google.android.things.pio.PeripheralManager
import com.google.firebase.firestore.FirebaseFirestore
import com.google.firebase.messaging.FirebaseMessaging
import com.google.firebase.storage.FirebaseStorage
import kotlinx.android.synthetic.main.activity_main.*
import kotlinx.coroutines.experimental.delay
import org.jetbrains.anko.toast
import java.io.ByteArrayOutputStream
import java.io.File
import java.io.IOException
import java.util.*
import kotlin.concurrent.timerTask

/**
 * Skeleton of an Android Things activity.
 *
 * Android Things peripheral APIs are accessible through the class
 * PeripheralManagerService. For example, the snippet below will open a GPIO pin and
 * set it to HIGH:
 *
 * <pre>{@code
 * val service = PeripheralManagerService()
 * val mLedGpio = service.openGpio("BCM6")
 * mLedGpio.setDirection(Gpio.DIRECTION_OUT_INITIALLY_LOW)
 * mLedGpio.value = true
 * }</pre>
 * <p>
 * For more complex peripherals, look for an existing user-space driver, or implement one if none
 * is available.
 *
 * @see <a href="https://github.com/androidthings/contrib-drivers#readme">https://github.com/androidthings/contrib-drivers#readme</a>
 *
 */

// GPIO Pin Name
private const val GPIO_FLASH: String = "BCM21"
private const val GPIO_MOTION: String = "BCM20"
private const val GPIO_LIGHT: String = "BCM16"
private const val INPUT_DIR: Boolean = false
private const val OUTPUT_DIR: Boolean = true
private const val firabaseID:String = "SecuCam_012777"
private const val deviceName:String = "SecuCam_012777"

class MainActivity : Activity() {

    private lateinit var mCameraHandler: Handler
    private lateinit var mCameraThread: HandlerThread

    private lateinit var mCamera: Camera

    private val storage = FirebaseStorage.getInstance()
    private val firestore = FirebaseFirestore.getInstance()
    private lateinit var connectionsClient: ConnectionsClient
    var choice: Int? = 1
    private lateinit var endpointID: String

    private var motionSensor: Gpio? = null
    private var flashActuator: Gpio? = null
    private var lightSensor: Gpio? = null

    private var validChannelID: Boolean = false

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_main)


        FirestoreUtil.getOrCreateDeviceChannel{
            validChannelID = true
        }

        pictureBtn.setOnClickListener {
            mCamera.takePicture()
        }

        connectionsClient = Nearby.getConnectionsClient(this)
        startAdvertising()
        FirebaseMessaging.getInstance().subscribeToTopic("rpi")


        // Attempt to access the GPIO
        motionSensor = try {
            PeripheralManager.getInstance()
                .openGpio(GPIO_MOTION)
        } catch (e: IOException) {
            Log.w(TAG, "Unable to access GPIO", e)
            null
        }

        flashActuator = try {
            PeripheralManager.getInstance()
                .openGpio(GPIO_FLASH)
        } catch (e: IOException) {
            Log.w(TAG, "Unable to access GPIO", e)
            null
        }

        lightSensor = try {
            PeripheralManager.getInstance()
                .openGpio(GPIO_LIGHT)
        } catch (e: IOException) {
            Log.w(TAG, "Unable to access GPIO", e)
            null
        }

        configureInput(motionSensor!!, INPUT_DIR, true)
        configureInput(flashActuator!!, OUTPUT_DIR, false)
        configureInput(lightSensor!!, INPUT_DIR, false)
    }

    override fun onDestroy() {
        super.onDestroy()

        try {
            motionSensor?.close()
            flashActuator?.close()
            lightSensor?.close()

            motionSensor = null
            flashActuator = null
            lightSensor = null
        } catch (e: IOException) {
            Log.w(TAG, "Unable to close GPIO", e)
        }
    }


    override fun onStart() {
        super.onStart()

        // Begin listening for interrupt events
        motionSensor?.registerGpioCallback(motionSensorCallback)
    }

    override fun onResume() {
        super.onResume()
        startBackgroundThread()
        mCamera = Camera(mOnImageAvailableListener, mCameraHandler)
        mCamera.setUpCameraOutputs(this)
        mCamera.openCamera(this)
        LocalBroadcastManager.getInstance(this)
            .registerReceiver(mMessageReceiver,
                IntentFilter("message")
            );
    }

    override fun onPause() {
        super.onPause()

        mCamera.shutDown()
        stopBackgroudThread()
    }

    override fun onStop() {
        super.onStop()
        // Interrupt events no longer necessary
        motionSensor?.unregisterGpioCallback(motionSensorCallback)
    }

    @Throws(IOException::class)
    fun configureInput(gpio: Gpio, dir: Boolean, call: Boolean) {
        gpio.apply {
            if (dir == INPUT_DIR){
                // Initialize the pin as an input
                setDirection(Gpio.DIRECTION_IN)
                // Low voltage is considered active
                setActiveType(Gpio.ACTIVE_LOW)
            } else {
                // Initialize the pin as an input
                setDirection(Gpio.DIRECTION_OUT_INITIALLY_LOW)
            }

            if (call) {
                // Register for all state changes
                setEdgeTriggerType(Gpio.EDGE_BOTH)
                registerGpioCallback(motionSensorCallback)
            }
        }
    }

    private val motionSensorCallback = object : GpioCallback {
        override fun onGpioEdge(gpio: Gpio): Boolean {
            // Read the active low pin state
            if (gpio.value) {
                toast("ya no paso")
                flashActuator!!.value = false
            } else {
                // Pin is HIGH
                toast("paso alguien")
                // Pin is LOW
                if (!lightSensor!!.value) {
                    flashActuator!!.value = true
                }
                if (validChannelID){}
                var i = 0
                 while (i<=100)
                     i = i+1
                mCamera.takePicture()

            }

            // Continue listening for more interrupts
            return true
        }

        override fun onGpioError(gpio: Gpio, error: Int) {
            Log.w(TAG, "$gpio: Error event $error")
        }
    }

    private fun rotateImage(img: Bitmap, degree: Float): Bitmap {
        val matrix = Matrix()
        matrix.postRotate(degree)
        return Bitmap.createBitmap(img, 0, 0, img.width, img.height, matrix, true)
    }

    private val mOnImageAvailableListener = ImageReader.OnImageAvailableListener { reader ->
        val image = reader.acquireLatestImage()
        val imageBuf = image.planes[0].buffer
        val imageBytes = ByteArray(imageBuf.remaining())
        imageBuf.get(imageBytes)
        image.close()
        val bitmap = BitmapFactory.decodeByteArray(imageBytes, 0, imageBytes.size)
        val rotated = rotateImage(bitmap, 180.0f)
        onPictureTaken(rotated)
    }
    private fun sendPayload(file: File) {

        val payload = Payload.fromFile(file)
        //connectionsClient.sendPayload(
        //    endpointID, payload)
    }

    // Method to save an image to gallery and return uri
    private fun saveImage(bitmap: Bitmap, title:String): File {

        val file = File(this.filesDir, title)
        val stream = ByteArrayOutputStream()
        bitmap.compress(Bitmap.CompressFormat.JPEG, 100, stream)
        file.writeBytes(stream.toByteArray())

        // Parse the gallery image url to uri
        return file
    }


    private fun onPictureTaken(bitmap: Bitmap) {
        if (choice == 0) {
            val file = saveImage(bitmap, "image01")
            sendPayload(file)
        } else {
            uploadImage(bitmap)
        }
        // Process the captured image...

        runOnUiThread {
            imageView.setImageBitmap(bitmap)
        }
    }

    //TODO: Cambiar para enviar msj tipo Secure Message
    private fun uploadImage(bitmap: Bitmap) {
        /*
        val stream = ByteArrayOutputStream()
        bitmap.compress(Bitmap.CompressFormat.JPEG, 100, stream)
        val uuid = UUID.randomUUID()
        val ref = "images/%s.jpg".format(uuid.toString())
        val obj = mutableMapOf<String, Any>()
        obj["ref"] = ref
        val storageRef = storage.reference
        val imageRef = storageRef.child(ref)
        val uploadTask = imageRef.putBytes(stream.toByteArray())
        uploadTask.addOnFailureListener {
            uploadImage(bitmap)
        }
        uploadTask.addOnSuccessListener{
            imageRef.downloadUrl.addOnSuccessListener {uri ->
                obj["downloadURL"] = uri.toString()
                firestore.collection("images").add(obj)
            }
        }*/
        val outputStream = ByteArrayOutputStream()

        bitmap.compress(Bitmap.CompressFormat.JPEG, 90, outputStream)
        val selectedImageBytes = outputStream.toByteArray()
        val filesize = selectedImageBytes.size/1000

        StorageUtil.uploadMessageImage(selectedImageBytes, this) { imagePath ->
            Toast.makeText(this@MainActivity, "Foto Enviada", Toast.LENGTH_SHORT).show()
            val messageToSend =
                SecureMessage(imagePath, filesize.toString(), "person", Calendar.getInstance().time,
                    firabaseID, deviceName)
            FirestoreUtil.sendMessage(messageToSend)
        }
    }

    private fun startAdvertising() {
        val options = AdvertisingOptions.Builder()
            .setStrategy(Strategy.P2P_CLUSTER)
            .build()
        //val options = AdvertisingOptions(Strategy.P2P_CLUSTER)
        connectionsClient.startAdvertising(
            "rpiRolf",
            "rolf",
            mConnectionLifecycleCallback,
            options)
            .addOnSuccessListener{
                text1.text = "Advertising"
            }
            .addOnFailureListener{
                text1.text = it.message
            }
    }

    // Callbacks for receiving payloads
    private val mPayloadCallback = object : PayloadCallback() {
        override fun onPayloadReceived(endpointId: String, payload: Payload) {
            choice = payload.asBytes()?.get(0)?.toInt()
            if (choice == 0 || choice == 1) {
                mCamera.takePicture()
            }
        }

        override fun onPayloadTransferUpdate(endpointId: String, update: PayloadTransferUpdate) {
            /*if (update.status == Status.SUCCESS) {

            }*/
        }
    }

    private val mConnectionLifecycleCallback = object : ConnectionLifecycleCallback() {

        override fun onConnectionInitiated(endpointId: String, connectionInfo: ConnectionInfo) {
            // Automatically accept the connection on both sides.
            endpointID = endpointId
            connectionsClient.acceptConnection(endpointId, mPayloadCallback)
        }

        override fun onConnectionResult(endpointId: String, result: ConnectionResolution) {
            when (result.status.statusCode) {
                ConnectionsStatusCodes.STATUS_OK -> {
                }
                ConnectionsStatusCodes.STATUS_CONNECTION_REJECTED -> {
                }
                ConnectionsStatusCodes.STATUS_ERROR -> {
                }
            }// We're connected! Can now start sending and receiving data.
            // The connection was rejected by one or both sides.
            // The connection broke before it was able to be accepted.
        }

        override fun onDisconnected(endpointId: String) {
            // We've been disconnected from this endpoint. No more data can be
            // sent or received.
        }
    }


    private fun startBackgroundThread() {
        mCameraThread = HandlerThread("CameraBackground")
        mCameraThread.start()
        mCameraHandler = Handler(mCameraThread.looper)
    }

    private fun stopBackgroudThread() {
        mCameraThread.quitSafely()
        try {
            mCameraThread.join()
        } catch (ex: InterruptedException) {
            ex.printStackTrace()
        }
    }

    private val mMessageReceiver = object : BroadcastReceiver() {
        override fun onReceive(context: Context, intent: Intent) {
            // Extract data included in the Intent
            choice = 1
            mCamera.takePicture()
        }
    }
}
