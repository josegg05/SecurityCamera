package com.example.ragnarok.securitycamera

import android.app.Activity
import android.content.ContentValues.TAG
import android.os.Bundle
import android.util.Log
import com.google.android.things.pio.Gpio
import com.google.android.things.pio.GpioCallback
import com.google.android.things.pio.PeripheralManager
import java.io.IOException

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
private const val GPIO_MOTION: String = "BCM21"
private const val GPIO_FLASH: String = "BCM20"
private const val GPIO_LIGHT: String = "BCM16"
private const val INPUT_DIR: Boolean = false
private const val OUTPUT_DIR: Boolean = true

class MainActivity : Activity() {

    private var motionSensor: Gpio? = null
    private var flashActuator: Gpio? = null
    private var lightSensor: Gpio? = null

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
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
                // Pin is LOW
                if (!lightSensor!!.value) {
                    flashActuator!!.value = true
                }
            } else {
                // Pin is HIGH

            }

            // Continue listening for more interrupts
            return true
        }

        override fun onGpioError(gpio: Gpio, error: Int) {
            Log.w(TAG, "$gpio: Error event $error")
        }
    }
}
