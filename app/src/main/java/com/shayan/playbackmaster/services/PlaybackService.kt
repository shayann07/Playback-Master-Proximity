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
    private var isPlaying = false

    companion object {
        private const val TAG = "PlaybackService"
        private const val NOTIFICATION_CHANNEL_ID = "playback_channel"
        private const val ACTION_PROXIMITY_DETECTED = "ACTION_PROXIMITY_DETECTED"
        private const val ACTION_PROXIMITY_LOST = "ACTION_PROXIMITY_LOST"
    }

    @RequiresApi(Build.VERSION_CODES.TIRAMISU)
    override fun onCreate() {
        super.onCreate()
        Log.d(TAG, "Playback Service created and ProximityReceiver registered.")

        registerReceiver(proximityReceiver, IntentFilter().apply {
            addAction(ACTION_PROXIMITY_DETECTED)
            addAction(ACTION_PROXIMITY_LOST)
        }, RECEIVER_NOT_EXPORTED)

        createNotificationChannel()
        startForeground(1, createForegroundNotification())
    }

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        when (intent?.action) {
            "ACTION_PLAY_VIDEO" -> {
                if (!isPlaying) {
                    Log.d("PlaybackService", "Starting Video Playback")
                    isPlaying = true
                    val preferencesHelper = PreferencesHelper(this)
                    val videoUri = preferencesHelper.getVideoUri()

                    if (!videoUri.isNullOrEmpty() && isWithinScheduledTime(preferencesHelper)) {
                        val videoIntent = Intent("ACTION_SHOW_VIDEO_FRAGMENT")
                        videoIntent.putExtra("VIDEO_URI", videoUri)
                        sendBroadcast(videoIntent)  // ✅ Broadcasts the video URI
                    } else {
                        Log.e("PlaybackService", "Video URI is null or empty. Cannot play video.")
                    }
                }
            }

            "ACTION_STOP_VIDEO" -> {
                if (isPlaying) {
                    Log.d("PlaybackService", "Stopping Video Playback")
                    val preferencesHelper = PreferencesHelper(this)
                    if (!isWithinScheduledTime(preferencesHelper) || !isProximityDetected) {
                        isPlaying = false
                        sendBroadcast(Intent("ACTION_STOP_VIDEO"))
                        stopSelf()
                    }

                }
            }
        }
        return START_STICKY
    }

    private fun shouldStartPlayback(preferencesHelper: PreferencesHelper): Boolean {
        val shouldStart =
            isProximityDetected && UsbProximityService.isConnected && isWithinScheduledTime(
                preferencesHelper
            )
        Log.d(TAG, "Checking if playback should start: $shouldStart")
        return shouldStart
    }

    private fun handleProximitySignal(signal: String) {
        Log.d(TAG, "Handling proximity signal: $signal")
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
        Log.d(TAG, "Starting playback. Video URI: $videoUri, End time in millis: $endMillis")

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
        Log.d(TAG, "Stopping playback.")
        exoPlayer?.apply {
            stop()
            release()
        }
        exoPlayer = null
        stopSelf()
        Toast.makeText(this, "Playback stopped", Toast.LENGTH_SHORT).show()
        Log.d(TAG, "Playback successfully stopped.")
    }

    private fun isWithinScheduledTime(preferencesHelper: PreferencesHelper): Boolean {
        val currentTime = System.currentTimeMillis()
        val startMillis = convertTimeToMillis(preferencesHelper.getStartTime() ?: "")
        val endMillis = convertTimeToMillis(preferencesHelper.getEndTime() ?: "")
        val withinScheduledTime = currentTime in startMillis..endMillis
        Log.d(TAG, "Current time within scheduled time: $withinScheduledTime")
        return withinScheduledTime
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
            Log.d(TAG, "Notification channel created.")
        }
    }

    private fun createForegroundNotification(): android.app.Notification {
        Log.d(TAG, "Creating foreground notification for playback service.")

        return NotificationCompat.Builder(this, NOTIFICATION_CHANNEL_ID)
            .setContentTitle("Playback Master")
            .setContentText("Scheduled video playback is running")
            .setSmallIcon(R.drawable.ic_notification).setPriority(NotificationCompat.PRIORITY_HIGH)
            .setOngoing(true).build()
    }

    override fun onDestroy() {
        super.onDestroy()

        unregisterReceiver(proximityReceiver)
        handler.removeCallbacksAndMessages(null)
        exoPlayer?.release()
        exoPlayer = null
        Log.d(TAG, "Playback Service destroyed and ProximityReceiver unregistered.")
    }

    override fun onBind(intent: Intent?): IBinder? = null
}