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
import android.widget.Button
import android.widget.Toast
import androidx.annotation.RequiresApi
import androidx.fragment.app.Fragment
import androidx.fragment.app.activityViewModels
import androidx.navigation.findNavController
import com.google.android.material.bottomsheet.BottomSheetDialog
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
    private fun updateSwitchState() {
        val isDisabled = isLockScreenDisabled(requireContext())
        binding.disableScreenLock.isChecked = isDisabled
        Log.d("HomeFragment", "Switch updated to: $isDisabled")
    }

    @RequiresApi(Build.VERSION_CODES.M)
    private fun isLockScreenDisabled(context: Context): Boolean {
        val keyguardManager = context.getSystemService(Context.KEYGUARD_SERVICE) as KeyguardManager
        val isDeviceSecure = keyguardManager.isDeviceSecure

        Log.d("HomeFragment", "isDeviceSecure: $isDeviceSecure")
        return !isDeviceSecure
    }

    @RequiresApi(Build.VERSION_CODES.M)
    private fun showDisableLockBottomSheet() {
        if (!isAdded) {
            Log.e("HomeFragment", "Fragment not attached to activity. Cannot show bottom sheet.")
            return
        }

        // Create a BottomSheetDialog
        val bottomSheetDialog = BottomSheetDialog(requireContext())
        val bottomSheetView = layoutInflater.inflate(R.layout.bottom_sheet_instructions, null)

        // Set the view for the BottomSheetDialog
        bottomSheetDialog.setContentView(bottomSheetView)

        // Access views in the bottom sheet
        val btnGoToSettings = bottomSheetView.findViewById<Button>(R.id.btn_go_to_settings)
        val btnCancel = bottomSheetView.findViewById<Button>(R.id.btn_close)

        // Set button click listeners
        btnGoToSettings.setOnClickListener {
            startActivity(Intent(Settings.ACTION_SECURITY_SETTINGS))
            bottomSheetDialog.dismiss()
        }

        btnCancel.setOnClickListener {
            bottomSheetDialog.dismiss()
            updateSwitchState() // Revert the switch state if the user cancels
        }

        // Show the BottomSheetDialog
        bottomSheetDialog.show()
    }

    @RequiresApi(Build.VERSION_CODES.M)
    private fun initializeScreenLockSwitch() {
        updateSwitchState()

        binding.disableScreenLock.setOnCheckedChangeListener { _, isChecked ->
            if (isChecked) {
                if (isLockScreenDisabled(requireContext())) {
                    showToast("Screen lock is already disabled.")
                } else {
                    showDisableLockBottomSheet()
                    updateSwitchState() // Revert the switch state after showing the bottom sheet
                }
            } else {
                showToast("Screen lock settings remain unchanged.")
                updateSwitchState() // Ensure the state remains consistent
            }
        }
    }


    @RequiresApi(Build.VERSION_CODES.M)
    private fun setupTimeSelection() {
        binding.startTimeBtn.setOnClickListener {
            TimePickerHelper.showTimePicker(requireContext()) { hour, minute ->
                val startTime = formatTime(hour, minute)

                    binding.startTimeBtn.text= startTime

                    checkPrerequisites()
                }
            }
        binding.endTimeBtn.setOnClickListener{
            TimePickerHelper.showTimePicker(requireContext()) { hour, minute ->
                val endTime = formatTime(hour, minute)

                binding.startTimeBtn.text= endTime

                checkPrerequisites()
            }
        }
        }


    private fun setupVideoUpload() {
        binding.uploadBtn.setOnClickListener {
            val intent = Intent(Intent.ACTION_PICK).apply { type = "video/*" }
            startActivityForResult(intent, Constants.VIDEO_PICK_REQUEST_CODE)
        }
    }

    private fun setupPlaybackNavigation() {
        binding.autoplayBtn.visibility = View.GONE
        binding.playBtn.apply {
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
        binding.playBtn.visibility = View.VISIBLE
        binding.autoplayBtn.visibility = View.VISIBLE
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
                    binding.videoUriTxt.text = videoUri.toString()
                    viewModel.saveVideoDetails(
                        videoUri.toString(),
                        viewModel.startTime.value.orEmpty(),
                        viewModel.endTime.value.orEmpty()
                    )
                    checkPrerequisites()
                } else {
                    binding.playBtn.visibility = View.GONE
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