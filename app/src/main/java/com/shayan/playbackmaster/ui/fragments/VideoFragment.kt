package com.shayan.playbackmaster.ui.fragments

import android.net.Uri
import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.view.WindowManager
import androidx.fragment.app.Fragment
import androidx.fragment.app.activityViewModels
import com.google.android.exoplayer2.ExoPlayer
import com.google.android.exoplayer2.MediaItem
import com.shayan.playbackmaster.databinding.FragmentVideoBinding
import com.shayan.playbackmaster.ui.viewmodel.AppViewModel
import java.util.Calendar

class VideoFragment : Fragment() {

    private var _binding: FragmentVideoBinding? = null
    private val binding get() = _binding!!
    private val viewModel: AppViewModel by activityViewModels()
    private var exoPlayer: ExoPlayer? = null

    override fun onCreateView(
        inflater: LayoutInflater, container: ViewGroup?, savedInstanceState: Bundle?
    ): View {
        _binding = FragmentVideoBinding.inflate(inflater, container, false)
        return binding.root
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)

        // Lock UI to prevent interruptions
        requireActivity().window.setFlags(
            WindowManager.LayoutParams.FLAG_FULLSCREEN or WindowManager.LayoutParams.FLAG_SECURE,
            WindowManager.LayoutParams.FLAG_FULLSCREEN or WindowManager.LayoutParams.FLAG_SECURE
        )

        val videoUri = viewModel.videoUri.value
        val currentTime = getCurrentTime()
        binding.txtError.visibility = View.GONE // Ensure it's hidden initially

        if (videoUri != null && currentTime in viewModel.startTime.value!!..viewModel.endTime.value!!) {
            setupPlayer(Uri.parse(videoUri))
        } else {
            binding.txtError.text = "Current time is outside the configured playback period."
        }
    }

    // Helper to get the current time in "HH:mm" format
    private fun getCurrentTime(): String {
        val calendar = Calendar.getInstance()
        val hour = calendar.get(Calendar.HOUR_OF_DAY)
        val minute = calendar.get(Calendar.MINUTE)
        return "$hour:${minute.toString().padStart(2, '0')}"
    }


    private fun setupPlayer(uri: Uri) {
        exoPlayer = ExoPlayer.Builder(requireContext()).build()
        binding.videoView.player = exoPlayer

        val mediaItem = MediaItem.fromUri(uri)
        exoPlayer?.setMediaItem(mediaItem)

        // Enable looping
        exoPlayer?.repeatMode = ExoPlayer.REPEAT_MODE_ALL

        exoPlayer?.prepare()
        exoPlayer?.play()
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
        exoPlayer?.release()
        exoPlayer = null
        _binding = null
    }
}