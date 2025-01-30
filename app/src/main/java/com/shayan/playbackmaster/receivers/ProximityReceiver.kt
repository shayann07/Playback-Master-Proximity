package com.shayan.playbackmaster.receivers

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.util.Log
import com.shayan.playbackmaster.services.PlaybackService

class ProximityReceiver : BroadcastReceiver() {

    override fun onReceive(context: Context, intent: Intent) {
        when (intent.action) {
            "ACTION_PROXIMITY_DETECTED" -> {
                Log.d("ProximityReceiver", "Proximity detected. Triggering service action.")
                val serviceIntent = Intent(context, PlaybackService::class.java)
                serviceIntent.action = "ACTION_PROXIMITY_DETECTED"
                context.startService(serviceIntent)
            }

            "ACTION_PROXIMITY_LOST" -> {
                Log.d("ProximityReceiver", "Proximity lost. Triggering service action.")
                val serviceIntent = Intent(context, PlaybackService::class.java)
                serviceIntent.action = "ACTION_PROXIMITY_LOST"
                context.startService(serviceIntent)
            }
        }
    }
}