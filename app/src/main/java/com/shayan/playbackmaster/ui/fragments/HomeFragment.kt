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
import com.shayan.playbackmaster.utils.Constants
import com.shayan.playbackmaster.utils.TimePickerHelper
import java.text.SimpleDateFormat
import java.util.Calendar
import java.util.Locale

class HomeFragment : Fragment() {

    private var _binding: FragmentHomeBinding? = null
    private val binding get() = _binding!!
    private val viewModel: AppViewModel by activityViewModels()

    // ESP connection monitoring
    private var checkEspRunnable: Runnable? = null
    private val handler = Handler(Looper.getMainLooper())
    private var noEspDialog: AlertDialog? = null

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

        viewModel.loadVideoDetails()
        observeViewModel()
        setupTimeSelection()
        setupVideoUpload()
        setupPlaybackNavigation()
        initializeScreenLockSwitch()
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
                handler.postDelayed(this, 1000) // Check every second
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
        val startTime = viewModel.startTime.value
        val endTime = viewModel.endTime.value

        if (startTime.isNullOrEmpty() || endTime.isNullOrEmpty()) {
            dismissPersistentDialog()
            return
        }

        val currentTime = System.currentTimeMillis()
        val startMillis = convertTimeToMillis(startTime)
        val endMillis = convertTimeToMillis(endTime)

        if (currentTime in startMillis..endMillis) {
            if (!UsbProximityService.isConnected) {
                showPersistentDialog()
            } else {
                dismissPersistentDialog()
            }
        } else {
            dismissPersistentDialog()
        }
    }

    private fun convertTimeToMillis(time: String): Long {
        val formatter = SimpleDateFormat("HH:mm", Locale.getDefault())
        val calendar = Calendar.getInstance().apply {
            val (hour, minute) = time.split(":").map { it.toInt() }
            set(Calendar.HOUR_OF_DAY, hour)
            set(Calendar.MINUTE, minute)
            set(Calendar.SECOND, 0)
            set(Calendar.MILLISECOND, 0)
        }
        return calendar.timeInMillis
    }

    private fun showPersistentDialog() {
        if (noEspDialog == null && isAdded) {
            noEspDialog = AlertDialog.Builder(requireContext()).setMessage("No ESP detected")
                .setCancelable(false).create()
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
            }
        }

        binding.endTimeBtn.setOnClickListener {
            TimePickerHelper.showTimePicker(requireContext()) { hour, minute ->
                val endTime = formatTime(hour, minute)
                viewModel.saveVideoDetails(
                    viewModel.videoUri.value.orEmpty(), viewModel.startTime.value.orEmpty(), endTime
                )
            }
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
//                scheduleAlarm()
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


    override fun onDestroyView() {
        super.onDestroyView()
        _binding = null
    }
}