package com.shayan.playbackmaster.utils

import android.app.AlarmManager
import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import android.os.Build
import android.provider.Settings
import android.util.Log
import androidx.annotation.RequiresApi
import com.shayan.playbackmaster.services.PlaybackService
import java.util.Calendar

object AlarmUtils {

    @RequiresApi(Build.VERSION_CODES.M)
    fun scheduleDailyAlarm(context: Context, videoUri: String, startTime: String, endTime: String) {
        Log.d(
            "AlarmUtils",
            "Scheduling daily alarm with URI: $videoUri, start: $startTime, end: $endTime"
        )
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
            val alarmManager = context.getSystemService(AlarmManager::class.java)
            if (alarmManager?.canScheduleExactAlarms() == false) {
                try {
                    val intent = Intent(Settings.ACTION_REQUEST_SCHEDULE_EXACT_ALARM).apply {
                        flags = Intent.FLAG_ACTIVITY_NEW_TASK
                    }
                    context.startActivity(intent)
                    return
                } catch (e: Exception) {
                    Log.e("AlarmUtils", "Failed to schedule alarm", e)
                    return
                }
            }
        }

        val alarmManager = context.getSystemService(Context.ALARM_SERVICE) as AlarmManager
        val intent = Intent(context, PlaybackService::class.java).apply {
            action = "ACTION_ALARM_TRIGGERED" // <-- Ensure this matches `PlaybackService`
        }

        val pendingIntent = PendingIntent.getService(
            context, 0, intent, PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        )

        val calendar = Calendar.getInstance()
        val (hour, minute) = startTime.split(":").map { it.toInt() }
        calendar.set(Calendar.HOUR_OF_DAY, hour)
        calendar.set(Calendar.MINUTE, minute)
        calendar.set(Calendar.SECOND, 0)

        if (calendar.timeInMillis < System.currentTimeMillis()) {
            calendar.add(Calendar.DAY_OF_YEAR, 1) // Schedule for next day if time has passed
        }

        alarmManager.setExactAndAllowWhileIdle(
            AlarmManager.RTC_WAKEUP, calendar.timeInMillis, pendingIntent
        )
        Log.d("AlarmUtils", "Alarm set successfully for time: ${calendar.time}")
    }
}