package com.shayan.playbackmaster.services

import android.app.Service
import android.content.Context
import android.content.Intent
import android.os.IBinder
import android.widget.Toast
import com.google.android.exoplayer2.ExoPlayer
import com.google.android.exoplayer2.MediaItem
import com.shayan.playbackmaster.data.preferences.PreferencesHelper
import java.text.SimpleDateFormat
import java.util.Calendar
import java.util.Locale

class PlaybackService : Service() {

    private var exoPlayer: ExoPlayer? = null

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        val preferencesHelper = PreferencesHelper(this)
        val startTime = preferencesHelper.getStartTime()
        val endTime = preferencesHelper.getEndTime()
        val currentTime = getCurrentTime()

        if (startTime != null && endTime != null && currentTime in startTime..endTime) {
            val videoUri = preferencesHelper.getVideoUri()
            if (videoUri != null) {
                startPlayback(videoUri)
            }
        }
        return START_STICKY
    }

    private fun startPlayback(videoUri: String) {
        exoPlayer = ExoPlayer.Builder(this).build()
        val mediaItem = MediaItem.fromUri(videoUri)
        exoPlayer?.setMediaItem(mediaItem)
        exoPlayer?.prepare()
        exoPlayer?.play()
    }

    private fun getCurrentTime(): String {
        val calendar = Calendar.getInstance()
        val formatter = SimpleDateFormat("HH:mm", Locale.getDefault())
        return formatter.format(calendar.time)
    }

    override fun onBind(intent: Intent?): IBinder? = null

    private fun stopPlayback(context: Context) {
        context.stopService(Intent(context, PlaybackService::class.java))
        Toast.makeText(context, "Playback stopped", Toast.LENGTH_SHORT).show()
    }

    override fun onDestroy() {
        super.onDestroy()
        exoPlayer?.release()
        exoPlayer = null
        stopSelf() // Ensure the service stops
    }
}