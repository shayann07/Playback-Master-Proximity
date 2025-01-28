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

class UsbProximityService : Service() {

    private val usbReceiver = object : BroadcastReceiver() {
        override fun onReceive(context: Context?, intent: Intent?) {
            if (intent?.action == "com.shayan.playbackmaster.USB_PERMISSION") {
                val granted = intent.getBooleanExtra(UsbManager.EXTRA_PERMISSION_GRANTED, false)
                val device = intent.getParcelableExtra<UsbDevice>(UsbManager.EXTRA_DEVICE)
                if (granted && device != null) {
                    val usbManager = getSystemService(USB_SERVICE) as UsbManager
                    setupUsbConnection(device, usbManager)
                } else {
                    showSnackbar("USB permission denied.")
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
            // No devices connected
            showSnackbar("ESP is not connected to the mobile device.")
            sendErrorBroadcast("ESP connection error: No USB devices found.")
        } else {
            val usbDevice = deviceList.values.firstOrNull()
            if (usbDevice != null) {
                requestUsbPermission(usbDevice)
            } else {
                showSnackbar("ESP is not connected to the mobile device.")
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
                port.setParameters(
                    115200, // Baud rate
                    8,      // Data bits
                    UsbSerialPort.STOPBITS_1, UsbSerialPort.PARITY_NONE
                )
                listenToProximitySignals(port)
            } else {
                showSnackbar("Failed to open USB device.")
            }
        } else {
            showSnackbar("No suitable USB driver found.")
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
                        handleSignal(signal)
                    }
                }
            } catch (e: Exception) {
                showSnackbar("Error in USB communication.")
            } finally {
                port.close()
            }
        }.start()
    }

    private fun handleSignal(signal: String) {
        when (signal) {
            "1" -> sendBroadcast(Intent("ACTION_PROXIMITY_DETECTED"))
            "0" -> sendBroadcast(Intent("ACTION_PROXIMITY_LOST"))
            else -> showSnackbar("Unknown signal received from the chip.")
        }
    }

    private fun showSnackbar(message: String) {
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