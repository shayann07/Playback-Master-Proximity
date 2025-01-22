package com.shayan.playbackmaster.ui

import android.Manifest
import android.content.pm.PackageManager
import android.os.Bundle
import android.util.Log
import android.widget.Toast
import androidx.appcompat.app.AppCompatActivity
import androidx.appcompat.app.AppCompatDelegate
import androidx.core.app.ActivityCompat
import androidx.core.content.ContextCompat
import androidx.navigation.NavController
import androidx.navigation.fragment.NavHostFragment
import androidx.navigation.fragment.findNavController
import com.shayan.playbackmaster.R
import com.shayan.playbackmaster.ui.fragments.ExitPlaybackListener

class MainActivity : AppCompatActivity(), ExitPlaybackListener {

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

        // Check for permissions and handle playback intent
        if (hasStoragePermission()) {
            handlePlaybackIntent()
        } else {
            requestStoragePermission()
        }
    }

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