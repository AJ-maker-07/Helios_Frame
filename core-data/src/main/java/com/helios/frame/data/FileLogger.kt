package com.helios.frame.data

import android.content.Context
import android.util.Log
import java.io.File
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

/**
 * Phase 3 safety net: persistent local log (survives Logcat loss).
 *
 * File: <app-external-files>/helios-logs/helios.log
 *   e.g. /sdcard/Android/data/com.helios.frame/files/helios-logs/helios.log
 * Pull after a test run:
 *   adb pull /sdcard/Android/data/com.helios.frame/files/helios-logs/helios.log .
 * Falls back to internal filesDir/helios-logs if external storage is unavailable.
 *
 * Thread-safe, never throws (logging must not crash a batch).
 */
object FileLogger {
    private const val TAG = "HeliosLog"
    private const val DIR = "helios-logs"
    private const val NAME = "helios.log"
    private const val MAX_BYTES = 2 * 1024 * 1024L

    @Volatile private var logFile: File? = null
    private val lock = Any()
    private val dateFmt = SimpleDateFormat("yyyy-MM-dd HH:mm:ss.SSS", Locale.US)

    fun init(appContext: Context) {
        try {
            val base = appContext.getExternalFilesDir(null) ?: appContext.filesDir
            val dir = File(base, DIR).apply { mkdirs() }
            synchronized(lock) { logFile = File(dir, NAME) }
            log(TAG, "logger init path=${logFile?.absolutePath}")
        } catch (t: Throwable) {
            Log.w(TAG, "init failed: ${t.message}")
        }
    }

    /** For UI copy-paste / bug reports. Null until [init] runs. */
    fun path(): String? = logFile?.absolutePath

    fun log(tag: String, msg: String) {
        Log.d(tag, msg)
        val f = logFile ?: return
        try {
            synchronized(lock) {
                if (f.exists() && f.length() > MAX_BYTES) {
                    // Simple rotation: truncate rather than grow unbounded.
                    f.writeText("")
                }
                f.appendText("${dateFmt.format(Date())} $tag: $msg\n")
            }
        } catch (_: Throwable) { }
    }

    fun warn(tag: String, msg: String) {
        Log.w(tag, msg)
        log(tag, "WARN: $msg")
    }
}
