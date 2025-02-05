package com.shayan.playbackmaster.receivers

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.hardware.usb.UsbDevice
import android.hardware.usb.UsbManager
import android.util.Log
import com.shayan.playbackmaster.services.PlaybackService

class ProximityReceiver : BroadcastReceiver() {

    companion object {
        private const val TAG = "ProximityReceiver"
        private const val ACTION_PROXIMITY_DETECTED = "ACTION_PROXIMITY_DETECTED"
        private const val ACTION_PROXIMITY_LOST = "ACTION_PROXIMITY_LOST"
        const val USB_PERMISSION_ACTION = "com.shayan.playbackmaster.USB_PERMISSION"
    }

    override fun onReceive(context: Context, intent: Intent) {
        Log.d("ProximityReceiver", "Received intent: ${intent.action}")
        when (intent.action) {
            ACTION_PROXIMITY_DETECTED -> {
                Log.i(TAG, "Proximity detected. Starting PlaybackService...")
                context.startService(Intent(context, PlaybackService::class.java).apply {
                    action = "ACTION_PLAY_VIDEO"
                })
            }

            ACTION_PROXIMITY_LOST -> {
                Log.i(TAG, "Proximity lost. Stopping playback...")
                context.startService(Intent(context, PlaybackService::class.java).apply {
                    action = "ACTION_STOP_VIDEO"
                })
            }

            USB_PERMISSION_ACTION -> {
                // Handle USB permission result
                val granted = intent.getBooleanExtra(UsbManager.EXTRA_PERMISSION_GRANTED, false)
                val device = intent.getParcelableExtra<UsbDevice>(UsbManager.EXTRA_DEVICE)
                if (granted && device != null) {
                    Log.i(TAG, "USB permission granted for device: $device")
                    // You can now proceed to set up your USB connection or notify your service.
                } else {
                    Log.i(TAG, "USB permission denied or device is null.")
                    // Handle permission denial or error as needed.
                }
            }

            else -> {
                Log.w(TAG, "Received unexpected action: ${intent.action}")
            }
        }
    }

    /**
     * Starts the PlaybackService with the given action.
     *//* private fun startPlaybackService(context: Context, action: String) {
         try {
             val serviceIntent = Intent(context, PlaybackService::class.java).apply {
                 this.action = action
             }
             context.startService(serviceIntent)
             Log.d(TAG, "Service started successfully with action: $action")
         } catch (e: Exception) {
             Log.e(TAG, "Failed to start PlaybackService: ${e.message}", e)
         }
     }*/
}