package com.zidny.keyboard

import android.Manifest
import android.annotation.SuppressLint
import android.content.Context
import android.content.pm.PackageManager
import android.provider.CalendarContract
import java.util.Calendar

/** Reads the events of one day from the phone's calendars, only when the user allowed it in the app. */
object DeviceCalendar {

    fun allowed(context: Context) =
        context.checkSelfPermission(Manifest.permission.READ_CALENDAR) == PackageManager.PERMISSION_GRANTED

    /** The events on [day] of [month] (0–11) in [year], sorted by start; empty without permission. */
    @SuppressLint("MissingPermission") // Checked by allowed() first.
    fun eventsOn(context: Context, year: Int, month: Int, day: Int): List<CalendarTools.Event> {
        if (!allowed(context)) return emptyList()
        val start = Calendar.getInstance().apply {
            clear()
            set(year, month, day)
        }.timeInMillis
        val end = start + 24 * 60 * 60 * 1000L
        val projection = arrayOf(
            CalendarContract.Instances.TITLE,
            CalendarContract.Instances.BEGIN,
            CalendarContract.Instances.END,
            CalendarContract.Instances.ALL_DAY,
        )
        val events = mutableListOf<CalendarTools.Event>()
        try {
            CalendarContract.Instances.query(context.contentResolver, projection, start, end)?.use { cursor ->
                while (cursor.moveToNext()) {
                    val allDay = cursor.getInt(3) == 1
                    // Times are clipped to this day, so an event from yesterday evening counts from midnight.
                    val from = ((cursor.getLong(1) - start) / 60_000).coerceIn(0, 24 * 60).toInt()
                    val to = ((cursor.getLong(2) - start) / 60_000).coerceIn(0, 24 * 60).toInt()
                    events.add(CalendarTools.Event(cursor.getString(0).orEmpty(), from, to, allDay))
                }
            }
        } catch (e: SecurityException) {
            return emptyList()
        }
        return events.sortedWith(compareBy({ !it.allDay }, { it.start }))
    }
}
