package com.shayan.playbackmaster.services

import android.app.Service
import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.content.IntentFilter
import android.hardware.usb.UsbDevice
import android.hardware.usb.UsbManager
import android.os.Build
import android.os.IBinder
import android.util.Log
import androidx.annotation.RequiresApi
import com.hoho.android.usbserial.driver.UsbSerialPort
import com.hoho.android.usbserial.driver.UsbSerialProber
import com.shayan.playbackmaster.data.preferences.PreferencesHelper
import java.io.IOException
import java.text.SimpleDateFormat
import java.util.*

class UsbProximityService : Service() {

    companion object {
        private const val TAG = "UsbProximityService"
        private const val USB_PERMISSION_ACTION = "com.shayan.playbackmaster.USB_PERMISSION"

        // Flag to indicate if the ESP is connected
        var isConnected: Boolean = false
    }

    private val usbReceiver = object : BroadcastReceiver() {
        override fun onReceive(context: Context?, intent: Intent?) {
            Log.d(TAG, "Received broadcast: ${intent?.action}")

            if (intent?.action == USB_PERMISSION_ACTION) {  // ✅ Now it's correctly placed
                val granted = intent.getBooleanExtra(UsbManager.EXTRA_PERMISSION_GRANTED, false)
                val device = intent.getParcelableExtra<UsbDevice>(UsbManager.EXTRA_DEVICE)
                Log.d(TAG, "USB permission result: $granted for device: $device")

                if (granted && device != null) {
                    val usbManager = getSystemService(USB_SERVICE) as UsbManager
                    if (usbManager.hasPermission(device)) {  // ✅ Correctly check before setup
                        setupUsbConnection(device, usbManager)
                    } else {
                        Log.e(TAG, "USB permission denied after request.")
                    }
                } else {
                    isConnected = false
                    Log.d(
                        TAG, "USB permission denied or no device. isConnected set to $isConnected"
                    )
                    broadcastSnackbar("USB permission denied.")
                }
            }
        }
    }

    @RequiresApi(Build.VERSION_CODES.TIRAMISU)
    override fun onCreate() {
        super.onCreate()
        Log.d(TAG, "Service Created. Registering USB Receiver...")

        registerReceiver(
            usbReceiver, IntentFilter(USB_PERMISSION_ACTION), Context.RECEIVER_NOT_EXPORTED
        )
        Log.d(TAG, "USB receiver registered.")
        checkEspConnection()
    }

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        Log.d(TAG, "onStartCommand called. Intent action: ${intent?.action}")
        checkEspConnection()
        return START_STICKY
    }

    private fun checkEspConnection() {
        val usbManager = getSystemService(USB_SERVICE) as UsbManager
        val deviceList = usbManager.deviceList
        Log.d(TAG, "Checking ESP connection. Device list size: ${deviceList.size}")

        if (deviceList.isEmpty()) {
            isConnected = false
            Log.d(TAG, "No devices connected. isConnected set to $isConnected")
            broadcastSnackbar("ESP disconnected.")
            sendErrorBroadcast("ESP connection lost.")
            sendBroadcast(Intent("ACTION_PROXIMITY_LOST"))
        } else {
            val usbDevice = deviceList.values.first()
            if (usbDevice != null) {
                isConnected = true
                setupUsbConnection(usbDevice, usbManager)
                Log.d(TAG, "ESP device found: $usbDevice, isConnected set to $isConnected")

            } else {
                isConnected = false
                Log.d(TAG, "No valid ESP device found. isConnected set to $isConnected")
                broadcastSnackbar("ESP is not connected to the mobile device.")
                sendErrorBroadcast("ESP connection error: No valid USB devices found.")
            }
        }
    }

    private fun setupUsbConnection(device: UsbDevice, usbManager: UsbManager) {
        Log.d(TAG, "Attempting to set up USB connection for device: $device")

        if (!usbManager.hasPermission(device)) {
            Log.e(TAG, "USB permission not granted for device: $device")
            return
        }

        val driver = UsbSerialProber.getDefaultProber().probeDevice(device)
        if (driver == null) {
            Log.e(TAG, "No driver found for USB device!")
            return
        }

        val connection = usbManager.openDevice(driver.device)
        if (connection == null) {
            Log.e(TAG, "UsbManager.openDevice() returned null!")
            return
        }

        val port = driver.ports.first()
        try {
            port.open(connection)
            Thread.sleep(100)

            try {
                port.purgeHwBuffers(true, true)
            } catch (e: UnsupportedOperationException) {
                Log.w(TAG, "purgeHwBuffers not supported on this device: ${e.message}")
            }

            port.setParameters(115200, 8, UsbSerialPort.STOPBITS_1, UsbSerialPort.PARITY_NONE)

            Log.d(TAG, "✅ USB Connection successful! Listening for data now...")
            listenToProximitySignals(port)
        } catch (e: Exception) {
            Log.e(TAG, "❌ Failed to open USB device: ${e.message}", e)
        }
    }


    private fun listenToProximitySignals(port: UsbSerialPort) {
        Log.d(TAG, "Listening for ESP32 Proximity Signals...")

        Thread {
            try {
                val buffer = ByteArray(100)
                while (isConnected) {
                    val len = port.read(buffer, 500)

                    // ✅ Detect disconnection
                    if (len < 0) {
                        Log.e(TAG, "❌ USB disconnected while reading. Exiting thread.")
                        break
                    }

                    if (len > 0) {
                        val signal = String(buffer, 0, len, Charsets.UTF_8).trim()
                        Log.d(TAG, "✅ Received Signal: '$signal'")

                        when (signal) {
                            "1" -> {
                                Log.d(TAG, "📡 Proximity Detected. Playing Video.")
                                sendBroadcast(Intent("ACTION_PROXIMITY_DETECTED"))
                            }

                            "0" -> {
                                Log.d(TAG, "🚫 Proximity Lost. Stopping Video.")
                                sendBroadcast(Intent("ACTION_PROXIMITY_LOST"))
                            }

                            else -> {
                                Log.w(TAG, "⚠️ Invalid signal received: '$signal'")
                            }
                        }
                    }
                }
            } catch (e: IOException) {
                Log.e(TAG, "⚠️ USB Read Error: ${e.message}. Device might be disconnected.", e)
            } finally {
                isConnected = false
                try {
                    port.close()
                    Log.d(TAG, "✅ Port closed after disconnection.")
                } catch (e: Exception) {
                    Log.e(TAG, "⚠️ Error closing USB port: ${e.message}", e)
                }
            }
        }.start()
    }

    private fun handleSignal(signal: String) {
        Log.d(TAG, "Received signal: $signal")
        val cleanedSignal = signal.trim().replace(Regex("[^01]"), "")
        Log.d(TAG, "Cleaned signal for processing: $cleanedSignal")

        val snackbarIntent = Intent("ACTION_SHOW_SNACKBAR")
        val preferencesHelper = PreferencesHelper(this)

        if (!isWithinScheduledTime(preferencesHelper) || !isConnected) {
            Log.d(TAG, "Signal received outside of scheduled time or when disconnected.")
            return
        }

        when (cleanedSignal) {
            "1" -> {
                sendBroadcast(Intent("ACTION_PROXIMITY_DETECTED"))
                snackbarIntent.putExtra("MESSAGE", "ESP Signal Received: 1 (Proximity Detected)")
                Log.d(TAG, "Proximity detected signal processed.")
            }

            "0" -> {
                sendBroadcast(Intent("ACTION_PROXIMITY_LOST"))
                snackbarIntent.putExtra("MESSAGE", "ESP Signal Received: 0 (Proximity Lost)")
                Log.d(TAG, "Proximity lost signal processed.")
            }

            else -> {
                snackbarIntent.putExtra("MESSAGE", "ESP Error: Invalid Signal - '$cleanedSignal'")
                Log.d(TAG, "Received invalid signal: $cleanedSignal")
            }
        }
        sendBroadcast(snackbarIntent)
    }

    private fun isWithinScheduledTime(preferencesHelper: PreferencesHelper): Boolean {
        val startTime = preferencesHelper.getStartTime() ?: return false
        val endTime = preferencesHelper.getEndTime() ?: return false

        val calendar = Calendar.getInstance()
        val currentHour = calendar.get(Calendar.HOUR_OF_DAY)
        val currentMinute = calendar.get(Calendar.MINUTE)

        val (startHour, startMinute) = startTime.split(":").map { it.toInt() }
        val (endHour, endMinute) = endTime.split(":").map { it.toInt() }

        return (currentHour > startHour || (currentHour == startHour && currentMinute >= startMinute)) && (currentHour < endHour || (currentHour == endHour && currentMinute <= endMinute))
    }

    private fun convertTimeToMillis(time: String): Long {
        return try {
            val formatter = SimpleDateFormat("HH:mm", Locale.getDefault())
            val (hour, minute) = time.split(":").map { it.toInt() }
            Calendar.getInstance().apply {
                set(Calendar.HOUR_OF_DAY, hour)
                set(Calendar.MINUTE, minute)
                set(Calendar.SECOND, 0)
            }.timeInMillis
        } catch (e: Exception) {
            Log.e(TAG, "Invalid time format: $time", e)
            0L
        }
    }

    private fun broadcastSnackbar(message: String) {
        if (!isConnected) {
            Log.d(TAG, "⚠️ Skipping snackbar broadcast because USB is disconnected.")
            return
        }
        Log.d(TAG, "📢 Broadcasting snackbar message: $message")
        sendBroadcast(Intent("ACTION_SHOW_SNACKBAR").apply {
            putExtra("MESSAGE", message)
        })
    }

    private fun sendErrorBroadcast(error: String) {
        Log.d(TAG, "Broadcasting error message: $error")
        sendBroadcast(Intent("ACTION_USB_ERROR").apply { putExtra("ERROR_MESSAGE", error) })
    }

    override fun onDestroy() {
        super.onDestroy()
        unregisterReceiver(usbReceiver)
        Log.d(TAG, "USB Receiver unregistered.")
    }

    override fun onBind(intent: Intent?): IBinder? = null
}