package com.shayan.playbackmaster.utils

import android.app.TimePickerDialog
import android.content.Context
import java.util.Calendar

object TimePickerHelper {

    fun showTimePicker(context: Context, onTimeSelected: (hour: Int, minute: Int) -> Unit) {
        val calendar = Calendar.getInstance()
        val hour = calendar.get(Calendar.HOUR_OF_DAY)
        val minute = calendar.get(Calendar.MINUTE)

        TimePickerDialog(
            context,
            { _, selectedHour, selectedMinute -> onTimeSelected(selectedHour, selectedMinute) },
            hour,
            minute,
            false // Set to true for 24-hour format
        ).show()
    }
}