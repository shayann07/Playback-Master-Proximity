package com.shayan.playbackmaster.ui.fragments

import android.app.Activity
import android.app.AlertDialog
import android.app.KeyguardManager
import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.content.IntentFilter
import android.os.Build
import android.os.Bundle
import android.os.Handler
import android.os.Looper
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
import com.shayan.playbackmaster.databinding.FragmentHomeBinding
import com.shayan.playbackmaster.services.UsbProximityService
import com.shayan.playbackmaster.ui.viewmodel.AppViewModel
import com.shayan.playbackmaster.utils.AlarmUtils
import com.shayan.playbackmaster.utils.BatteryOptimizationHelper
import com.shayan.playbackmaster.utils.Constants
import com.shayan.playbackmaster.utils.TimePickerHelper
import java.util.Calendar

class HomeFragment : Fragment() {

    private var _binding: FragmentHomeBinding? = null
    private val binding get() = _binding!!
    private val viewModel: AppViewModel by activityViewModels()

    // ESP connection monitoring
    private var checkEspRunnable: Runnable? = null
    private val handler = Handler(Looper.getMainLooper())
    private var noEspDialog: AlertDialog? = null
    private var wasEspConnected = UsbProximityService.isConnected
    private var isEspReceiverRegistered = false


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

    private val espErrorReceiver = object : BroadcastReceiver() {
        override fun onReceive(context: Context?, intent: Intent?) {
            val errorMessage = intent?.getStringExtra("ERROR_MESSAGE")
            if (!errorMessage.isNullOrEmpty()) {
                showEspErrorDialog(errorMessage)
            }
        }
    }

    private val espConnectedReceiver = object : BroadcastReceiver() {
        override fun onReceive(context: Context?, intent: Intent?) {
            when (intent?.action) {
                "ACTION_USB_CONNECTED" -> {
                    Log.d("Connection", "USB Connected broadcast received")
                    wasEspConnected = true
                    forceConnectionCheck()
                }

                "ACTION_USB_DISCONNECTED" -> {
                    wasEspConnected = false
                    forceConnectionCheck()
                }
            }
        }
    }

    private fun forceConnectionCheck() {
        handler.post {
            checkEspAndTimeConditions()
        }
    }

    override fun onCreateView(
        inflater: LayoutInflater, container: ViewGroup?, savedInstanceState: Bundle?
    ): View {
        _binding = FragmentHomeBinding.inflate(inflater, container, false)
        return binding.root
    }


    @RequiresApi(Build.VERSION_CODES.TIRAMISU)
    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)

        val connectedFilter = IntentFilter().apply {
            addAction("ACTION_USB_CONNECTED")
            addAction("ACTION_USB_DISCONNECTED")
        }
        requireContext().registerReceiver(
            espConnectedReceiver, connectedFilter, Context.RECEIVER_NOT_EXPORTED
        )
        isEspReceiverRegistered = true // ✅ Set flag after registering
        requireContext().registerReceiver(
            espErrorReceiver, connectedFilter, Context.RECEIVER_NOT_EXPORTED
        )

        viewModel.loadVideoDetails()
        observeViewModel()
        setupTimeSelection()
        setupVideoUpload()
        setupPlaybackNavigation()
        setupBatteryOptimisation()
        initializeScreenLockSwitch()
    }

    private fun showEspErrorDialog(errorMessage: String) {
        if (wasEspConnected) { // ✅ Only show dialog if ESP was previously connected
            AlertDialog.Builder(requireContext()).setTitle("ESP Disconnected")
                .setMessage(errorMessage).setPositiveButton("OK") { dialog, _ -> dialog.dismiss() }
                .setCancelable(false).show()
        } else {
            Log.d("HomeFragment", "ESP was never connected before. Not showing dialog.")
        }
    }

    override fun onResume() {
        super.onResume()
        startPeriodicConnectionCheck()
        val filter = IntentFilter("ACTION_SHOW_SNACKBAR")
        ContextCompat.registerReceiver(
            requireContext(), snackbarReceiver, filter, ContextCompat.RECEIVER_NOT_EXPORTED
        )
    }

    override fun onPause() {
        super.onPause()
        requireContext().unregisterReceiver(snackbarReceiver)
        stopPeriodicConnectionCheck()
        dismissPersistentDialog()
    }

    private fun startPeriodicConnectionCheck() {
        checkEspRunnable = object : Runnable {
            override fun run() {
                checkEspAndTimeConditions()
                handler.postDelayed(this, 500) // Check every second
            }
        }
        handler.post(checkEspRunnable!!)
    }

    private fun stopPeriodicConnectionCheck() {
        checkEspRunnable?.let {
            handler.removeCallbacks(it)
            checkEspRunnable = null
        }
    }

    private fun checkEspAndTimeConditions() {
        val currentTime = System.currentTimeMillis()
        val startTime = viewModel.startTime.value?.let { convertTimeToMillis(it) } ?: 0
        val endTime = viewModel.endTime.value?.let { convertTimeToMillis(it) } ?: 0


        if (startTime == 0L || endTime == 0L) {
            Log.w(
                "HomeFragment",
                "checkEspAndTimeConditions: Start time or End time not set. Skipping check."
            )
            dismissPersistentDialog() // ✅ Hide dialog if time is missing
            return
        }

        val shouldCheck = currentTime in startTime..endTime

        when {
            !shouldCheck -> dismissPersistentDialog()
            !wasEspConnected -> showPersistentDialog()
            else -> dismissPersistentDialog()
        }
    }

    private fun convertTimeToMillis(time: String?): Long {
        if (time.isNullOrEmpty()) {
            Log.e("HomeFragment", "convertTimeToMillis: Received null or empty time string")
            return 0L // ✅ Return 0 if time is empty
        }

        return try {
            val parts = time.split(":")
            if (parts.size != 2) {
                Log.e("HomeFragment", "convertTimeToMillis: Invalid time format - $time")
                return 0L
            }

            val hour = parts[0].toIntOrNull() ?: return 0L
            val minute = parts[1].toIntOrNull() ?: return 0L

            val calendar = Calendar.getInstance()
            calendar.set(Calendar.HOUR_OF_DAY, hour)
            calendar.set(Calendar.MINUTE, minute)
            calendar.set(Calendar.SECOND, 0)
            calendar.set(Calendar.MILLISECOND, 0)

            calendar.timeInMillis
        } catch (e: Exception) {
            Log.e("HomeFragment", "convertTimeToMillis: Exception - ${e.message}", e)
            0L // ✅ Return 0 if an exception occurs
        }
    }

    private fun showPersistentDialog() {
        if (noEspDialog == null && isAdded) {
            Log.d("HomeFragment", "Showing ESP disconnected dialog.")
            noEspDialog = AlertDialog.Builder(requireContext()).setTitle("ESP Disconnected")
                .setMessage("No ESP detected. Please reconnect your device.").setCancelable(false)
                .create()
            noEspDialog?.show()
        }
    }

    private fun dismissPersistentDialog() {
        if (isAdded) {
            noEspDialog?.dismiss()
        }
        noEspDialog = null
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
    private fun setupTimeSelection() {
        binding.startTimeBtn.setOnClickListener {
            TimePickerHelper.showTimePicker(requireContext()) { hour, minute ->
                val startTime = formatTime(hour, minute)
                viewModel.saveVideoDetails(
                    viewModel.videoUri.value.orEmpty(), startTime, viewModel.endTime.value.orEmpty()
                )

                // Schedule playback alarm
                scheduleVideoAlarm()
            }
        }

        binding.endTimeBtn.setOnClickListener {
            TimePickerHelper.showTimePicker(requireContext()) { hour, minute ->
                val endTime = formatTime(hour, minute)
                viewModel.saveVideoDetails(
                    viewModel.videoUri.value.orEmpty(), viewModel.startTime.value.orEmpty(), endTime
                )

                // Schedule playback alarm
                scheduleVideoAlarm()
            }
        }
    }

    @RequiresApi(Build.VERSION_CODES.M)
    private fun scheduleVideoAlarm() {
        val videoUri = viewModel.videoUri.value
        val startTime = viewModel.startTime.value
        val endTime = viewModel.endTime.value

        if (!videoUri.isNullOrEmpty() && !startTime.isNullOrEmpty() && !endTime.isNullOrEmpty()) {
            AlarmUtils.scheduleDailyAlarm(requireContext(), videoUri, startTime, endTime)
            Log.d("HomeFragment", "Playback alarm scheduled at: $startTime")
        }
    }


    private fun setupVideoUpload() {
        binding.uploadBtn.setOnClickListener {
            val intent = Intent(Intent.ACTION_PICK).apply { type = "video/*" }
            startActivityForResult(intent, Constants.VIDEO_PICK_REQUEST_CODE)
        }
    }

    @RequiresApi(Build.VERSION_CODES.M)
    override fun onActivityResult(requestCode: Int, resultCode: Int, data: Intent?) {
        super.onActivityResult(requestCode, resultCode, data)
        if (requestCode == Constants.VIDEO_PICK_REQUEST_CODE && resultCode == Activity.RESULT_OK) {
            data?.data?.let { uri ->
                viewModel.saveVideoDetails(
                    uri.toString(),
                    viewModel.startTime.value.orEmpty(),
                    viewModel.endTime.value.orEmpty()
                )
            }
        }
    }


    private fun setupPlaybackNavigation() {
        binding.playBtn.setOnClickListener {
            requireActivity().findNavController(R.id.nav_host_fragment)
                .navigate(R.id.action_homeFragment_to_videoFragment)
        }
    }

    private fun updateVisibilityBasedOnVideoUpload() {
        val isVideoUploaded = !viewModel.videoUri.value.isNullOrEmpty()
        binding.circularShapeLower.visibility = if (isVideoUploaded) View.VISIBLE else View.GONE
        binding.videoUriTxt.visibility = if (isVideoUploaded) View.VISIBLE else View.GONE
    }

    private fun formatTime(hour: Int, minute: Int): String {
        return String.format("%02d:%02d", hour, minute)
    }

    private fun showToast(message: String) {
        Toast.makeText(requireContext(), message, Toast.LENGTH_SHORT).show()
    }

    @RequiresApi(Build.VERSION_CODES.M)
    private fun initializeScreenLockSwitch() {
        updateSwitchState()
        binding.disableScreenLock.setOnCheckedChangeListener { _, isChecked ->
            if (isChecked && !isLockScreenDisabled(requireContext())) {
                showDisableLockBottomSheet()
            }
            updateSwitchState()
        }
    }

    @RequiresApi(Build.VERSION_CODES.M)
    private fun updateSwitchState() {
        binding.disableScreenLock.isChecked = isLockScreenDisabled(requireContext())
    }

    @RequiresApi(Build.VERSION_CODES.M)
    private fun isLockScreenDisabled(context: Context): Boolean {
        return !(context.getSystemService(Context.KEYGUARD_SERVICE) as KeyguardManager).isDeviceSecure
    }

    @RequiresApi(Build.VERSION_CODES.M)
    private fun showDisableLockBottomSheet() {
        val bottomSheetDialog = BottomSheetDialog(requireContext())
        val bottomSheetView = layoutInflater.inflate(R.layout.bottom_sheet_instructions, null)
        bottomSheetDialog.setContentView(bottomSheetView)

        bottomSheetView.findViewById<Button>(R.id.btn_go_to_settings).setOnClickListener {
            startActivity(Intent(Settings.ACTION_SECURITY_SETTINGS))
            bottomSheetDialog.dismiss()
        }

        bottomSheetView.findViewById<Button>(R.id.btn_close).setOnClickListener {
            bottomSheetDialog.dismiss()
            updateSwitchState()
        }

        bottomSheetDialog.show()
    }


    private fun setupBatteryOptimisation() {
        // If the device is Android M (API 23) or higher, navigate to battery optimization settings
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.M && !BatteryOptimizationHelper.isBatteryOptimized(
                requireContext()
            )
        ) {
            val intent = Intent(Settings.ACTION_IGNORE_BATTERY_OPTIMIZATION_SETTINGS)
            try {
                val builder = AlertDialog.Builder(requireContext())
                builder.setTitle("Battery Optimization").setMessage(
                    "To ensure the best performance, please disable battery optimization for this app.Go to setting " + "and find PlaybackMaster and turn off battery optimization"
                ).setPositiveButton("Go to Settings") { dialog, _ ->
                    // Open battery optimization settings
                    startActivity(intent)
                    dialog.dismiss() // Close the dialog
                }.setNegativeButton("Cancel") { dialog, _ ->
                    dialog.dismiss() // Close the dialog
                }.setCancelable(false) // Prevent closing the dialog by tapping outside

                // Create and show the dialog
                val dialog = builder.create()
                dialog.show()

                Toast.makeText(requireContext(), "Turn off Battery Optimization", Toast.LENGTH_LONG)
                    .show()
                // Successfully opened the settings
            } catch (e: Exception) {
                e.printStackTrace()
                // Failed to open settings
            }
        }
    }


    override fun onDestroyView() {
        super.onDestroyView()
        if (isEspReceiverRegistered) {
            requireContext().unregisterReceiver(espConnectedReceiver)
            isEspReceiverRegistered = false // ✅ Reset flag
        }

        requireContext().unregisterReceiver(espErrorReceiver)
        _binding = null
    }
}