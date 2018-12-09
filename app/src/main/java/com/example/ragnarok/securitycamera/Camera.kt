package com.example.ragnarok.securitycamera

import android.app.Activity
import android.graphics.*
import android.content.Context
import android.hardware.camera2.*
import android.media.ImageReader
import android.os.Handler
import android.util.Size
import android.util.Log
import java.util.*

class Camera (private val mImageAvailableListener: ImageReader.OnImageAvailableListener,
              private val mBackgroundHandler: Handler) {
    companion object {
        const val TAG = "TAG"
        const val MAX_IMAGES = 2
    }

    class CompareSizeByArea : Comparator<Size> {
        override fun compare(lhs: Size, rhs: Size): Int {
            return lhs.width * lhs.height - rhs.width * rhs.height
        }
    }

    enum class STATE {
        STATE_PREVIEW,
        STATE_PICTURE_TAKEN,
    }


    private var mCameraId: String? = null
    private var mImageReader: ImageReader? = null
    private var mCameraDevice: CameraDevice? = null
    private var mCaptureSession: CameraCaptureSession? = null
    private var mCaptureSessionForImage: CameraCaptureSession? = null
    private var mSupportedAEModes: IntArray = kotlin.IntArray(0)
    private var mState = STATE.STATE_PREVIEW

    private val mCaptureCallback = object : CameraCaptureSession.CaptureCallback() {
        private fun progress(result: CaptureResult, session: CameraCaptureSession) {
            when (mState) {
                STATE.STATE_PREVIEW -> {
                    // Nothing to do
                }
                STATE.STATE_PICTURE_TAKEN -> {
                    // session may not equal to mCaptureSessionForImage when take picture while
                    // preview. In this case, leave session as it is, it will close automatically
                    // because next call of createCaptureSession(...) will cause the previous
                    // session to close according to the API of CameraDevice:
                    // https://developer.android.com/reference/android/hardware/camera2/CameraDevice.html#createCaptureSession(java.util.List<android.view.Surface>, android.hardware.camera2.CameraCaptureSession.StateCallback, android.os.Handler)
                    if (mCaptureSessionForImage == session) {
                        Log.d(TAG, "Close take picture session: $session")
                        session.close()
                    }
                    mCaptureSession = null
                    // Reset to preview state
                    mState = STATE.STATE_PREVIEW
                }
            }
        }

        override fun onCaptureProgressed(session: CameraCaptureSession, request: CaptureRequest?, partialResult: CaptureResult) {
            progress(partialResult, session)
        }

        override fun onCaptureCompleted(session: CameraCaptureSession, request: CaptureRequest?, result: TotalCaptureResult) {
            progress(result, session)
        }
    }


    private val mStateCallback = object : CameraDevice.StateCallback() {
        override fun onOpened(camera: CameraDevice) {
            Log.d(TAG, "Camera opened")
            mCameraDevice = camera
        }

        override fun onDisconnected(camera: CameraDevice) {
            Log.d(TAG, "Camera disconnected")
            camera.close()
            mCameraDevice = null
        }

        override fun onError(camera: CameraDevice, error: Int) {
            Log.d(TAG, "Camera error: $error")
            camera.close()
            mCameraDevice = null
        }
    }

    /**
     * Should be called before call openCamera(context: Context).
     */
    fun setUpCameraOutputs(activity: Activity) {
        Log.d(TAG, "Begin setUpCameraOutputs")
        val manager = activity.getSystemService(Context.CAMERA_SERVICE) as CameraManager
        try {
            var camIds: Array<String> = emptyArray()
            try {
                camIds = manager.cameraIdList
            } catch (e: CameraAccessException) {
                Log.d(TAG, "Camera access exception getting IDs", e)
            }
            if (camIds.isEmpty()) {
                Log.d(TAG, "No cameras found")
                return
            }
            val id = camIds[0]

            val characteristics = manager.getCameraCharacteristics(id)
            val map = characteristics.get(CameraCharacteristics.SCALER_STREAM_CONFIGURATION_MAP)
            if (map == null) {
                Log.d(TAG, "Stream configuration map is null")
                return
            }

            val outputSizes = map.getOutputSizes(ImageFormat.JPEG).asList()

            val largest = Collections.max(outputSizes, CompareSizeByArea())

            val mwidth = largest.width / 4
            val mheight = largest.height / 4

            // Initialize image processor
            mImageReader = ImageReader.newInstance(mwidth, mheight, ImageFormat.JPEG, MAX_IMAGES)
            mImageReader!!.setOnImageAvailableListener(mImageAvailableListener, mBackgroundHandler)

            mSupportedAEModes = characteristics.get(CameraCharacteristics.CONTROL_AE_AVAILABLE_MODES)

            mCameraId = id
        } catch (ex: CameraAccessException) {
            ex.printStackTrace()
        }
    }

    fun openCamera(context: Context) {
        val manager = context.getSystemService(Context.CAMERA_SERVICE) as CameraManager
        // Open the camera resource
        try {
            Log.d(TAG, "Try open camera...")
            manager.openCamera(mCameraId, mStateCallback, mBackgroundHandler)
        } catch (ex: CameraAccessException) {
            Log.d(TAG, "Open camera error", ex)
        }
    }

    fun shutDown() {
        mImageReader?.close()
        mCaptureSession?.close()
        mCameraDevice?.close()
    }

    fun takePicture() {
        if (mCameraDevice == null || mImageReader == null) {
            Log.d(TAG, "Cannot capture image. Camera not initialized")
            return
        }
        mCaptureSession?.stopRepeating()
        mCaptureSession?.abortCaptures()
        try {
            mCameraDevice!!.createCaptureSession(Collections.singletonList(mImageReader!!.surface),
                    object : CameraCaptureSession.StateCallback() {
                        override fun onClosed(session: CameraCaptureSession?) {
                            Log.d(TAG, "Take picture session closed, session: $session")
                            mCaptureSessionForImage = null
                        }

                        override fun onConfigureFailed(session: CameraCaptureSession?) {
                            Log.d(TAG, "Failed to configure take picture session: $session")
                        }

                        override fun onConfigured(session: CameraCaptureSession?) {
                            if (mCameraDevice == null) {
                                return
                            }
                            mCaptureSession = session
                            mCaptureSessionForImage = session
                            Log.d(TAG, "Take picture session initialized, session: $session")
                            triggerImageCapture()
                        }
                    }, null)
        } catch (ex: CameraAccessException) {
            Log.d(TAG, "Access exception while preparing picture", ex)
        }
    }

    private fun triggerImageCapture() {
        try {
            val captureBuilder = mCameraDevice!!.createCaptureRequest(CameraDevice.TEMPLATE_STILL_CAPTURE)
            captureBuilder.addTarget(mImageReader!!.surface)
            if (CaptureRequest.CONTROL_AE_MODE_ON in mSupportedAEModes) {
                captureBuilder.set(CaptureRequest.CONTROL_AE_MODE, CaptureRequest.CONTROL_AE_MODE_ON)
            }
            mState = STATE.STATE_PICTURE_TAKEN
            Log.d(TAG, "Use session to capture picture, session: $mCaptureSession")
            mCaptureSession!!.capture(captureBuilder.build(), mCaptureCallback, null)
        } catch (ex: CameraAccessException) {
            Log.d(TAG, "Camera capture exception", ex)
        }
    }
}