package com.shayan.playbackmaster.ui.viewmodel

import android.app.Application
import android.util.Log
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

    init {
        Log.d("AppViewModel", "ViewModel initialized, loading video details.")
        loadVideoDetails()
    }

    /**
     * Loads video details from preferences and updates the LiveData.
     */
    fun loadVideoDetails() {
        Log.d("AppViewModel", "Loading video details from preferences")
        _videoUri.value = preferencesHelper.getVideoUri()
        _startTime.value = preferencesHelper.getStartTime()
        _endTime.value = preferencesHelper.getEndTime()
    }

    /**
     * Saves video details to preferences and updates the LiveData.
     *
     * @param uri The URI of the video.
     * @param startTime The start time of the video playback.
     * @param endTime The end time of the video playback.
     */
    fun saveVideoDetails(uri: String, startTime: String, endTime: String) {
        Log.d("AppViewModel", "Saving video details: URI: $uri, Start: $startTime, End: $endTime")
        preferencesHelper.saveVideoDetails(uri, startTime, endTime)
        _videoUri.value = uri
        _startTime.value = startTime
        _endTime.value = endTime
    }

    /**
     * Clears all saved video details from preferences and resets the LiveData.
     */
    fun clearVideoDetails() {
        Log.d("AppViewModel", "Clearing all video details from preferences.")
        preferencesHelper.clearPreferences()
        _videoUri.value = null
        _startTime.value = null
        _endTime.value = null
    }
}