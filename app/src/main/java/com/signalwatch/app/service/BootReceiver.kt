package com.signalwatch.app.service

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import com.signalwatch.app.data.settings.SettingsRepository
import dagger.hilt.EntryPoint
import dagger.hilt.InstallIn
import dagger.hilt.android.EntryPointAccessors
import dagger.hilt.components.SingletonComponent
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch

/**
 * Restarts watching after a reboot or an app update (installing a new APK kills the service),
 * if either alert toggle is on. specialUse foreground services may start from BOOT_COMPLETED.
 */
class BootReceiver : BroadcastReceiver() {

    @EntryPoint
    @InstallIn(SingletonComponent::class)
    interface SettingsEntryPoint {
        fun settings(): SettingsRepository
    }

    override fun onReceive(context: Context, intent: Intent) {
        if (intent.action != Intent.ACTION_BOOT_COMPLETED && intent.action != Intent.ACTION_MY_PACKAGE_REPLACED) return
        val app = context.applicationContext
        val settings = EntryPointAccessors.fromApplication(app, SettingsEntryPoint::class.java).settings()
        val pending = goAsync()
        CoroutineScope(Dispatchers.IO).launch {
            try {
                val s = settings.snapshot()
                if (s.goLiveAlert || s.signalAlerts) WatchService.sync(app, enabled = true)
            } finally {
                pending.finish()
            }
        }
    }
}
