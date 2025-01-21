package com.shayan.playbackmaster.ui.fragments

import android.app.Activity
import android.app.KeyguardManager
import android.content.Context
import android.content.Intent
import android.net.Uri
import android.os.Build
import android.os.Bundle
import android.provider.Settings
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

        // Set up time selection
        binding.btnSelectTime.setOnClickListener {
            TimePickerHelper.showTimePicker(requireContext()) { hour, minute ->
                val startTime = "$hour:${minute.toString().padStart(2, '0')}"
                TimePickerHelper.showTimePicker(requireContext()) { endHour, endMinute ->
                    val endTime = "$endHour:${endMinute.toString().padStart(2, '0')}"
                    binding.txtTimeFrame.text = "Start: $startTime, End: $endTime"
                    viewModel.saveVideoDetails(
                        viewModel.videoUri.value.orEmpty(), startTime, endTime
                    )
                    checkPrerequisites()
                }
            }
        }

        // Set up video upload
        binding.btnUploadVideo.setOnClickListener {
            val intent = Intent(Intent.ACTION_PICK).apply { type = "video/*" }
            startActivityForResult(intent, Constants.VIDEO_PICK_REQUEST_CODE)
        }

        // Play button initially hidden
        binding.btnNavigateToPlayback.visibility = View.GONE
        binding.btnNavigateToPlayback.setOnClickListener {
            val navController = requireActivity().findNavController(R.id.nav_host_fragment)
            navController.navigate(R.id.action_homeFragment_to_videoFragment)
        }

        // Initial switch state based on lock status
        binding.switchScreenLock.isChecked = isLockScreenCompletelyDisabled(requireContext())

        // Screen Lock Switch with condition
        binding.switchScreenLock.setOnCheckedChangeListener { _, isChecked ->
            if (isChecked) {
                if (isLockScreenCompletelyDisabled(requireContext())) {
                    Toast.makeText(
                        requireContext(), "Screen lock is already disabled.", Toast.LENGTH_SHORT
                    ).show()
                } else {
                    Toast.makeText(
                        requireContext(),
                        "Please disable all screen locks, including swipe-to-unlock, in your device settings.",
                        Toast.LENGTH_LONG
                    ).show()
                    binding.switchScreenLock.isChecked = false // Prevent toggle to ON
                }
            } else {
                Toast.makeText(
                    requireContext(), "Screen lock settings remain unchanged.", Toast.LENGTH_SHORT
                ).show()
            }
        }
    }

    @RequiresApi(Build.VERSION_CODES.M)
    private fun isLockScreenCompletelyDisabled(context: Context): Boolean {
        val keyguardManager = context.getSystemService(Context.KEYGUARD_SERVICE) as KeyguardManager

        // Check if any secure lock (PIN, password, pattern) is enabled
        val isDeviceSecure = keyguardManager.isDeviceSecure

        // Check if swipe-to-unlock is enabled
        val isSwipeToUnlockEnabled = try {
            val lockDisabled = Settings.Secure.getInt(
                context.contentResolver, "lock_screen_disabled"
            )
            lockDisabled != 1 // If not disabled, swipe-to-unlock is active
        } catch (e: Settings.SettingNotFoundException) {
            true // Default to true if the setting is not found
        }

        // Return true only if both secure lock and swipe-to-unlock are disabled
        return !isDeviceSecure && !isSwipeToUnlockEnabled
    }

    private fun showDisableLockDialog() {
        val dialogMessage = """
        To ensure seamless operation, please disable all screen locks, including swipe-to-unlock:
        1. Go to your device settings.
        2. Navigate to "Security" or "Screen Lock."
        3. Set the lock screen to "None."
        
        Note: On some devices, you may need to disable swipe-to-unlock in Developer Options.
    """.trimIndent()

        val dialog =
            android.app.AlertDialog.Builder(requireContext()).setTitle("Disable All Screen Locks")
                .setMessage(dialogMessage).setPositiveButton("Go to Settings") { _, _ ->
                    startActivity(Intent(Settings.ACTION_SECURITY_SETTINGS))
                }.setNegativeButton("Cancel") { dialog, _ ->
                    dialog.dismiss()
                    binding.switchScreenLock.isChecked = false // Revert the switch
                }.create()

        dialog.show()
    }

    private fun checkPrerequisites() {
        if (viewModel.videoUri.value.isNullOrEmpty()) {
            Toast.makeText(requireContext(), "Please upload a video", Toast.LENGTH_SHORT).show()
            return
        }

        if (viewModel.startTime.value.isNullOrEmpty()) {
            Toast.makeText(requireContext(), "Please set a time frame", Toast.LENGTH_SHORT).show()
            return
        }
        binding.btnNavigateToPlayback.visibility = View.VISIBLE
    }

    private fun isVideoFormatSupported(uri: Uri): Boolean {
        val contentResolver = requireContext().contentResolver
        val mimeType = contentResolver.getType(uri)
        return mimeType == "video/mp4" || mimeType == "video/x-matroska" || mimeType == "video/avi"
    }

    override fun onActivityResult(requestCode: Int, resultCode: Int, data: Intent?) {
        super.onActivityResult(requestCode, resultCode, data)
        if (requestCode == Constants.VIDEO_PICK_REQUEST_CODE && resultCode == Activity.RESULT_OK) {
            val videoUri: Uri? = data?.data
            if (videoUri != null) {
                // Validate video format using MIME type
                if (isVideoFormatSupported(videoUri)) {
                    binding.txtVideoUri.text = videoUri.toString()
                    viewModel.saveVideoDetails(
                        videoUri.toString(),
                        viewModel.startTime.value.orEmpty(),
                        viewModel.endTime.value.orEmpty()
                    )
                    checkPrerequisites()
                } else {
                    binding.btnNavigateToPlayback.visibility = View.GONE // Hide Play button
                    Toast.makeText(requireContext(), "Unsupported video format", Toast.LENGTH_SHORT)
                        .show()
                }
            }
        }
    }

    override fun onDestroyView() {
        super.onDestroyView()
        _binding = null
    }
}