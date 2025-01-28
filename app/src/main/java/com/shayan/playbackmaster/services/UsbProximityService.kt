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
import androidx.annotation.RequiresApi
import com.hoho.android.usbserial.driver.UsbSerialPort
import com.hoho.android.usbserial.driver.UsbSerialProber
import com.shayan.playbackmaster.data.preferences.PreferencesHelper
import java.text.SimpleDateFormat
import java.util.*

class UsbProximityService : Service() {

    companion object {
        // Flag to indicate if the ESP is connected
        var isConnected: Boolean = false
    }

    private val usbReceiver = object : BroadcastReceiver() {
        override fun onReceive(context: Context?, intent: Intent?) {
            if (intent?.action == "com.shayan.playbackmaster.USB_PERMISSION") {
                val granted = intent.getBooleanExtra(UsbManager.EXTRA_PERMISSION_GRANTED, false)
                val device = intent.getParcelableExtra<UsbDevice>(UsbManager.EXTRA_DEVICE)
                if (granted && device != null) {
                    val usbManager = getSystemService(USB_SERVICE) as UsbManager
                    setupUsbConnection(device, usbManager)
                } else {
                    broadcastSnackbar("USB permission denied.")
                    isConnected = false
                }
            }
        }
    }

    @RequiresApi(Build.VERSION_CODES.TIRAMISU)
    override fun onCreate() {
        super.onCreate()
        val filter = IntentFilter("com.shayan.playbackmaster.USB_PERMISSION")
        registerReceiver(usbReceiver, filter, Context.RECEIVER_NOT_EXPORTED)
    }

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        checkEspConnection()
        return START_STICKY
    }

    private fun checkEspConnection() {
        val usbManager = getSystemService(USB_SERVICE) as UsbManager
        val deviceList = usbManager.deviceList

        if (deviceList.isEmpty()) {
            isConnected = false
            broadcastSnackbar("ESP is not connected to the mobile device.")
            sendErrorBroadcast("ESP connection error: No USB devices found.")
        } else {
            val usbDevice = deviceList.values.firstOrNull()
            if (usbDevice != null) {
                isConnected = true
                requestUsbPermission(usbDevice)
            } else {
                isConnected = false
                broadcastSnackbar("ESP is not connected to the mobile device.")
                sendErrorBroadcast("ESP connection error: No valid USB devices found.")
            }
        }
    }

    private fun setupUsbConnection(device: UsbDevice, usbManager: UsbManager) {
        val driver = UsbSerialProber.getDefaultProber().probeDevice(device)
        if (driver != null) {
            val connection = usbManager.openDevice(driver.device)
            if (connection != null) {
                val port = driver.ports.first()
                port.open(connection)
                port.setParameters(115200, 8, UsbSerialPort.STOPBITS_1, UsbSerialPort.PARITY_NONE)
                listenToProximitySignals(port)
            } else {
                isConnected = false
                broadcastSnackbar("Failed to open USB device.")
            }
        } else {
            isConnected = false
            broadcastSnackbar("No suitable USB driver found.")
        }
    }

    private fun requestUsbPermission(device: UsbDevice) {
        val usbManager = getSystemService(USB_SERVICE) as UsbManager
        val permissionIntent = PendingIntent.getBroadcast(
            this,
            0,
            Intent("com.shayan.playbackmaster.USB_PERMISSION"),
            PendingIntent.FLAG_IMMUTABLE
        )
        usbManager.requestPermission(device, permissionIntent)
    }

    private fun listenToProximitySignals(port: UsbSerialPort) {
        Thread {
            try {
                val buffer = ByteArray(100)
                while (true) {
                    val len = port.read(buffer, 1000)
                    if (len > 0) {
                        val signal = String(buffer, 0, len, Charsets.UTF_8).trim()
                        handleSignalWithinTimeFrame(signal)
                    }
                }
            } catch (e: Exception) {
                isConnected = false
                broadcastSnackbar("Error in USB communication.")
            } finally {
                isConnected = false
                port.close()
            }
        }.start()
    }

    private fun handleSignalWithinTimeFrame(signal: String) {
        val preferencesHelper = PreferencesHelper(this)
        val startTime = preferencesHelper.getStartTime()
        val endTime = preferencesHelper.getEndTime()

        if (isWithinScheduledTime(startTime, endTime)) {
            when (signal) {
                "1" -> sendBroadcast(Intent("ACTION_PROXIMITY_DETECTED"))
                "0" -> sendBroadcast(Intent("ACTION_PROXIMITY_LOST"))
                else -> broadcastSnackbar("Unknown signal received from the chip.")
            }
        }
    }

    private fun isWithinScheduledTime(startTime: String?, endTime: String?): Boolean {
        if (startTime == null || endTime == null) return false
        val currentTimeMillis = System.currentTimeMillis()
        val startMillis = convertTimeToMillis(startTime)
        val endMillis = convertTimeToMillis(endTime)
        return currentTimeMillis in startMillis..endMillis
    }

    private fun convertTimeToMillis(time: String): Long {
        val formatter = SimpleDateFormat("HH:mm", Locale.getDefault())
        val calendar = Calendar.getInstance()
        val (hour, minute) = time.split(":").map { it.toInt() }
        calendar.set(Calendar.HOUR_OF_DAY, hour)
        calendar.set(Calendar.MINUTE, minute)
        calendar.set(Calendar.SECOND, 0)
        return calendar.timeInMillis
    }

    private fun broadcastSnackbar(message: String) {
        val intent = Intent("ACTION_SHOW_SNACKBAR").apply {
            putExtra("MESSAGE", message)
        }
        sendBroadcast(intent)
    }

    private fun sendErrorBroadcast(error: String) {
        val intent = Intent("ACTION_USB_ERROR").apply {
            putExtra("ERROR_MESSAGE", error)
        }
        sendBroadcast(intent)
    }

    override fun onDestroy() {
        super.onDestroy()
        unregisterReceiver(usbReceiver)
    }

    override fun onBind(intent: Intent?): IBinder? = null
}