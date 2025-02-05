package com.shayan.playbackmaster.ui.fragments

import android.annotation.SuppressLint
import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.content.IntentFilter
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
import androidx.core.content.ContextCompat
import androidx.fragment.app.Fragment
import androidx.fragment.app.activityViewModels
import androidx.navigation.fragment.findNavController
import com.google.android.exoplayer2.ExoPlayer
import com.google.android.exoplayer2.MediaItem
import com.shayan.playbackmaster.R
import com.shayan.playbackmaster.databinding.FragmentVideoBinding
import com.shayan.playbackmaster.ui.viewmodel.AppViewModel

class VideoFragment : Fragment() {

    private var _binding: FragmentVideoBinding? = null
    private val binding get() = _binding!!
    private val viewModel: AppViewModel by activityViewModels()
    private var exoPlayer: ExoPlayer? = null
    private val handler = Handler(Looper.getMainLooper())
    private var wakeLock: PowerManager.WakeLock? = null
    private var screenTurnedOnByApp = false

    private val stopPlaybackReceiver = object : BroadcastReceiver() {
        override fun onReceive(context: Context?, intent: Intent?) {
            if (intent?.action == "ACTION_STOP_VIDEO") {
                stopPlayback()
            }
        }
    }

    override fun onCreateView(
        inflater: LayoutInflater, container: ViewGroup?, savedInstanceState: Bundle?
    ): View {
        _binding = FragmentVideoBinding.inflate(inflater, container, false)

        Log.d("VideoFragment", "onCreateView - VideoFragment layout is created.")

        // Set the orientation to landscape
        requireActivity().requestedOrientation = ActivityInfo.SCREEN_ORIENTATION_LANDSCAPE

        requireActivity().onBackPressedDispatcher.addCallback(
            viewLifecycleOwner,
            object : OnBackPressedCallback(true) {
                override fun handleOnBackPressed() {}
            })
        return binding.root
    }

    @SuppressLint("ClickableViewAccessibility")
    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)

        setupFullScreen()

        val videoUri = arguments?.getString("VIDEO_URI")

        if (!videoUri.isNullOrEmpty()) {
            Log.d("VideoFragment", "Received video URI: $videoUri")
            acquireWakeLock()
            setupPlayer(Uri.parse(videoUri))
        } else {
            binding.txtError.text = "Error: No video found!"
            binding.txtError.visibility = View.VISIBLE
            Log.e("VideoFragment", "No video URI received.")
        }

        binding.videoView.useController = false
        binding.fabAction1.setOnClickListener { navigateBack() }
        binding.fabAction2.setOnClickListener { requireActivity().finishAffinity() }
    }

    private fun setupFullScreen() {
        requireActivity().window.setFlags(
            WindowManager.LayoutParams.FLAG_FULLSCREEN or WindowManager.LayoutParams.FLAG_SECURE,
            WindowManager.LayoutParams.FLAG_FULLSCREEN or WindowManager.LayoutParams.FLAG_SECURE
        )
        Log.d("VideoFragment", "Full-screen mode set.")
    }

    private fun navigateBack() {
        try {
            requireActivity().requestedOrientation = ActivityInfo.SCREEN_ORIENTATION_UNSPECIFIED
            findNavController().navigate(R.id.action_videoFragment_to_homeFragment)
            Log.d("VideoFragment", "Navigating back to home fragment.")
        } catch (e: Exception) {
            Log.e("VideoFragment", "Navigation failed: ${e.message}", e)
        }
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
    }

    private fun stopPlayback() {
        exoPlayer?.stop()
        exoPlayer?.release()
        exoPlayer = null
        releaseWakeLock()

        if (isAdded) {
            context?.let {
                Toast.makeText(it, "Playback stopped", Toast.LENGTH_SHORT).show()
            }
            findNavController().navigate(R.id.homeFragment)
            Log.d("VideoFragment", "Playback stopped and navigating back.")
        } else {
            Log.e("VideoFragment", "Fragment is not attached to context. Unable to navigate.")
        }
    }

    private fun acquireWakeLock() {
        val powerManager = requireContext().getSystemService(Context.POWER_SERVICE) as PowerManager
        wakeLock = powerManager.newWakeLock(
            PowerManager.SCREEN_BRIGHT_WAKE_LOCK or PowerManager.ACQUIRE_CAUSES_WAKEUP,
            "PlaybackMaster::WakeLock"
        )
        wakeLock?.acquire(10 * 60 * 1000L) // Hold wake lock for 10 minutes
        screenTurnedOnByApp = true
        Log.d("VideoFragment", "Wake lock acquired to keep screen on during playback.")
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
        val filter = IntentFilter("ACTION_STOP_VIDEO")
        ContextCompat.registerReceiver(
            requireContext(), stopPlaybackReceiver, filter, ContextCompat.RECEIVER_NOT_EXPORTED
        )

        requireActivity().window.decorView.systemUiVisibility =
            (View.SYSTEM_UI_FLAG_IMMERSIVE_STICKY or View.SYSTEM_UI_FLAG_HIDE_NAVIGATION or View.SYSTEM_UI_FLAG_FULLSCREEN)

        Log.d("VideoFragment", "onResume - UI flags set for immersive sticky fullscreen.")
    }

    override fun onPause() {
        super.onPause()
        requireContext().unregisterReceiver(stopPlaybackReceiver)
        requireActivity().window.clearFlags(
            WindowManager.LayoutParams.FLAG_FULLSCREEN or WindowManager.LayoutParams.FLAG_SECURE
        )
        Log.d("VideoFragment", "onPause - Fullscreen flags cleared.")
    }

    override fun onDestroyView() {
        super.onDestroyView()

        // Revert the orientation to unspecified when leaving the fragment
        requireActivity().requestedOrientation = ActivityInfo.SCREEN_ORIENTATION_UNSPECIFIED

        releaseWakeLock()
        exoPlayer?.release()
        exoPlayer = null
        _binding = null
        Log.d("VideoFragment", "View destroyed and resources released.")
    }
}