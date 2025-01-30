package com.shayan.playbackmaster.receivers

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.util.Log
import com.shayan.playbackmaster.services.PlaybackService

class ProximityReceiver : BroadcastReceiver() {

    companion object {
        private const val TAG = "ProximityReceiver"
        private const val ACTION_PROXIMITY_DETECTED = "ACTION_PROXIMITY_DETECTED"
        private const val ACTION_PROXIMITY_LOST = "ACTION_PROXIMITY_LOST"
    }

    override fun onReceive(context: Context, intent: Intent) {
        when (intent.action) {
            ACTION_PROXIMITY_DETECTED -> {
                Log.i(TAG, "Proximity detected. Starting PlaybackService...")
                startPlaybackService(context, ACTION_PROXIMITY_DETECTED)
            }

            ACTION_PROXIMITY_LOST -> {
                Log.i(TAG, "Proximity lost. Stopping playback...")
                startPlaybackService(context, ACTION_PROXIMITY_LOST)
            }

            else -> {
                Log.w(TAG, "Received unexpected action: ${intent.action}")
            }
        }
    }

    /**
     * Starts the PlaybackService with the given action.
     */
    private fun startPlaybackService(context: Context, action: String) {
        try {
            val serviceIntent = Intent(context, PlaybackService::class.java).apply {
                this.action = action
            }
            context.startService(serviceIntent)
            Log.d(TAG, "Service started successfully with action: $action")
        } catch (e: Exception) {
            Log.e(TAG, "Failed to start PlaybackService: ${e.message}", e)
        }
    }
}