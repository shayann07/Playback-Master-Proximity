package com.shayan.playbackmaster.receivers

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.os.Build
import android.util.Log
import androidx.annotation.RequiresApi
import com.shayan.playbackmaster.data.preferences.PreferencesHelper
import com.shayan.playbackmaster.utils.AlarmUtils

class BootReceiver : BroadcastReceiver() {

    companion object {
        private const val TAG = "BootReceiver"
    }

    @RequiresApi(Build.VERSION_CODES.M)
    override fun onReceive(context: Context, intent: Intent?) {
        // Check if the received intent action is BOOT_COMPLETED
        if (intent?.action == Intent.ACTION_BOOT_COMPLETED) {
            Log.i(TAG, "Device boot completed. Checking preferences for alarm rescheduling.")

            val preferences = PreferencesHelper(context)

            // Retrieve preferences for video playback
            val videoUri = preferences.getVideoUri()
            val startTime = preferences.getStartTime()
            val endTime = preferences.getEndTime()

            if (videoUri.isNullOrEmpty()) {
                Log.w(TAG, "Video URI is empty or null. Alarm scheduling skipped.")
                return
            }

            if (startTime.isNullOrEmpty()) {
                Log.w(TAG, "Start time is empty or null. Alarm scheduling skipped.")
                return
            }

            if (endTime.isNullOrEmpty()) {
                Log.w(TAG, "End time is empty or null. Alarm scheduling skipped.")
                return
            }

            try {
                // Schedule the daily alarm if all required data is present
                AlarmUtils.scheduleDailyAlarm(context, videoUri, startTime, endTime)
                Log.i(
                    TAG,
                    "Alarm successfully scheduled with URI: $videoUri, Start Time: $startTime, End Time: $endTime"
                )
            } catch (e: Exception) {
                Log.e(TAG, "Failed to schedule alarm. Error: ${e.message}", e)
            }
        } else {
            Log.w(TAG, "Received unexpected intent action: ${intent?.action}")
        }
    }
}