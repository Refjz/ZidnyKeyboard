package com.zidny.keyboard

import android.content.Context
import java.io.File
import java.io.PrintWriter
import java.io.StringWriter
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

/**
 * Keeps the last crash on the phone, so the settings screen can show it and the user can copy it to us.
 * A keyboard that crashes just disappears, which says nothing about why.
 */
object CrashLog {

    private const val FILE = "last_crash.txt"

    fun install(context: Context) {
        val file = File(context.applicationContext.filesDir, FILE)
        val previous = Thread.getDefaultUncaughtExceptionHandler()
        if (previous is Handler) return
        Thread.setDefaultUncaughtExceptionHandler(Handler(file, previous))
    }

    fun read(context: Context): String? = File(context.filesDir, FILE).takeIf { it.exists() }?.readText()

    fun clear(context: Context) {
        File(context.filesDir, FILE).delete()
    }

    private class Handler(
        private val file: File,
        private val previous: Thread.UncaughtExceptionHandler?,
    ) : Thread.UncaughtExceptionHandler {
        override fun uncaughtException(thread: Thread, error: Throwable) {
            try {
                val trace = StringWriter().also { error.printStackTrace(PrintWriter(it)) }.toString()
                val time = SimpleDateFormat("yyyy-MM-dd HH:mm:ss", Locale.US).format(Date())
                file.writeText("ZidnyKeyboard ${BuildConfig.VERSION_NAME} (${BuildConfig.VERSION_CODE}) · $time · ${thread.name}\n$trace")
            } catch (_: Throwable) {
                // Saving the report must never get in the way of the crash itself.
            }
            previous?.uncaughtException(thread, error)
        }
    }
}
