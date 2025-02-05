package com.shayan.playbackmaster.ui.fragments

import android.app.Activity
import android.app.AlertDialog
import android.app.KeyguardManager
import android.app.PendingIntent
import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.content.IntentFilter
import android.hardware.usb.UsbDevice
import android.hardware.usb.UsbManager
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
import com.shayan.playbackmaster.receivers.ProximityReceiver
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

    // We no longer use a polling Runnable.
    private val handler = Handler(Looper.getMainLooper())
    private var noEspDialog: AlertDialog? = null

    // We now rely on the static variable from UsbProximityService directly.
    // private var wasEspConnected = UsbProximityService.isConnected  // Not used now.
    private var isEspReceiverRegistered = false

    private val snackbarReceiver = object : BroadcastReceiver() {
        override fun onReceive(context: Context?, intent: Intent?) {
            if (intent?.action == "ACTION_SHOW_SNACKBAR") {
                val message = intent.getStringExtra("MESSAGE")
                Log.d("HomeFragment", "Snackbar message received: $message")
                message?.let {
                    Snackbar.make(binding.root, it, Snackbar.LENGTH_LONG).show()
                }
            }
        }
    }


    private val espConnectedReceiver = object : BroadcastReceiver() {
        override fun onReceive(context: Context?, intent: Intent?) {
            when (intent?.action) {
                UsbManager.ACTION_USB_DEVICE_ATTACHED -> {
                    val usbManager = context?.getSystemService(Context.USB_SERVICE) as UsbManager
                    val device: UsbDevice? = intent.getParcelableExtra(UsbManager.EXTRA_DEVICE)

                    if (device != null) {
                        Log.d("USBReceiver", "USB device attached: ${device.deviceName}")

                        UsbProximityService.isConnected = true
                        dismissPersistentDialog()
                        if (!usbManager.hasPermission(device)) {
                            requestUsbPermission(device) // Request permission only if not granted
                        }
                    }
                }

                UsbManager.ACTION_USB_DEVICE_DETACHED -> {
                    val device: UsbDevice? = intent.getParcelableExtra(UsbManager.EXTRA_DEVICE)
                    Log.d("USBReceiver", "USB device detached: ${device?.deviceName}")
                    UsbProximityService.isConnected = false
                    if (shouldShowDialogBasedOnTime()) {
                        showPersistentDialog()
                    }
                }
            }
        }
    }


    override fun onCreateView(
        inflater: LayoutInflater, container: ViewGroup?, savedInstanceState: Bundle?
    ): View {
        _binding = FragmentHomeBinding.inflate(inflater, container, false)
        Log.d("HomeFragment", "View created")
        return binding.root
    }

    @RequiresApi(Build.VERSION_CODES.TIRAMISU)
    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)
        Log.d("HomeFragment", "View created")

        // Initialize UI components and ViewModel
        viewModel.loadVideoDetails()
        observeViewModel()
        setupTimeSelection()
        setupVideoUpload()
        setupPlaybackNavigation()
        setupBatteryOptimisation()
        initializeScreenLockSwitch()

        // Register USB device receiver dynamically
        val usbFilter = IntentFilter().apply {
            addAction(UsbManager.ACTION_USB_DEVICE_ATTACHED)
            addAction(UsbManager.ACTION_USB_DEVICE_DETACHED)
        }
        requireContext().registerReceiver(espConnectedReceiver, usbFilter)
        isEspReceiverRegistered = true

        // Check for connected USB devices on launch
        val usbManager = requireContext().getSystemService(Context.USB_SERVICE) as UsbManager
        val deviceList = usbManager.deviceList
        UsbProximityService.isConnected = deviceList.isNotEmpty()
        Log.d("HomeFragment", "Initial USB connection state: ${UsbProximityService.isConnected}")

        /*.any { device ->
            // Add device filtering logic here (e.g., vendor/product ID checks)
            // For now, assume any connected device is the ESP32
            true
        }*/

        // Update dialog state based on ESP connection and time window
        updateEspDialogBasedOnConditions()
    }

    private fun requestUsbPermission(device: UsbDevice) {
        val usbManager = requireContext().getSystemService(Context.USB_SERVICE) as UsbManager

        // Use an explicit intent to avoid security issues
        val usbPermissionIntent = Intent(requireContext(), ProximityReceiver::class.java).apply {
            action = ProximityReceiver.USB_PERMISSION_ACTION
        }

        val permissionIntent = PendingIntent.getBroadcast(
            requireContext(),
            0,
            usbPermissionIntent,
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_MUTABLE // Ensures mutability
        )

        usbManager.requestPermission(device, permissionIntent)
        Log.d("HomeFragment", "Requesting USB permission for device: ${device.deviceName}")
    }

    // Define a periodic runnable for checking dialog state
    private val dialogCheckRunnable = object : Runnable {
        override fun run() {
            updateEspDialogBasedOnConditions()
            // Adjust the interval (in milliseconds) as needed.
            handler.postDelayed(this, 1_000L) // re-check every 1 seconds
        }
    }

    override fun onResume() {
        super.onResume()
        Log.d("HomeFragment", "Fragment resumed")

        // Register the snackbar receiver
        val filter = IntentFilter("ACTION_SHOW_SNACKBAR")
        ContextCompat.registerReceiver(
            requireContext(), snackbarReceiver, filter, ContextCompat.RECEIVER_NOT_EXPORTED
        )

        // Update dialog state immediately on resume
        updateEspDialogBasedOnConditions()

        // Start periodic checks to ensure the dialog state is correct within the time window
        handler.post(dialogCheckRunnable)
    }

    override fun onPause() {
        super.onPause()
        Log.d("HomeFragment", "Fragment paused")

        // Unregister the snackbar receiver
        requireContext().unregisterReceiver(snackbarReceiver)

        // Dismiss the persistent dialog
        dismissPersistentDialog()

        // Remove the periodic dialog check callbacks to prevent leaks or unnecessary processing
        handler.removeCallbacks(dialogCheckRunnable)
    }


    /**
     * Checks the current time and, if within the start/end window, shows or dismisses
     * the dialog based on the ESP connection status.
     */
    private fun updateEspDialogBasedOnConditions() {
        Log.d(
            "HomeFragment",
            "Checking ESP connection at the scheduled start time and within time window."
        )

        val currentTime = System.currentTimeMillis()
        val startTime = viewModel.startTime.value?.let { convertTimeToMillis(it) } ?: 0L
        val endTime = viewModel.endTime.value?.let { convertTimeToMillis(it) } ?: 0L

        if (startTime == 0L || endTime == 0L) {
            dismissPersistentDialog() // Hide dialog if time is not set
            return
        }

        val withinTimeWindow = currentTime in startTime..endTime
        val isStartTime =
            (currentTime in startTime..(startTime + 60_000L)) // 1-minute threshold at start time

        Log.d(
            "HomeFragment",
            "Current Time: $currentTime, Start Time: $startTime, End Time: $endTime, Within Window: $withinTimeWindow, Is Start Time: $isStartTime"
        )

        if (isStartTime || withinTimeWindow) {
            if (!UsbProximityService.isConnected) {
                Log.d("HomeFragment", "ESP is not connected. Showing ESP Disconnected dialog.")
                showPersistentDialog()
            } else {
                Log.d("HomeFragment", "ESP is connected. Dismissing ESP Disconnected dialog.")
                dismissPersistentDialog()
            }
        } else {
            dismissPersistentDialog()
        }
    }


    /**
     * Returns true if the current time is within the set start and end times.
     */
    private fun shouldShowDialogBasedOnTime(): Boolean {
        val currentTime = System.currentTimeMillis()
        val startTime = viewModel.startTime.value?.let { convertTimeToMillis(it) } ?: 0L
        val endTime = viewModel.endTime.value?.let { convertTimeToMillis(it) } ?: 0L

        if (startTime == 0L || endTime == 0L) {
            return false
        }
        return currentTime in startTime..endTime
    }

    private fun convertTimeToMillis(time: String?): Long {
        if (time.isNullOrEmpty()) {
            Log.e("HomeFragment", "convertTimeToMillis: Received null or empty time string")
            return 0L
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
            0L
        }
    }

    private fun showPersistentDialog() {
        requireActivity().runOnUiThread {
            if (noEspDialog == null && isAdded) {
                Log.d("HomeFragment", "Showing ESP disconnected dialog.")
                noEspDialog = AlertDialog.Builder(requireContext()).setTitle("ESP Disconnected")
                    .setMessage("No ESP detected. Please reconnect your device.")
                    .setCancelable(false).create()
                noEspDialog?.show()
            }
        }
    }

    private fun dismissPersistentDialog() {
        requireActivity().runOnUiThread {
            if (isAdded && noEspDialog?.isShowing == true) {
                noEspDialog?.dismiss()
                Log.d("HomeFragment", "Persistent dialog dismissed.")
            }
            noEspDialog = null
        }
    }

    private fun observeViewModel() {
        viewModel.videoUri.observe(viewLifecycleOwner) { uri ->
            binding.videoUriTxt.text = uri ?: "No video selected"
            updateVisibilityBasedOnVideoUpload()
        }

        viewModel.startTime.observe(viewLifecycleOwner) {
            binding.startTimeBtn.text = it ?: "Select Start Time"
            updateVisibilityBasedOnVideoUpload()
            updateEspDialogBasedOnConditions() // Update dialog if the start time changes
        }

        viewModel.endTime.observe(viewLifecycleOwner) {
            binding.endTimeBtn.text = it ?: "Select End Time"
            updateVisibilityBasedOnVideoUpload()
            updateEspDialogBasedOnConditions() // Update dialog if the end time changes
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
                scheduleVideoAlarm()
                updateEspDialogBasedOnConditions()
            }
        }

        binding.endTimeBtn.setOnClickListener {
            TimePickerHelper.showTimePicker(requireContext()) { hour, minute ->
                val endTime = formatTime(hour, minute)
                viewModel.saveVideoDetails(
                    viewModel.videoUri.value.orEmpty(), viewModel.startTime.value.orEmpty(), endTime
                )
                scheduleVideoAlarm()
                updateEspDialogBasedOnConditions()
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
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.M && !BatteryOptimizationHelper.isBatteryOptimized(
                requireContext()
            )
        ) {
            val intent = Intent(Settings.ACTION_IGNORE_BATTERY_OPTIMIZATION_SETTINGS)
            try {
                val builder = AlertDialog.Builder(requireContext())
                builder.setTitle("Battery Optimization")
                    .setMessage("To ensure the best performance, please disable battery optimization for this app. Go to settings and find PlaybackMaster and turn off battery optimization")
                    .setPositiveButton("Go to Settings") { dialog, _ ->
                        startActivity(intent)
                        dialog.dismiss()
                    }.setNegativeButton("Cancel") { dialog, _ ->
                        dialog.dismiss()
                    }.setCancelable(false)
                val dialog = builder.create()
                dialog.show()

                Toast.makeText(requireContext(), "Turn off Battery Optimization", Toast.LENGTH_LONG)
                    .show()
            } catch (e: Exception) {
                e.printStackTrace()
            }
        }
    }

    override fun onDestroyView() {
        super.onDestroyView()
        Log.d("HomeFragment", "Destroying view and unregistering ESP receivers")
        if (isEspReceiverRegistered) {
            requireContext().unregisterReceiver(espConnectedReceiver)
            isEspReceiverRegistered = false
        }

        _binding = null
    }
}