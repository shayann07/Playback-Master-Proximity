package com.shayan.playbackmaster.ui

import android.content.Intent
import android.content.IntentFilter
import android.os.Bundle
import androidx.appcompat.app.AppCompatActivity
import androidx.appcompat.app.AppCompatDelegate
import androidx.navigation.findNavController
import androidx.navigation.fragment.NavHostFragment
import androidx.navigation.fragment.findNavController
import com.shayan.playbackmaster.R
import com.shayan.playbackmaster.ui.fragments.ExitPlaybackListener

class MainActivity : AppCompatActivity(), ExitPlaybackListener {

    private val playbackReceiver = PlaybackReceiver(this) // Pass the listener

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_main)

        // Set up the navigation host fragment
        val navHostFragment =
            supportFragmentManager.findFragmentById(R.id.nav_host_fragment) as NavHostFragment
        val navController = navHostFragment.navController

        AppCompatDelegate.setDefaultNightMode(AppCompatDelegate.MODE_NIGHT_YES)
    }

    override fun onSupportNavigateUp(): Boolean {
        val navController =
            supportFragmentManager.findFragmentById(R.id.nav_host_fragment)?.findNavController()
        return navController?.navigateUp() ?: super.onSupportNavigateUp()
    }

    override fun onExitPlayback() {
        val navController = findNavController(R.id.nav_host_fragment)
        navController.navigate(R.id.homeFragment) // Navigate to HomeFragment
    }

    override fun onResume() {
        super.onResume()
        val filter = IntentFilter(Intent.ACTION_SCREEN_OFF)
        registerReceiver(playbackReceiver, filter)
    }

    override fun onPause() {
        super.onPause()
        unregisterReceiver(playbackReceiver)
    }

}