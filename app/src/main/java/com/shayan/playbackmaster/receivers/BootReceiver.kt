package com.shayan.playbackmaster.receivers

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.os.Build
import androidx.annotation.RequiresApi
import com.shayan.playbackmaster.data.preferences.PreferencesHelper
import com.shayan.playbackmaster.utils.AlarmUtils

class BootReceiver : BroadcastReceiver() {
    @RequiresApi(Build.VERSION_CODES.M)
    override fun onReceive(context: Context, intent: Intent?) {
        if (intent?.action == Intent.ACTION_BOOT_COMPLETED) {
            // Reschedule alarm
            val preferences = PreferencesHelper(context)
            val videoUri = preferences.getVideoUri()
            val startTime = preferences.getStartTime()
            val endTime = preferences.getEndTime()

            if (!videoUri.isNullOrEmpty() && !startTime.isNullOrEmpty() && !endTime.isNullOrEmpty()) {
                AlarmUtils.scheduleDailyAlarm(context, videoUri, startTime, endTime)
            }
        }
    }
}
