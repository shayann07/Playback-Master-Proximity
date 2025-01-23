package com.shayan.playbackmaster.ui.fragments

import android.annotation.SuppressLint
import android.content.Context
import android.content.pm.ActivityInfo
import android.net.Uri
import android.os.Bundle
import android.os.Handler
import android.os.Looper
import android.os.PowerManager
import android.util.Log
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.view.WindowManager
import android.widget.Toast
import androidx.activity.OnBackPressedCallback
import androidx.fragment.app.Fragment
import androidx.fragment.app.activityViewModels
import androidx.navigation.findNavController
import androidx.navigation.fragment.findNavController
import com.google.android.exoplayer2.ExoPlayer
import com.google.android.exoplayer2.MediaItem
import com.shayan.playbackmaster.R
import com.shayan.playbackmaster.databinding.FragmentVideoBinding
import com.shayan.playbackmaster.ui.viewmodel.AppViewModel
import java.text.SimpleDateFormat
import java.util.Calendar
import java.util.Locale

class VideoFragment : Fragment() {

    private var _binding: FragmentVideoBinding? = null
    private val binding get() = _binding!!
    private val viewModel: AppViewModel by activityViewModels()
    private var exoPlayer: ExoPlayer? = null
    private val handler = Handler(Looper.getMainLooper())
    private var wakeLock: PowerManager.WakeLock? = null
    private var screenTurnedOnByApp = false

    override fun onCreateView(
        inflater: LayoutInflater, container: ViewGroup?, savedInstanceState: Bundle?
    ): View {
        _binding = FragmentVideoBinding.inflate(inflater, container, false)

        // Set the orientation to landscape
        requireActivity().requestedOrientation = ActivityInfo.SCREEN_ORIENTATION_LANDSCAPE

        requireActivity().onBackPressedDispatcher.addCallback(viewLifecycleOwner,
            object : OnBackPressedCallback(true) {
                override fun handleOnBackPressed() {}
            })
        return binding.root
    }

    @SuppressLint("ClickableViewAccessibility")
    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)

        requireActivity().window.setFlags(
            WindowManager.LayoutParams.FLAG_FULLSCREEN or WindowManager.LayoutParams.FLAG_SECURE,
            WindowManager.LayoutParams.FLAG_FULLSCREEN or WindowManager.LayoutParams.FLAG_SECURE
        )

        val videoUri = arguments?.getString("VIDEO_URI") ?: viewModel.videoUri.value
        val startTime = arguments?.getString("START_TIME") ?: viewModel.startTime.value
        val endTime = arguments?.getString("END_TIME") ?: viewModel.endTime.value

        Log.d("VideoFragment", "Video URI: $videoUri, Start Time: $startTime, End Time: $endTime")

        if (videoUri != null && isWithinPlaybackPeriod(startTime, endTime)) {
            acquireWakeLock()
            setupPlayer(Uri.parse(videoUri))
        } else {
            binding.txtError.text = "Current time is outside the configured playback period."
            binding.txtError.visibility = View.VISIBLE
        }

        binding.videoView.useController = false
        binding.fabAction1.setOnClickListener {
            try {
                // Revert to portrait before navigating
                requireActivity().requestedOrientation = ActivityInfo.SCREEN_ORIENTATION_UNSPECIFIED

                // Use NavController to navigate
                findNavController().navigate(R.id.action_videoFragment_to_homeFragment)
            } catch (e: Exception) {
                Log.e("VideoFragment", "Navigation failed: ${e.message}")
            }
        }
        binding.fabAction2.setOnClickListener {
            requireActivity().finishAffinity()
        }
    }

    private fun isWithinPlaybackPeriod(startTime: String?, endTime: String?): Boolean {
        if (startTime == null || endTime == null) return false
        val currentTime = getCurrentTime()
        return currentTime in startTime..endTime
    }

    private fun getCurrentTime(): String {
        val calendar = Calendar.getInstance()
        val hour = calendar.get(Calendar.HOUR_OF_DAY)
        val minute = calendar.get(Calendar.MINUTE)
        return "$hour:${minute.toString().padStart(2, '0')}"
    }

    private fun scheduleStopAtEndTime(endTime: String?) {
        if (endTime == null) return
        val endMillis = convertTimeToMillis(endTime)
        val currentMillis = System.currentTimeMillis()
        val delay = endMillis - currentMillis

        if (delay > 0) {
            handler.postDelayed({
                stopPlayback()
            }, delay)
        } else {
            stopPlayback()
        }
    }

    private fun stopPlayback() {
        exoPlayer?.stop()
        exoPlayer?.release()
        exoPlayer = null
        releaseWakeLock()
        if (isAdded) {
            context?.let {
                Toast.makeText(it, "Playback stopped at end time", Toast.LENGTH_SHORT).show()
            }
            findNavController().navigate(R.id.homeFragment)
        } else {
            Log.e("VideoFragment", "Fragment is not attached to context. Unable to navigate.")
        }
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

    private fun setupPlayer(uri: Uri) {
        exoPlayer = ExoPlayer.Builder(requireContext()).build()
        binding.videoView.player = exoPlayer

        val mediaItem = MediaItem.fromUri(uri)
        exoPlayer?.setMediaItem(mediaItem)

        exoPlayer?.repeatMode = ExoPlayer.REPEAT_MODE_ALL

        exoPlayer?.prepare()
        exoPlayer?.play()
        Log.d("VideoFragment", "ExoPlayer started playing video from URI: $uri")
        val endTime = arguments?.getString("END_TIME") ?: viewModel.endTime.value
        scheduleStopAtEndTime(endTime)
    }

    private fun acquireWakeLock() {
        val powerManager = requireContext().getSystemService(Context.POWER_SERVICE) as PowerManager
        wakeLock = powerManager.newWakeLock(
            PowerManager.SCREEN_BRIGHT_WAKE_LOCK or PowerManager.ACQUIRE_CAUSES_WAKEUP,
            "PlaybackMaster::WakeLock"
        )
        wakeLock?.acquire(10 * 60 * 1000L)
        screenTurnedOnByApp = true
        Log.d("VideoFragment", "Wake lock acquired to turn on the screen.")
    }

    private fun releaseWakeLock() {
        if (screenTurnedOnByApp) {
            wakeLock?.let {
                if (it.isHeld) {
                    it.release()
                    Log.d("VideoFragment", "Wake lock released.")
                }
            }
            screenTurnedOnByApp = false
        }
    }

    override fun onResume() {
        super.onResume()
        requireActivity().window.decorView.systemUiVisibility =
            (View.SYSTEM_UI_FLAG_IMMERSIVE_STICKY or View.SYSTEM_UI_FLAG_HIDE_NAVIGATION or View.SYSTEM_UI_FLAG_FULLSCREEN)
    }

    override fun onPause() {
        super.onPause()
        requireActivity().window.clearFlags(
            WindowManager.LayoutParams.FLAG_FULLSCREEN or WindowManager.LayoutParams.FLAG_SECURE
        )
    }

    override fun onDestroyView() {
        super.onDestroyView()

        // Revert the orientation to unspecified when leaving the fragment
        requireActivity().requestedOrientation = ActivityInfo.SCREEN_ORIENTATION_UNSPECIFIED

        releaseWakeLock()
        exoPlayer?.release()
        exoPlayer = null
        _binding = null
    }
}