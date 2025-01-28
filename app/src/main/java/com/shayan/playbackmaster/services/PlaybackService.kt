package com.shayan.playbackmaster.services

import android.app.Service
import android.content.Intent
import android.os.Handler
import android.os.IBinder
import android.os.Looper
import android.widget.Toast
import com.google.android.exoplayer2.ExoPlayer
import com.google.android.exoplayer2.MediaItem
import com.shayan.playbackmaster.data.preferences.PreferencesHelper
import java.text.SimpleDateFormat
import java.util.Calendar
import java.util.Locale

class PlaybackService : Service() {

    private var exoPlayer: ExoPlayer? = null
    private val handler = Handler(Looper.getMainLooper())
    private var isProximityDetected = false

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        val preferencesHelper = PreferencesHelper(this)

        when (intent?.action) {
            "ACTION_PROXIMITY_DETECTED" -> {
                isProximityDetected = true
                startPlaybackIfScheduled(preferencesHelper)
            }

            "ACTION_PROXIMITY_LOST" -> {
                isProximityDetected = false
                stopPlayback()
            }

            "ACTION_USB_ERROR" -> {
                val errorMessage = intent.getStringExtra("ERROR_MESSAGE")
                Toast.makeText(this, errorMessage, Toast.LENGTH_LONG).show()
                stopSelf()
            }

            else -> handleScheduledPlayback(preferencesHelper)
        }

        return START_STICKY
    }

    private fun handleScheduledPlayback(preferencesHelper: PreferencesHelper) {
        val startTime = preferencesHelper.getStartTime()
        val endTime = preferencesHelper.getEndTime()
        val currentTime = System.currentTimeMillis()

        if (startTime != null && endTime != null) {
            val startMillis = convertTimeToMillis(startTime)
            val endMillis = convertTimeToMillis(endTime)

            if (currentTime < startMillis) {
                val delayToStart = startMillis - currentTime
                handler.postDelayed({
                    startPlaybackIfConditionsMet(preferencesHelper)
                }, delayToStart)
                Toast.makeText(this, "Playback scheduled at $startTime", Toast.LENGTH_SHORT).show()
            } else if (currentTime in startMillis..endMillis) {
                startPlaybackIfConditionsMet(preferencesHelper)
            } else {
                stopPlayback()
                Toast.makeText(
                    this,
                    "Playback not scheduled as current time is out of range.",
                    Toast.LENGTH_SHORT
                ).show()
            }
        }
    }

    private fun startPlaybackIfScheduled(preferencesHelper: PreferencesHelper) {
        if (isWithinScheduledTime(preferencesHelper)) {
            val videoUri = preferencesHelper.getVideoUri()
            val endMillis = convertTimeToMillis(preferencesHelper.getEndTime() ?: "")
            startPlayback(videoUri, endMillis)
        }
    }

    private fun startPlaybackIfConditionsMet(preferencesHelper: PreferencesHelper) {
        if (isProximityDetected && isWithinScheduledTime(preferencesHelper)) {
            val videoUri = preferencesHelper.getVideoUri()
            val endMillis = convertTimeToMillis(preferencesHelper.getEndTime() ?: "")
            startPlayback(videoUri, endMillis)
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

        exoPlayer = ExoPlayer.Builder(this).build()
        val mediaItem = MediaItem.fromUri(videoUri)
        exoPlayer?.setMediaItem(mediaItem)
        exoPlayer?.prepare()
        exoPlayer?.play()

        Toast.makeText(this, "Playback started", Toast.LENGTH_SHORT).show()

        val delayToEnd = endMillis - System.currentTimeMillis()
        if (delayToEnd > 0) {
            handler.postDelayed({
                stopPlayback()
            }, delayToEnd)
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

    override fun onBind(intent: Intent?): IBinder? = null

    override fun onDestroy() {
        super.onDestroy()
        handler.removeCallbacksAndMessages(null)
        exoPlayer?.release()
        exoPlayer = null
    }
}