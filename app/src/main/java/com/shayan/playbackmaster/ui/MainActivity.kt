package com.shayan.playbackmaster.ui

import android.Manifest
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.os.Build
import android.os.Bundle

import android.provider.Settings
import android.util.Log
import android.widget.Toast
import androidx.appcompat.app.AlertDialog
import androidx.appcompat.app.AppCompatActivity
import androidx.appcompat.app.AppCompatDelegate
import androidx.core.app.ActivityCompat
import androidx.core.content.ContextCompat
import androidx.navigation.NavController
import androidx.navigation.fragment.NavHostFragment
import androidx.navigation.fragment.findNavController
import com.shayan.playbackmaster.R
import android.os.PowerManager
import com.shayan.playbackmaster.ui.fragments.ExitPlaybackListener

class MainActivity<PowerManager> : AppCompatActivity(), ExitPlaybackListener {

    private lateinit var navHostFragment: NavHostFragment
    private lateinit var navController: NavController

    private val REQUEST_CODE_READ_STORAGE = 100

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_main)

        // Set up the navigation host fragment
        navHostFragment =
            supportFragmentManager.findFragmentById(R.id.nav_host_fragment) as NavHostFragment
        navController = navHostFragment.navController

        AppCompatDelegate.setDefaultNightMode(AppCompatDelegate.MODE_NIGHT_YES)

        // Check if battery optimization is ignored
     /*   if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.M && !isBatteryOptimizationIgnored() ) {
            promptBatteryOptimization()  // Show the dialog to ask for user permission
        }*/

        // Check for permissions and handle playback intent
        if (hasStoragePermission()) {
            handlePlaybackIntent()
        } else {
            requestStoragePermission()
        }
    }

  /*  private fun isBatteryOptimizationIgnored(): Boolean {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.M) {
            val powerManager = getSystemService(Context.POWER_SERVICE) as PowerManager
            try {
                return powerManager.isIgnoringBatteryOptimizations(packageName)
            } catch (e: NoSuchMethodError) {
                // Handle the case where the method is not available, e.g. for older versions of Android
                e.printStackTrace()
            }
        }
        // For devices below Android M, battery optimization is not a concern
        return true
    }

    private fun promptBatteryOptimization() {
        // Show a dialog explaining the need to disable battery optimization
        AlertDialog.Builder(this)
            .setTitle("Battery Optimization")
            .setMessage("For uninterrupted playback, please disable battery optimization for this app. Do you want to open the settings?")
            .setPositiveButton("Yes") { _, _ ->
                // If the user agrees, redirect them to the battery optimization settings
                batteryOptimization()
            }
            .setNegativeButton("No", null)  // If they cancel, do nothing
            .show()
    }

    private fun batteryOptimization(): Boolean {
        // If the device is Android M (API 23) or higher, navigate to battery optimization settings
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.M) {
            val intent = Intent(Settings.ACTION_IGNORE_BATTERY_OPTIMIZATION_SETTINGS)
            try {
                startActivity(intent)
                return true  // Successfully opened the settings
            } catch (e: Exception) {
                e.printStackTrace()
                return false  // Failed to open settings
            }
        }
        return false  // Battery optimization settings are not available for this Android version
    }*/

    private fun hasStoragePermission(): Boolean {
        return if (android.os.Build.VERSION.SDK_INT >= android.os.Build.VERSION_CODES.TIRAMISU) {
            ContextCompat.checkSelfPermission(
                this, Manifest.permission.READ_MEDIA_VIDEO
            ) == PackageManager.PERMISSION_GRANTED
        } else {
            ContextCompat.checkSelfPermission(
                this, Manifest.permission.READ_EXTERNAL_STORAGE
            ) == PackageManager.PERMISSION_GRANTED
        }
    }




    private fun requestStoragePermission() {
        if (android.os.Build.VERSION.SDK_INT >= android.os.Build.VERSION_CODES.TIRAMISU) {
            ActivityCompat.requestPermissions(
                this, arrayOf(Manifest.permission.READ_MEDIA_VIDEO), REQUEST_CODE_READ_STORAGE
            )
        } else {
            ActivityCompat.requestPermissions(
                this, arrayOf(Manifest.permission.READ_EXTERNAL_STORAGE), REQUEST_CODE_READ_STORAGE
            )
        }
    }

    private fun handlePlaybackIntent() {
        val videoUri = intent.getStringExtra("VIDEO_URI")
        val startTime = intent.getStringExtra("START_TIME")
        val endTime = intent.getStringExtra("END_TIME")

        Log.d(
            "MainActivity",
            "Intent Data - VIDEO_URI: $videoUri, START_TIME: $startTime, END_TIME: $endTime"
        )

        if (!videoUri.isNullOrEmpty() && !startTime.isNullOrEmpty() && !endTime.isNullOrEmpty()) {
            // Navigate directly to VideoFragment with playback details
            val bundle = Bundle().apply {
                putString("VIDEO_URI", videoUri)
                putString("START_TIME", startTime)
                putString("END_TIME", endTime)
            }
            navController.setGraph(R.navigation.nav_graph, bundle)
            navController.navigate(R.id.videoFragment, bundle)
        }
    }

    override fun onRequestPermissionsResult(
        requestCode: Int, permissions: Array<out String>, grantResults: IntArray
    ) {
        super.onRequestPermissionsResult(requestCode, permissions, grantResults)
        if (requestCode == REQUEST_CODE_READ_STORAGE) {
            if (grantResults.isNotEmpty() && grantResults[0] == PackageManager.PERMISSION_GRANTED) {
                handlePlaybackIntent()
            } else {
                Toast.makeText(this, "Permission required to play video", Toast.LENGTH_SHORT).show()
            }
        }
    }

    override fun onSupportNavigateUp(): Boolean {
        val navController =
            supportFragmentManager.findFragmentById(R.id.nav_host_fragment)?.findNavController()
        return navController?.navigateUp() ?: super.onSupportNavigateUp()
    }

    override fun onExitPlayback() {
        val navController = navController
        navController.navigate(R.id.homeFragment) // Navigate to HomeFragment
    }
}