package com.shayan.playbackmaster.data.preferences

import android.content.Context
import android.content.SharedPreferences

class PreferencesHelper(context: Context) {

    private val sharedPreferences: SharedPreferences =
        context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
    private val editor: SharedPreferences.Editor = sharedPreferences.edit()

    companion object {
        private const val PREFS_NAME = "VideoPlaybackPrefs"
        const val KEY_VIDEO_URI = "video_uri"
        const val KEY_START_TIME = "start_time"
        const val KEY_END_TIME = "end_time"
    }

    fun saveVideoDetails(uri: String, startTime: String, endTime: String) {
        editor.putString(KEY_VIDEO_URI, uri)
        editor.putString(KEY_START_TIME, startTime)
        editor.putString(KEY_END_TIME, endTime)
        editor.apply()
    }

    fun getVideoUri(): String? = sharedPreferences.getString(KEY_VIDEO_URI, null)

    fun getStartTime(): String? = sharedPreferences.getString(KEY_START_TIME, null)

    fun getEndTime(): String? = sharedPreferences.getString(KEY_END_TIME, null)

    fun clearPreferences() {
        editor.clear().apply()
    }
}