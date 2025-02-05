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
import androidx.navigation.fragment.findNavController
import com.google.android.material.bottomsheet.BottomSheetDialog
import com.google.android.material.snackbar.Snackbar
import com.shayan.playbackmaster.R
import com.shayan.playbackmaster.databinding.FragmentHomeBinding
import com.shayan.playbackmaster.services.PlaybackService
import com.shayan.playbackmaster.services.UsbProximityService
import com.shayan.playbackmaster.ui.viewmodel.AppViewModel
import com.shayan.playbackmaster.utils.AlarmUtils
import com.shayan.playbackmaster.utils.BatteryOptimizationHelper
import com.shayan.playbackmaster.utils.Constants
import com.shayan.playbackmaster.utils.TimePickerHelper
import java.util.Calendar

class HomeFragment : Fragment() {

    companion object {
        // Your custom USB permission action
        const val USB_PERMISSION_ACTION = "com.shayan.playbackmaster.USB_PERMISSION"
    }

    private var _binding: FragmentHomeBinding? = null
    private val binding get() = _binding!!
    private val viewModel: AppViewModel by activityViewModels()

    private val handler = Handler(Looper.getMainLooper())
    private var noEspDialog: AlertDialog? = null
    private var isEspReceiverRegistered = false

    // ----------------------------------------------------------------------
    // 1) USB Permission Receiver
    // ----------------------------------------------------------------------
    /**
     * Listens for the result of our dynamic USB permission request.
     * When the user grants or denies permission, we get a broadcast
     * with action = USB_PERMISSION_ACTION.
     */
    private val usbPermissionReceiver = object : BroadcastReceiver() {
        override fun onReceive(context: Context, intent: Intent) {
            if (intent.action == USB_PERMISSION_ACTION) {
                synchronized(this) {
                    val device = intent.getParcelableExtra<UsbDevice>(UsbManager.EXTRA_DEVICE)
                    val granted = intent.getBooleanExtra(UsbManager.EXTRA_PERMISSION_GRANTED, false)
                    if (granted && device != null) {
                        Log.d("HomeFragment", "Permission granted for device: ${device.deviceName}")
                        // If needed, open the device or pass it to your service:
                        // openUsbDevice(device)
                    } else {
                        Log.e("HomeFragment", "Permission denied for device: ${device?.deviceName}")
                    }
                }
            }
        }
    }

    // ----------------------------------------------------------------------
    // 2) USB Attach/Detach Receiver
    // ----------------------------------------------------------------------
    private val espConnectedReceiver = object : BroadcastReceiver() {
        override fun onReceive(context: Context?, intent: Intent?) {
            when (intent?.action) {
                UsbManager.ACTION_USB_DEVICE_ATTACHED -> {
                    val usbManager = context?.getSystemService(Context.USB_SERVICE) as UsbManager
                    val device: UsbDevice? = intent.getParcelableExtra(UsbManager.EXTRA_DEVICE)
                    if (device != null) {
                        Log.d(
                            "USBReceiver",
                            "USB device attached: vendorId=${device.vendorId}, productId=${device.productId}"
                        )
                        // Mark it as connected for your logic
                        UsbProximityService.isConnected = true
                        dismissPersistentDialog()

                        // If we don't already have permission, request it
                        if (!usbManager.hasPermission(device)) {
                            requestUsbPermission(device)
                        } else {
                            // Already have permission; optionally open or read device
                            // openUsbDevice(device)
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

    // ----------------------------------------------------------------------
    // 3) Other Broadcast Receivers (SnackBar, ShowVideo, Proximity)
    // ----------------------------------------------------------------------
    private val snackbarReceiver = object : BroadcastReceiver() {
        override fun onReceive(context: Context?, intent: Intent?) {
            if (intent?.action == "ACTION_SHOW_SNACKBAR") {
                val message = intent?.getStringExtra("MESSAGE")
                Log.d("HomeFragment", "Snackbar message received: $message")
                message?.let {
                    Snackbar.make(binding.root, it, Snackbar.LENGTH_LONG).show()
                }
            }
        }
    }

    private val showVideoReceiver = object : BroadcastReceiver() {
        override fun onReceive(context: Context?, intent: Intent?) {
            if (intent?.action == "ACTION_SHOW_VIDEO_FRAGMENT") {
                val videoUri = intent?.getStringExtra("VIDEO_URI")
                if (!videoUri.isNullOrEmpty()) {
                    Log.d("HomeFragment", "Received request to show VideoFragment: $videoUri")

                    val bundle = Bundle().apply { putString("VIDEO_URI", videoUri) }
                    findNavController().navigate(R.id.action_homeFragment_to_videoFragment, bundle)
                } else {
                    Log.e("HomeFragment", "Empty video URI. Cannot start playback.")
                }
            }
        }
    }

    private val proximityReceiver = object : BroadcastReceiver() {
        override fun onReceive(context: Context?, intent: Intent?) {
            when (intent?.action) {
                "ACTION_PROXIMITY_DETECTED" -> {
                    Log.d("HomeFragment", "👀 Proximity detected! Starting playback.")
                    context?.startService(Intent(context, PlaybackService::class.java).apply {
                        action = "ACTION_PLAY_VIDEO"
                    })
                }

                "ACTION_PROXIMITY_LOST" -> {
                    Log.d("HomeFragment", "🛑 Proximity lost! Stopping playback.")
                    context?.startService(Intent(context, PlaybackService::class.java).apply {
                        action = "ACTION_STOP_VIDEO"
                    })
                }
            }
        }
    }

    // ----------------------------------------------------------------------
    // 4) onCreateView / onViewCreated
    // ----------------------------------------------------------------------
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

        // Initialize your UI & ViewModel
        viewModel.loadVideoDetails()
        observeViewModel()
        setupTimeSelection()
        setupVideoUpload()
        setupPlaybackNavigation()
        setupBatteryOptimisation()
        initializeScreenLockSwitch()

        // Register USB attach/detach
        val usbFilter = IntentFilter().apply {
            addAction(UsbManager.ACTION_USB_DEVICE_ATTACHED)
            addAction(UsbManager.ACTION_USB_DEVICE_DETACHED)
        }
        requireContext().registerReceiver(espConnectedReceiver, usbFilter)
        isEspReceiverRegistered = true

        // Check if devices already connected
        val usbManager = requireContext().getSystemService(Context.USB_SERVICE) as UsbManager
        val deviceList = usbManager.deviceList
        UsbProximityService.isConnected = deviceList.isNotEmpty()
        Log.d("HomeFragment", "Initial USB state: ${UsbProximityService.isConnected}")

        // Update dialog state
        updateEspDialogBasedOnConditions()
    }

    // ----------------------------------------------------------------------
    // 5) onResume / onPause: register/unregister dynamic receivers
    // ----------------------------------------------------------------------
    override fun onResume() {
        super.onResume()
        Log.d("HomeFragment", "Fragment resumed")

        // (A) Register the snackbar receiver
        val snackFilter = IntentFilter("ACTION_SHOW_SNACKBAR")
        ContextCompat.registerReceiver(
            requireContext(), snackbarReceiver, snackFilter, ContextCompat.RECEIVER_NOT_EXPORTED
        )

        // (B) Register the showVideo receiver
        val showVideoIntentFilter = IntentFilter("ACTION_SHOW_VIDEO_FRAGMENT")
        ContextCompat.registerReceiver(
            requireContext(),
            showVideoReceiver,
            showVideoIntentFilter,
            ContextCompat.RECEIVER_NOT_EXPORTED
        )

        // (C) Register the proximity signals
        val proximityFilter = IntentFilter().apply {
            addAction("ACTION_PROXIMITY_DETECTED")
            addAction("ACTION_PROXIMITY_LOST")
        }
        ContextCompat.registerReceiver(
            requireContext(),
            proximityReceiver,
            proximityFilter,
            ContextCompat.RECEIVER_NOT_EXPORTED
        )

        // (D) Register our dynamic USB permission receiver
        val permissionFilter = IntentFilter(USB_PERMISSION_ACTION)
        ContextCompat.registerReceiver(
            requireContext(),
            usbPermissionReceiver,
            permissionFilter,
            ContextCompat.RECEIVER_NOT_EXPORTED
        )

        // Update dialog & start periodic checks
        updateEspDialogBasedOnConditions()
        handler.post(dialogCheckRunnable)
    }

    override fun onPause() {
        super.onPause()
        Log.d("HomeFragment", "Fragment paused")

        // Unregister all the dynamic receivers
        requireContext().unregisterReceiver(snackbarReceiver)
        requireContext().unregisterReceiver(showVideoReceiver)
        requireContext().unregisterReceiver(proximityReceiver)
        requireContext().unregisterReceiver(usbPermissionReceiver)

        // Dismiss the persistent dialog
        dismissPersistentDialog()

        // Stop periodic checks
        handler.removeCallbacks(dialogCheckRunnable)
    }

    override fun onDestroyView() {
        super.onDestroyView()
        Log.d("HomeFragment", "Destroying view; unregistering ESP receiver")

        if (isEspReceiverRegistered) {
            requireContext().unregisterReceiver(espConnectedReceiver)
            isEspReceiverRegistered = false
        }
        _binding = null
    }

    // ----------------------------------------------------------------------
    // 6) Requesting USB Permission
    // ----------------------------------------------------------------------
    /**
     * Called whenever we detect a newly attached device that doesn't
     * already have permission. We'll send an Intent with action=USB_PERMISSION_ACTION
     * and then our usbPermissionReceiver will handle the result.
     */
    private fun requestUsbPermission(device: UsbDevice) {
        val usbManager = requireContext().getSystemService(Context.USB_SERVICE) as UsbManager

        val usbPermissionIntent = Intent(USB_PERMISSION_ACTION)

        val permissionIntent = PendingIntent.getBroadcast(
            requireContext(),
            0,
            usbPermissionIntent,
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_MUTABLE
        )

        usbManager.requestPermission(device, permissionIntent)
        Log.d("HomeFragment", "Requesting USB permission for device: ${device.deviceName}")
    }

    // If you want to open the device after permission is granted:
    /*
    private fun openUsbDevice(device: UsbDevice) {
        val usbManager = requireContext().getSystemService(Context.USB_SERVICE) as UsbManager
        val connection = usbManager.openDevice(device)
        if (connection == null) {
            Log.e("HomeFragment", "openUsbDevice: Failed to open device.")
            return
        }
        val usbInterface = device.getInterface(0)
        if (connection.claimInterface(usbInterface, true)) {
            Log.d("HomeFragment", "Interface claimed. You can do I/O here.")
        } else {
            Log.e("HomeFragment", "Failed to claim interface.")
            connection.close()
        }
    }
    */

    // ----------------------------------------------------------------------
    // 7) Periodic Dialog Checking
    // ----------------------------------------------------------------------
    private val dialogCheckRunnable = object : Runnable {
        override fun run() {
            updateEspDialogBasedOnConditions()
            handler.postDelayed(this, 5_000L) // re-check every 5 seconds
        }
    }

    private fun updateEspDialogBasedOnConditions() {
        // your existing logic ...
        Log.d("HomeFragment", "Checking ESP connection/time window...")

        val currentTime = System.currentTimeMillis()
        val startTime = viewModel.startTime.value?.let { convertTimeToMillis(it) } ?: 0L
        val endTime = viewModel.endTime.value?.let { convertTimeToMillis(it) } ?: 0L

        if (startTime == 0L || endTime == 0L) {
            dismissPersistentDialog()
            return
        }

        val withinTimeWindow = currentTime in startTime..endTime
        val isStartTime = currentTime in startTime..(startTime + 60_000L)

        if (isStartTime || withinTimeWindow) {
            if (!UsbProximityService.isConnected) {
                Log.d("HomeFragment", "ESP not connected; showing dialog.")
                showPersistentDialog()
            } else {
                Log.d("HomeFragment", "ESP connected; dismissing dialog.")
                dismissPersistentDialog()
            }
        } else {
            dismissPersistentDialog()
        }
    }

    private fun shouldShowDialogBasedOnTime(): Boolean {
        // existing logic ...
        val currentTime = System.currentTimeMillis()
        val startTime = viewModel.startTime.value?.let { convertTimeToMillis(it) } ?: 0L
        val endTime = viewModel.endTime.value?.let { convertTimeToMillis(it) } ?: 0L
        if (startTime == 0L || endTime == 0L) return false
        return currentTime in startTime..endTime
    }

    private fun showPersistentDialog() {
        // existing logic ...
        requireActivity().runOnUiThread {
            if (noEspDialog == null && isAdded) {
                noEspDialog = AlertDialog.Builder(requireContext()).setTitle("ESP Disconnected")
                    .setMessage("No ESP detected. Please reconnect your device.")
                    .setCancelable(false).create()
                noEspDialog?.show()
            }
        }
    }

    private fun dismissPersistentDialog() {
        // existing logic ...
        requireActivity().runOnUiThread {
            if (isAdded && noEspDialog?.isShowing == true) {
                noEspDialog?.dismiss()
                Log.d("HomeFragment", "Persistent dialog dismissed.")
            }
            noEspDialog = null
        }
    }

    // ----------------------------------------------------------------------
    // 8) Observing ViewModel, Setting Times, Alarms, etc.
    // ----------------------------------------------------------------------
    private fun observeViewModel() {
        viewModel.videoUri.observe(viewLifecycleOwner) { uri ->
            binding.videoUriTxt.text = uri ?: "No video selected"
            updateVisibilityBasedOnVideoUpload()
        }
        viewModel.startTime.observe(viewLifecycleOwner) {
            binding.startTimeBtn.text = it ?: "Select Start Time"
            updateVisibilityBasedOnVideoUpload()
            updateEspDialogBasedOnConditions()
        }
        viewModel.endTime.observe(viewLifecycleOwner) {
            binding.endTimeBtn.text = it ?: "Select End Time"
            updateVisibilityBasedOnVideoUpload()
            updateEspDialogBasedOnConditions()
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
            Log.d("HomeFragment", "Alarm scheduled at: $startTime")
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

    // ----------------------------------------------------------------------
    // 9) Lock Screen & Battery Optimization
    // ----------------------------------------------------------------------
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
                builder.setTitle("Battery Optimization").setMessage(
                        "To ensure best performance, please disable battery optimization for this app." + "Go to settings and find PlaybackMaster and turn off battery optimization"
                    ).setPositiveButton("Go to Settings") { dialog, _ ->
                        startActivity(intent)
                        dialog.dismiss()
                    }.setNegativeButton("Cancel") { dialog, _ -> dialog.dismiss() }
                    .setCancelable(false)

                val dialog = builder.create()
                dialog.show()
                Toast.makeText(
                    requireContext(), "Turn off Battery Optimization", Toast.LENGTH_LONG
                ).show()
            } catch (e: Exception) {
                e.printStackTrace()
            }
        }
    }

    // ----------------------------------------------------------------------
    // 10) Utility: Convert Time Strings to Millis
    // ----------------------------------------------------------------------
    private fun convertTimeToMillis(time: String?): Long {
        if (time.isNullOrEmpty()) {
            Log.e("HomeFragment", "convertTimeToMillis: null/empty time string")
            return 0L
        }
        return try {
            val parts = time.split(":")
            if (parts.size != 2) {
                Log.e("HomeFragment", "convertTimeToMillis: Invalid format - $time")
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
}