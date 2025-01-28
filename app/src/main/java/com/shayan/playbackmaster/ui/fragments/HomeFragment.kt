package com.shayan.playbackmaster.ui.fragments

import android.app.Activity
import android.app.AlarmManager
import android.app.KeyguardManager
import android.content.BroadcastReceiver
import android.content.ComponentName
import android.content.Context
import android.content.Intent
import android.content.IntentFilter
import android.content.pm.PackageManager
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
import androidx.core.content.ContextCompat
import androidx.fragment.app.Fragment
import androidx.fragment.app.activityViewModels
import androidx.navigation.findNavController
import com.google.android.material.bottomsheet.BottomSheetDialog
import com.google.android.material.snackbar.Snackbar
import com.shayan.playbackmaster.R
import com.shayan.playbackmaster.data.preferences.PreferencesHelper
import com.shayan.playbackmaster.databinding.FragmentHomeBinding
import com.shayan.playbackmaster.receivers.BootReceiver
import com.shayan.playbackmaster.ui.viewmodel.AppViewModel
import com.shayan.playbackmaster.utils.AlarmUtils
import com.shayan.playbackmaster.utils.Constants
import com.shayan.playbackmaster.utils.TimePickerHelper

class HomeFragment : Fragment() {

    private var _binding: FragmentHomeBinding? = null
    private val binding get() = _binding!!
    private val viewModel: AppViewModel by activityViewModels()

    private val snackbarReceiver = object : BroadcastReceiver() {
        override fun onReceive(context: Context?, intent: Intent?) {
            if (intent?.action == "ACTION_SHOW_SNACKBAR") {
                val message = intent.getStringExtra("MESSAGE")
                message?.let {
                    Snackbar.make(binding.root, it, Snackbar.LENGTH_LONG).show()
                }
            }
        }
    }

    override fun onCreateView(
        inflater: LayoutInflater, container: ViewGroup?, savedInstanceState: Bundle?
    ): View {
        _binding = FragmentHomeBinding.inflate(inflater, container, false)
        return binding.root
    }

    @RequiresApi(Build.VERSION_CODES.M)
    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)

        viewModel.loadVideoDetails() // Load saved video details from preferences
        observeViewModel() // Observe LiveData from ViewModel
        setupTimeSelection() // Configure time pickers
        setupVideoUpload() // Configure video upload functionality
        setupPlaybackNavigation() // Configure navigation to playback screen
        initializeScreenLockSwitch() // Configure screen lock toggle
    }

    override fun onStart() {
        super.onStart()
        val filter = IntentFilter("ACTION_SHOW_SNACKBAR")
        ContextCompat.registerReceiver(
            requireContext(),
            snackbarReceiver,
            filter,
            ContextCompat.RECEIVER_NOT_EXPORTED
        )
    }

    override fun onStop() {
        super.onStop()
        requireContext().unregisterReceiver(snackbarReceiver)
    }

    private fun observeViewModel() {
        viewModel.videoUri.observe(viewLifecycleOwner) { uri ->
            binding.videoUriTxt.text = uri ?: "No video selected"
            updateVisibilityBasedOnVideoUpload()
        }

        viewModel.startTime.observe(viewLifecycleOwner) {
            binding.startTimeBtn.text = it ?: "Select Start Time"
            updateVisibilityBasedOnVideoUpload()
        }

        viewModel.endTime.observe(viewLifecycleOwner) {
            binding.endTimeBtn.text = it ?: "Select End Time"
            updateVisibilityBasedOnVideoUpload()
        }
    }


    @RequiresApi(Build.VERSION_CODES.M)
    private fun scheduleAlarm() {
        val videoUri = viewModel.videoUri.value.orEmpty()
        val startTime = viewModel.startTime.value.orEmpty()
        val endTime = viewModel.endTime.value.orEmpty()
        val bootReceiver = context?.let { ComponentName(it, BootReceiver::class.java) }

        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU && bootReceiver != null) {
            context?.packageManager?.setComponentEnabledSetting(
                bootReceiver,
                PackageManager.COMPONENT_ENABLED_STATE_ENABLED, // Enable or disable the receiver
                PackageManager.DONT_KILL_APP // Optional flags
            )
        }

        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
            val alarmManager =
                requireContext().getSystemService(Context.ALARM_SERVICE) as AlarmManager
            if (!alarmManager.canScheduleExactAlarms()) {
                navigateToAlarmSettings()
                showToast("Permission required to schedule exact alarms. Please enable it in settings.")
                return
            }
        }

        if (videoUri.isNotEmpty() && startTime.isNotEmpty() && endTime.isNotEmpty()) {
            // Schedule the alarm
            AlarmUtils.scheduleDailyAlarm(requireContext(), videoUri, startTime, endTime)

            // Save the details to preferences
            val preferencesHelper = PreferencesHelper(requireContext())
            preferencesHelper.saveVideoDetails(videoUri, startTime, endTime)

            showToast("Playback scheduled at $startTime daily!")
        } else {
            showToast("Please set a valid video, start time, and end time.")
        }
    }

    @RequiresApi(Build.VERSION_CODES.S)
    private fun navigateToAlarmSettings() {
        val intent = Intent().apply {
            action = android.provider.Settings.ACTION_REQUEST_SCHEDULE_EXACT_ALARM
            data = Uri.parse("package:${requireContext().packageName}")
        }
        startActivity(intent)
    }

    @RequiresApi(Build.VERSION_CODES.M)
    private fun setupTimeSelection() {
        binding.startTimeBtn.setOnClickListener {
            TimePickerHelper.showTimePicker(requireContext()) { hour, minute ->
                val startTime = formatTime(hour, minute)
                viewModel.saveVideoDetails(
                    viewModel.videoUri.value.orEmpty(), startTime, viewModel.endTime.value.orEmpty()
                )
                scheduleAlarm()
            }
        }

        binding.endTimeBtn.setOnClickListener {
            TimePickerHelper.showTimePicker(requireContext()) { hour, minute ->
                val endTime = formatTime(hour, minute)
                viewModel.saveVideoDetails(
                    viewModel.videoUri.value.orEmpty(), viewModel.startTime.value.orEmpty(), endTime
                )
                scheduleAlarm()
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
        binding.playBtn.setOnClickListener {
            requireActivity().findNavController(R.id.nav_host_fragment)
                .navigate(R.id.action_homeFragment_to_videoFragment)
        }
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
                }
            } else {
                showToast("Screen lock settings remain unchanged.")
            }
            updateSwitchState()
        }
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
        return !keyguardManager.isDeviceSecure
    }

    @RequiresApi(Build.VERSION_CODES.M)
    private fun showDisableLockBottomSheet() {
        val bottomSheetDialog = BottomSheetDialog(requireContext())
        val bottomSheetView = layoutInflater.inflate(R.layout.bottom_sheet_instructions, null)
        bottomSheetDialog.setContentView(bottomSheetView)

        val btnGoToSettings = bottomSheetView.findViewById<Button>(R.id.btn_go_to_settings)
        val btnCancel = bottomSheetView.findViewById<Button>(R.id.btn_close)

        btnGoToSettings.setOnClickListener {
            startActivity(Intent(Settings.ACTION_SECURITY_SETTINGS))
            bottomSheetDialog.dismiss()
        }

        btnCancel.setOnClickListener {
            bottomSheetDialog.dismiss()
            updateSwitchState()
        }

        bottomSheetDialog.show()
    }

    private fun updateVisibilityBasedOnVideoUpload() {
        val isVideoUploaded = !viewModel.videoUri.value.isNullOrEmpty()

        binding.circularShapeLower.visibility = if (isVideoUploaded) View.VISIBLE else View.GONE
        binding.videoUriTxt.visibility = if (isVideoUploaded) View.VISIBLE else View.GONE
    }

    @RequiresApi(Build.VERSION_CODES.M)
    override fun onActivityResult(requestCode: Int, resultCode: Int, data: Intent?) {
        super.onActivityResult(requestCode, resultCode, data)
        if (requestCode == Constants.VIDEO_PICK_REQUEST_CODE && resultCode == Activity.RESULT_OK) {
            data?.data?.let { videoUri ->
                viewModel.saveVideoDetails(
                    videoUri.toString(),
                    viewModel.startTime.value.orEmpty(),
                    viewModel.endTime.value.orEmpty()
                )
                scheduleAlarm()
            }
        }
    }

    private fun formatTime(hour: Int, minute: Int): String {
        return "$hour:${minute.toString().padStart(2, '0')}"
    }

    private fun showToast(message: String) {
        Toast.makeText(requireContext(), message, Toast.LENGTH_SHORT).show()
    }

    override fun onDestroyView() {
        super.onDestroyView()
        _binding = null
    }
}