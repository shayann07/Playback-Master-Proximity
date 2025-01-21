package com.shayan.playbackmaster.utils

import android.app.NotificationChannel
import android.app.NotificationManager
import android.content.Context
import android.os.Build
import androidx.core.app.NotificationCompat
import com.shayan.playbackmaster.R

object NotificationHelper {

    private const val CHANNEL_ID = "video_playback_channel"

    fun createNotification(context: Context, title: String, message: String) {
        val notificationManager =
            context.getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager

        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            val channel = NotificationChannel(
                CHANNEL_ID, "Video Playback Notifications", NotificationManager.IMPORTANCE_HIGH
            )
            notificationManager.createNotificationChannel(channel)
        }

        val notification =
            NotificationCompat.Builder(context, CHANNEL_ID).setSmallIcon(R.drawable.ic_notification)
                .setContentTitle(title).setContentText(message)
                .setPriority(NotificationCompat.PRIORITY_HIGH).build()

        notificationManager.notify(1, notification)
    }
}