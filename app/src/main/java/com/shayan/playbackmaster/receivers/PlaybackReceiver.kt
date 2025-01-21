package com.shayan.playbackmaster.receivers

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.os.Handler
import android.os.Looper
import android.widget.Toast
import com.shayan.playbackmaster.ui.fragments.ExitPlaybackListener

class PlaybackReceiver(private val listener: ExitPlaybackListener) : BroadcastReceiver() {

    private var pressCount = 0
    private val handler = Handler(Looper.getMainLooper())

    override fun onReceive(context: Context, intent: Intent) {
        if (intent.action == Intent.ACTION_SCREEN_OFF) {
            pressCount++
            handler.postDelayed({
                when (pressCount) {
                    2 -> stopPlayback(context)
                    3 -> exitPlayback(context)
                }
                pressCount = 0
            }, 500) // Detect double/triple press within 500ms
        }
    }

    private fun stopPlayback(context: Context) {
        Toast.makeText(context, "Stopping playback", Toast.LENGTH_SHORT).show()
        // Stop playback logic (notify VideoFragment to stop ExoPlayer)
    }

    private fun exitPlayback(context: Context) {
        Toast.makeText(context, "Exiting playback mode", Toast.LENGTH_SHORT).show()
        listener.onExitPlayback() // Notify the listener to navigate back to HomeFragment
    }
}
