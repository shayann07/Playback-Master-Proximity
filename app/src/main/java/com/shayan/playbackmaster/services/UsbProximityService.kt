package com.shayan.playbackmaster.services

import android.app.PendingIntent
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
import com.shayan.playbackmaster.receivers.ProximityReceiver
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
            if (intent?.action == USB_PERMISSION_ACTION) {
                val granted = intent.getBooleanExtra(UsbManager.EXTRA_PERMISSION_GRANTED, false)
                val device = intent.getParcelableExtra<UsbDevice>(UsbManager.EXTRA_DEVICE)
                Log.d(TAG, "USB permission result: $granted for device: $device")

                if (granted && device != null) {
                    val usbManager = getSystemService(USB_SERVICE) as UsbManager
                    setupUsbConnection(device, usbManager)
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
            val usbDevice = deviceList.values.firstOrNull()
            if (usbDevice != null) {
                isConnected = true
                Log.d(TAG, "ESP device found: $usbDevice, isConnected set to $isConnected")
                requestUsbPermission(usbDevice)
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
        val driver = UsbSerialProber.getDefaultProber().probeDevice(device)
        if (driver != null) {
            val connection = usbManager.openDevice(driver.device)
            if (connection != null) {
                val port = driver.ports.first()
                try {
                    port.open(connection)
                    port.setParameters(
                        115200, 8, UsbSerialPort.STOPBITS_1, UsbSerialPort.PARITY_NONE
                    )
                    listenToProximitySignals(port)
                    isConnected = true
                    Log.d(TAG, "USB connection established. isConnected set to $isConnected")
                    sendBroadcast(Intent("ACTION_USB_CONNECTED"))
                } catch (e: Exception) {
                    Log.e(TAG, "Failed to open USB device: ${e.message}", e)
                    isConnected = false
                    broadcastSnackbar("Failed to open USB device.")
                }
            } else {
                Log.d(TAG, "Failed to open connection for USB device.")
            }
        } else {
            isConnected = false
            Log.d(TAG, "No suitable USB driver found for the device.")
            broadcastSnackbar("No suitable USB driver found.")
        }
    }

    private fun requestUsbPermission(device: UsbDevice) {
        val usbManager = getSystemService(USB_SERVICE) as UsbManager

        // Create an explicit Intent targeting ProximityReceiver
        val usbPermissionIntent = Intent(this, ProximityReceiver::class.java).apply {
            action = ProximityReceiver.USB_PERMISSION_ACTION
        }

        // Create a mutable PendingIntent with the explicit Intent
        val permissionPendingIntent = PendingIntent.getBroadcast(
            this,
            0,
            usbPermissionIntent,
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_MUTABLE
        )

        // Request USB permission using the explicit PendingIntent
        usbManager.requestPermission(device, permissionPendingIntent)
        Log.d(TAG, "Requesting USB permission for device: $device")
    }

    private fun listenToProximitySignals(port: UsbSerialPort) {
        Log.d(TAG, "Starting to listen for proximity signals.")
        Thread {
            try {
                val buffer = ByteArray(100)
                while (true) {
                    val len = port.read(buffer, 500)
                    if (len > 0) {
                        val signal = String(buffer, 0, len, Charsets.UTF_8).trim()
                        handleSignal(signal)
                    }
                }
            } catch (e: Exception) {
                Log.e(TAG, "Error in USB communication: ${e.message}", e)
                isConnected = false
                broadcastSnackbar("Error in USB communication.")
            } finally {
                isConnected = false
                port.close()
                Log.d(TAG, "Stopped listening for proximity signals. Port closed.")
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
        Log.d(TAG, "Broadcasting snackbar message: $message")
        sendBroadcast(Intent("ACTION_SHOW_SNACKBAR").apply { putExtra("MESSAGE", message) })
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