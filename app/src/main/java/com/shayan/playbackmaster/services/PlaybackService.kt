package com.shayan.playbackmaster.services

import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.Service
import android.content.Intent
import android.content.IntentFilter
import android.os.Build
import android.os.Handler
import android.os.IBinder
import android.os.Looper
import android.util.Log
import android.widget.Toast
import androidx.annotation.RequiresApi
import androidx.core.app.NotificationCompat
import com.google.android.exoplayer2.ExoPlayer
import com.google.android.exoplayer2.MediaItem
import com.shayan.playbackmaster.R
import com.shayan.playbackmaster.data.preferences.PreferencesHelper
import com.shayan.playbackmaster.receivers.ProximityReceiver
import java.text.SimpleDateFormat
import java.util.Calendar
import java.util.Locale

class PlaybackService : Service() {

    private var exoPlayer: ExoPlayer? = null
    private val handler = Handler(Looper.getMainLooper())
    private var isProximityDetected = false
    private val proximityReceiver = ProximityReceiver()

    companion object {
        private const val TAG = "PlaybackService"
        private const val NOTIFICATION_CHANNEL_ID = "playback_channel"
        private const val ACTION_PROXIMITY_DETECTED = "ACTION_PROXIMITY_DETECTED"
        private const val ACTION_PROXIMITY_LOST = "ACTION_PROXIMITY_LOST"
        private const val ACTION_ALARM_TRIGGERED = "ACTION_ALARM_TRIGGERED"
    }

    @RequiresApi(Build.VERSION_CODES.TIRAMISU)
    override fun onCreate() {
        super.onCreate()
        Log.d(TAG, "Service Created. Registering ProximityReceiver...")

        registerReceiver(proximityReceiver, IntentFilter().apply {
            addAction(ACTION_PROXIMITY_DETECTED)
            addAction(ACTION_PROXIMITY_LOST)
        }, RECEIVER_NOT_EXPORTED)

        createNotificationChannel()
        startForeground(1, createForegroundNotification())
    }

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        val preferencesHelper = PreferencesHelper(this)

        when (intent?.action) {
            ACTION_PROXIMITY_DETECTED -> {
                isProximityDetected = true
                handleProximitySignal("1")
            }

            ACTION_PROXIMITY_LOST -> {
                isProximityDetected = false
                stopPlayback()
            }

            ACTION_ALARM_TRIGGERED -> {
                Log.d(TAG, "Alarm triggered. Checking playback conditions...")
                if (shouldStartPlayback(preferencesHelper)) {
                    startPlayback(preferencesHelper)
                } else {
                    Log.d(TAG, "Playback not started - Conditions not met.")
                }
            }
        }

        logPlaybackConditions(preferencesHelper)
        return START_STICKY
    }

    private fun shouldStartPlayback(preferencesHelper: PreferencesHelper): Boolean {
        return isProximityDetected && UsbProximityService.isConnected && isWithinScheduledTime(
            preferencesHelper
        )
    }

    private fun handleProximitySignal(signal: String) {
        val snackbarIntent = Intent("ACTION_SHOW_SNACKBAR")

        when (signal) {
            "1" -> {
                handler.postDelayed({
                    val preferencesHelper = PreferencesHelper(this)
                    if (shouldStartPlayback(preferencesHelper)) {
                        startPlayback(preferencesHelper)
                    }
                }, 2000) // 2-second delay to confirm presence
                snackbarIntent.putExtra("MESSAGE", "Playback Starting (Signal: 1)")
            }

            "0" -> {
                stopPlayback()
                snackbarIntent.putExtra("MESSAGE", "Playback Stopping (Signal: 0)")
            }

            else -> {
                snackbarIntent.putExtra("MESSAGE", "Unexpected Signal: '$signal'")
            }
        }

        sendBroadcast(snackbarIntent) // Show the snackbar on the UI
    }

    private fun startPlayback(preferencesHelper: PreferencesHelper) {
        val videoUri = preferencesHelper.getVideoUri()
        val endMillis = convertTimeToMillis(preferencesHelper.getEndTime() ?: "")

        if (videoUri.isNullOrEmpty()) {
            Log.e(TAG, "Invalid video URI. Playback aborted.")
            stopSelf()
            return
        }

        if (exoPlayer == null) {
            try {
                exoPlayer = ExoPlayer.Builder(this).build().apply {
                    setMediaItem(MediaItem.fromUri(videoUri))
                    prepare()
                    play()
                }
                Toast.makeText(this, "Playback started", Toast.LENGTH_SHORT).show()
                Log.d(TAG, "Playback started for URI: $videoUri")
            } catch (e: Exception) {
                Log.e(TAG, "Error initializing ExoPlayer: ${e.message}", e)
            }
        }

        val delayToEnd = endMillis - System.currentTimeMillis()
        if (delayToEnd > 0) {
            handler.postDelayed({ stopPlayback() }, delayToEnd)
        }
    }

    private fun stopPlayback() {
        exoPlayer?.apply {
            stop()
            release()
        }
        exoPlayer = null
        stopSelf()
        Toast.makeText(this, "Playback stopped", Toast.LENGTH_SHORT).show()
        Log.d(TAG, "Playback stopped")
    }

    private fun isWithinScheduledTime(preferencesHelper: PreferencesHelper): Boolean {
        val startMillis = convertTimeToMillis(preferencesHelper.getStartTime() ?: "")
        val endMillis = convertTimeToMillis(preferencesHelper.getEndTime() ?: "")
        val currentTime = System.currentTimeMillis()

        return currentTime in startMillis..endMillis
    }

    private fun convertTimeToMillis(time: String): Long {
        return try {
            val formatter = SimpleDateFormat("HH:mm", Locale.getDefault())
            val (hour, minute) = time.split(":").map { it.toInt() }
            Calendar.getInstance().apply {
                set(Calendar.HOUR_OF_DAY, hour)
                set(Calendar.MINUTE, minute)
                set(Calendar.SECOND, 0)
            }.timeInMillis
        } catch (e: Exception) {
            Log.e(TAG, "Invalid time format: $time", e)
            0L
        }
    }

    private fun createNotificationChannel() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            val channel = NotificationChannel(
                NOTIFICATION_CHANNEL_ID,
                "Playback Master Service",
                NotificationManager.IMPORTANCE_LOW
            )
            getSystemService(NotificationManager::class.java)?.createNotificationChannel(channel)
        }
    }

    private fun createForegroundNotification(): android.app.Notification {
        return NotificationCompat.Builder(this, NOTIFICATION_CHANNEL_ID)
            .setContentTitle("Playback Master")
            .setContentText("Scheduled video playback is running")
            .setSmallIcon(R.drawable.ic_notification).setPriority(NotificationCompat.PRIORITY_HIGH)
            .setOngoing(true).build()
    }

    private fun logPlaybackConditions(preferencesHelper: PreferencesHelper) {
        Log.d(TAG, "USB Connected: ${UsbProximityService.isConnected}")
        Log.d(TAG, "Proximity Detected: $isProximityDetected")
        Log.d(TAG, "Within Scheduled Time: ${isWithinScheduledTime(preferencesHelper)}")
    }

    override fun onDestroy() {
        super.onDestroy()
        Log.d(TAG, "Service Destroyed. Unregistering ProximityReceiver...")

        unregisterReceiver(proximityReceiver)
        handler.removeCallbacksAndMessages(null)
        exoPlayer?.release()
        exoPlayer = null
    }

    override fun onBind(intent: Intent?): IBinder? = null
}