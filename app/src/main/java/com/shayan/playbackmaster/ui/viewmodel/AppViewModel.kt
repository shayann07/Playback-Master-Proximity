package com.shayan.playbackmaster.ui.viewmodel

import android.app.Application
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.LiveData
import androidx.lifecycle.MutableLiveData
import com.shayan.playbackmaster.data.preferences.PreferencesHelper

class AppViewModel(application: Application) : AndroidViewModel(application) {

    private val preferencesHelper = PreferencesHelper(application)

    // LiveData for UI state
    private val _videoUri = MutableLiveData<String?>()
    val videoUri: LiveData<String?> = _videoUri

    private val _startTime = MutableLiveData<String?>()
    val startTime: LiveData<String?> = _startTime

    private val _endTime = MutableLiveData<String?>()
    val endTime: LiveData<String?> = _endTime

    // Load video details from preferences
    fun loadVideoDetails() {
        _videoUri.value = preferencesHelper.getVideoUri()
        _startTime.value = preferencesHelper.getStartTime()
        _endTime.value = preferencesHelper.getEndTime()
    }

    // Save video details to preferences
    fun saveVideoDetails(uri: String, startTime: String, endTime: String) {
        preferencesHelper.saveVideoDetails(uri, startTime, endTime)
        _videoUri.value = uri
        _startTime.value = startTime
        _endTime.value = endTime
    }

    // Clear all saved preferences
    fun clearVideoDetails() {
        preferencesHelper.clearPreferences()
        _videoUri.value = null
        _startTime.value = null
        _endTime.value = null
    }
}