package com.shayan.playbackmaster.services

import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.Service
import android.content.Intent
import android.os.Build
import android.os.Handler
import android.os.IBinder
import android.os.Looper
import android.util.Log
import android.widget.Toast
import androidx.core.app.NotificationCompat
import com.google.android.exoplayer2.ExoPlayer
import com.google.android.exoplayer2.MediaItem
import com.shayan.playbackmaster.R
import com.shayan.playbackmaster.data.preferences.PreferencesHelper
import java.text.SimpleDateFormat
import java.util.Calendar
import java.util.Locale

class PlaybackService : Service() {

    private var exoPlayer: ExoPlayer? = null
    private val handler = Handler(Looper.getMainLooper())
    private var isProximityDetected = false

    override fun onCreate() {
        super.onCreate()
        createNotificationChannel()
        val notification =
            NotificationCompat.Builder(this, "playback_channel").setContentTitle("Playback Master")
                .setContentText("Scheduled video playback is running")
                .setSmallIcon(R.drawable.ic_notification)
                .setPriority(NotificationCompat.PRIORITY_HIGH).setOngoing(true).build()
        startForeground(1, notification)
    }

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        val preferencesHelper = PreferencesHelper(this)

        when (intent?.action) {
            "ACTION_PROXIMITY_DETECTED" -> {
                isProximityDetected = true
                handleProximitySignal("1")
            }

            "ACTION_PROXIMITY_LOST" -> {
                isProximityDetected = false
                stopPlayback()
            }

            "ACTION_ALARM_TRIGGERED" -> {
                Log.d("PlaybackService", "Alarm triggered. Checking playback conditions...")
                if (UsbProximityService.isConnected && isProximityDetected && isWithinScheduledTime(
                        preferencesHelper
                    )
                ) {
                    checkAndStartPlayback(preferencesHelper)
                } else {
                    Log.d("PlaybackService", "Playback not started - Conditions not met.")
                }
            }
        }

        return START_STICKY
    }

    private fun handleProximitySignal(signal: Any) {
        val signalString = signal.toString()
        Log.d("PlaybackService", "Received proximity signal: $signalString")

        if (signalString == "1") {
            handler.postDelayed({
                if (isProximityDetected) {
                    val preferencesHelper = PreferencesHelper(this)
                    checkAndStartPlayback(preferencesHelper)
                }
            }, 3000) // 3-second delay to confirm presence
        } else if (signalString == "0") {
            stopPlayback()
        }
    }

    private fun checkAndStartPlayback(preferencesHelper: PreferencesHelper) {
        if (isProximityDetected && isWithinScheduledTime(preferencesHelper) && UsbProximityService.isConnected) {
            val videoUri = preferencesHelper.getVideoUri()
            val endMillis = convertTimeToMillis(preferencesHelper.getEndTime() ?: "")
            startPlayback(videoUri, endMillis)
        } else {
            stopPlayback()
        }
    }

    private fun isWithinScheduledTime(preferencesHelper: PreferencesHelper): Boolean {
        val startTime = preferencesHelper.getStartTime() ?: return false
        val endTime = preferencesHelper.getEndTime() ?: return false
        val currentTime = System.currentTimeMillis()
        val startMillis = convertTimeToMillis(startTime)
        val endMillis = convertTimeToMillis(endTime)

        return currentTime in startMillis..endMillis
    }

    private fun startPlayback(videoUri: String?, endMillis: Long) {
        if (videoUri == null) {
            stopSelf()
            return
        }

        if (exoPlayer == null) {
            exoPlayer = ExoPlayer.Builder(this).build()
            val mediaItem = MediaItem.fromUri(videoUri)
            exoPlayer?.setMediaItem(mediaItem)
            exoPlayer?.prepare()
            exoPlayer?.play()
            Toast.makeText(this, "Playback started", Toast.LENGTH_SHORT).show()
        }

        val delayToEnd = endMillis - System.currentTimeMillis()
        if (delayToEnd > 0) {
            handler.postDelayed({ stopPlayback() }, delayToEnd)
        }
    }

    private fun stopPlayback() {
        exoPlayer?.stop()
        exoPlayer?.release()
        exoPlayer = null
        stopSelf()
        Toast.makeText(this, "Playback stopped", Toast.LENGTH_SHORT).show()
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

    private fun createNotificationChannel() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            val channel = NotificationChannel(
                "playback_channel", "Playback Master Service", NotificationManager.IMPORTANCE_LOW
            )
            val manager = getSystemService(NotificationManager::class.java)
            manager.createNotificationChannel(channel)
        }
    }

    override fun onBind(intent: Intent?): IBinder? = null

    override fun onDestroy() {
        super.onDestroy()
        handler.removeCallbacksAndMessages(null)
        exoPlayer?.release()
        exoPlayer = null
    }
}