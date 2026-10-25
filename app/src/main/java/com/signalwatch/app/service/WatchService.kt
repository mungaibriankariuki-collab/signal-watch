package com.signalwatch.app.service

import android.content.Context
import android.content.Intent
import android.content.pm.ServiceInfo
import android.os.Build
import android.os.PowerManager
import androidx.core.app.ServiceCompat
import androidx.core.content.ContextCompat
import androidx.lifecycle.LifecycleService
import androidx.lifecycle.lifecycleScope
import com.signalwatch.app.notify.Notifier
import dagger.hilt.android.AndroidEntryPoint
import kotlinx.coroutines.launch
import javax.inject.Inject

/**
 * Keeps [WatchEngine] running in the background while "Go-live alert" or "Signal alerts" is on,
 * with the small "Watching for live stream" notification.
 */
@AndroidEntryPoint
class WatchService : LifecycleService() {

    @Inject lateinit var engine: WatchEngine
    @Inject lateinit var notifier: Notifier

    private var wakeLock: PowerManager.WakeLock? = null

    override fun onCreate() {
        super.onCreate()
        val type = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.UPSIDE_DOWN_CAKE) {
            ServiceInfo.FOREGROUND_SERVICE_TYPE_SPECIAL_USE
        } else 0
        ServiceCompat.startForeground(this, Notifier.ID_WATCH, notifier.watchNotification(TEXT_OFFLINE), type)

        // Keep the CPU awake between polls so the timer keeps running with the screen off.
        wakeLock = getSystemService(PowerManager::class.java)
            .newWakeLock(PowerManager.PARTIAL_WAKE_LOCK, "SignalWatch:watch")
            .apply { setReferenceCounted(false); acquire() }

        engine.acquire()
        lifecycleScope.launch {
            engine.state.collect { state ->
                notifier.updateWatch(if (state is WatchState.Live) TEXT_LIVE else TEXT_OFFLINE)
            }
        }
    }

    override fun onDestroy() {
        engine.release()
        wakeLock?.release()
        super.onDestroy()
    }

    companion object {
        private const val TEXT_OFFLINE = "Watching for live stream"
        private const val TEXT_LIVE = "Live now · watching chat for signals"

        /** Start or stop the service to match the alert toggles. Call from the foreground UI. */
        fun sync(context: Context, enabled: Boolean) {
            val intent = Intent(context, WatchService::class.java)
            if (enabled) ContextCompat.startForegroundService(context, intent)
            else context.stopService(intent)
        }
    }
}
