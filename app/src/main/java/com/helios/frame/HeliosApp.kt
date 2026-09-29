package com.helios.frame

import android.app.Application
import android.app.NotificationChannel
import android.app.NotificationManager
import android.content.ComponentCallbacks2
import android.content.res.Configuration
import android.os.Build
import android.os.StrictMode
import coil.Coil
import com.helios.frame.batch.BatchContract
import com.helios.frame.data.FileLogger

class HeliosApp : Application(), ComponentCallbacks2 {
    override fun onCreate() {
        super.onCreate()
        // Phase 3 safety net: persistent log first so later events are captured.
        FileLogger.init(this)
        // Phase 3.1: StrictMode, debug builds only. penaltyLog() — never crash.
        if (BuildConfig.DEBUG) {
            StrictMode.setThreadPolicy(
                StrictMode.ThreadPolicy.Builder()
                    .detectDiskReads()
                    .detectDiskWrites()
                    .detectNetwork()
                    .penaltyLog()
                    .build(),
            )
            StrictMode.setVmPolicy(
                StrictMode.VmPolicy.Builder()
                    .detectLeakedClosableObjects()
                    .penaltyLog()
                    .build(),
            )
        }
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            val mgr = getSystemService(NotificationManager::class.java)
            if (mgr?.getNotificationChannel(BatchContract.CHANNEL_ID) == null) {
                mgr?.createNotificationChannel(
                    NotificationChannel(
                        BatchContract.CHANNEL_ID,
                        "Helios batch export",
                        NotificationManager.IMPORTANCE_LOW,
                    ),
                )
            }
        }
    }

    /**
     * Phase 3.3: memory pressure handling. Clear Coil's thumbnail memory cache
     * on moderate/high pressure before the OS OOM-kills the process.
     * TRIM_MEMORY_MODERATE (60) and above = act; background trims are ignored.
     */
    override fun onTrimMemory(level: Int) {
        super.onTrimMemory(level)
        if (level >= ComponentCallbacks2.TRIM_MEMORY_MODERATE) {
            try {
                Coil.imageLoader(this).memoryCache?.clear()
                FileLogger.log("HeliosMem", "onTrimMemory level=$level cleared Coil memory cache")
            } catch (t: Throwable) {
                FileLogger.warn("HeliosMem", "cache clear failed: ${t.message}")
            }
        }
    }

    override fun onConfigurationChanged(newConfig: Configuration) {
        super.onConfigurationChanged(newConfig)
    }

    override fun onLowMemory() {
        super.onLowMemory()
        try {
            Coil.imageLoader(this).memoryCache?.clear()
            FileLogger.log("HeliosMem", "onLowMemory cleared Coil memory cache")
        } catch (t: Throwable) {
            FileLogger.warn("HeliosMem", "cache clear failed: ${t.message}")
        }
    }
}
