package com.shayan.playbackmaster.ui.fragments

import android.app.Activity
import android.app.KeyguardManager
import android.content.Context
import android.content.Intent
import android.net.Uri
import android.os.Build
import android.os.Bundle
import android.provider.Settings
import android.util.Log
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.Toast
import androidx.annotation.RequiresApi
import androidx.fragment.app.Fragment
import androidx.fragment.app.activityViewModels
import androidx.navigation.findNavController
import com.shayan.playbackmaster.R
import com.shayan.playbackmaster.databinding.FragmentHomeBinding
import com.shayan.playbackmaster.ui.viewmodel.AppViewModel
import com.shayan.playbackmaster.utils.Constants
import com.shayan.playbackmaster.utils.TimePickerHelper

class HomeFragment : Fragment() {

    private var _binding: FragmentHomeBinding? = null
    private val binding get() = _binding!!
    private val viewModel: AppViewModel by activityViewModels()

    override fun onCreateView(
        inflater: LayoutInflater, container: ViewGroup?, savedInstanceState: Bundle?
    ): View {
        _binding = FragmentHomeBinding.inflate(inflater, container, false)
        return binding.root
    }

    @RequiresApi(Build.VERSION_CODES.M)
    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)

        setupTimeSelection()
        setupVideoUpload()
        setupPlaybackNavigation()
        initializeScreenLockSwitch()
    }

    @RequiresApi(Build.VERSION_CODES.M)
    private fun initializeScreenLockSwitch() {
        updateSwitchState()

        binding.switchScreenLock.setOnCheckedChangeListener { _, isChecked ->
            if (isChecked) {
                if (isLockScreenDisabled(requireContext())) {
                    showToast("Screen lock is already disabled.")
                } else {
                    showDisableLockDialog()
                    updateSwitchState() // Revert the switch state after showing the dialog
                }
            } else {
                showToast("Screen lock settings remain unchanged.")
                updateSwitchState() // Ensure the state remains consistent
            }
        }
    }

    @RequiresApi(Build.VERSION_CODES.M)
    private fun updateSwitchState() {
        val isDisabled = isLockScreenDisabled(requireContext())
        binding.switchScreenLock.isChecked = isDisabled
        Log.d("HomeFragment", "Switch updated to: $isDisabled")
    }

    @RequiresApi(Build.VERSION_CODES.M)
    private fun isLockScreenDisabled(context: Context): Boolean {
        val keyguardManager = context.getSystemService(Context.KEYGUARD_SERVICE) as KeyguardManager
        val isDeviceSecure = keyguardManager.isDeviceSecure

        Log.d("HomeFragment", "isDeviceSecure: $isDeviceSecure")
        return !isDeviceSecure
    }

    private fun showDisableLockDialog() {
        if (!isAdded) {
            Log.e("HomeFragment", "Fragment not attached to activity. Cannot show dialog.")
            return
        }

        val dialogMessage = """
            To ensure seamless operation, please disable all screen locks, including swipe-to-unlock:
            1. Go to your device settings.
            2. Navigate to "Security" or "Screen Lock."
            3. Set the lock screen to "None."

            Note: On some devices, you may need to disable swipe-to-unlock in Developer Options.
        """.trimIndent()

        android.app.AlertDialog.Builder(requireContext()).setTitle("Disable All Screen Locks")
            .setMessage(dialogMessage).setPositiveButton("Go to Settings") { _, _ ->
                startActivity(Intent(Settings.ACTION_SECURITY_SETTINGS))
            }.setNegativeButton("Cancel") { dialog, _ ->
                dialog.dismiss()
                updateSwitchState()
            }.create().show()
    }

    @RequiresApi(Build.VERSION_CODES.M)
    private fun setupTimeSelection() {
        binding.btnSelectTime.setOnClickListener {
            TimePickerHelper.showTimePicker(requireContext()) { hour, minute ->
                val startTime = formatTime(hour, minute)
                TimePickerHelper.showTimePicker(requireContext()) { endHour, endMinute ->
                    val endTime = formatTime(endHour, endMinute)
                    binding.txtTimeFrame.text = "Start: $startTime, End: $endTime"
                    viewModel.saveVideoDetails(
                        viewModel.videoUri.value.orEmpty(), startTime, endTime
                    )
                    checkPrerequisites()
                }
            }
        }
    }

    private fun setupVideoUpload() {
        binding.btnUploadVideo.setOnClickListener {
            val intent = Intent(Intent.ACTION_PICK).apply { type = "video/*" }
            startActivityForResult(intent, Constants.VIDEO_PICK_REQUEST_CODE)
        }
    }

    private fun setupPlaybackNavigation() {
        binding.switchAutoPlay.visibility = View.GONE
        binding.btnNavigateToPlayback.apply {
            visibility = View.GONE
            setOnClickListener {
                requireActivity().findNavController(R.id.nav_host_fragment)
                    .navigate(R.id.action_homeFragment_to_videoFragment)
            }
        }
    }

    private fun checkPrerequisites() {
        if (viewModel.videoUri.value.isNullOrEmpty()) {
            showToast("Please upload a video")
            return
        }

        if (viewModel.startTime.value.isNullOrEmpty()) {
            showToast("Please set a time frame")
            return
        }
        binding.btnNavigateToPlayback.visibility = View.VISIBLE
        binding.switchAutoPlay.visibility = View.VISIBLE
    }

    private fun isVideoFormatSupported(uri: Uri): Boolean {
        val mimeType = requireContext().contentResolver.getType(uri)
        return mimeType in listOf("video/mp4", "video/x-matroska", "video/avi")
    }

    override fun onActivityResult(requestCode: Int, resultCode: Int, data: Intent?) {
        super.onActivityResult(requestCode, resultCode, data)
        if (requestCode == Constants.VIDEO_PICK_REQUEST_CODE && resultCode == Activity.RESULT_OK) {
            data?.data?.let { videoUri ->
                if (isVideoFormatSupported(videoUri)) {
                    binding.txtVideoUri.text = videoUri.toString()
                    viewModel.saveVideoDetails(
                        videoUri.toString(),
                        viewModel.startTime.value.orEmpty(),
                        viewModel.endTime.value.orEmpty()
                    )
                    checkPrerequisites()
                } else {
                    binding.btnNavigateToPlayback.visibility = View.GONE
                    showToast("Unsupported video format")
                }
            }
        }
    }

    private fun formatTime(hour: Int, minute: Int) = "$hour:${minute.toString().padStart(2, '0')}"

    private fun showToast(message: String) {
        Toast.makeText(requireContext(), message, Toast.LENGTH_SHORT).show()
    }

    override fun onDestroyView() {
        super.onDestroyView()
        _binding = null
    }
}