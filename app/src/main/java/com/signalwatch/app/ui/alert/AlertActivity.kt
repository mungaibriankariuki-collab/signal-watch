package com.signalwatch.app.ui.alert

import android.app.KeyguardManager
import android.content.Intent
import android.os.Build
import android.os.Bundle
import android.view.WindowManager
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import com.signalwatch.app.MainActivity
import com.signalwatch.app.data.repo.CaughtSignal
import com.signalwatch.app.notify.Notifier
import com.signalwatch.app.ui.theme.SignalWatchTheme
import dagger.hilt.android.AndroidEntryPoint
import javax.inject.Inject

/** Full-screen alert shown over the lock screen via the notification's full-screen intent. */
@AndroidEntryPoint
class AlertActivity : ComponentActivity() {

    @Inject lateinit var notifier: Notifier

    private var launched by mutableStateOf<CaughtSignal?>(null)

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        showOverLockScreen()
        enableEdgeToEdge()
        launched = CaughtSignal.fromBundle(intent.extras)

        setContent {
            SignalWatchTheme {
                val active by notifier.activeAlert.collectAsState()
                var seenActive by remember { mutableStateOf(false) }
                LaunchedEffect(active) {
                    if (active != null) seenActive = true
                    // Dismissed from the notification shade while this screen was up.
                    else if (seenActive) finish()
                }
                val caught = active ?: launched
                if (caught == null) {
                    LaunchedEffect(Unit) { finish() }
                } else {
                    AlertScreen(caught, onOpenStream = ::openStream, onDismiss = {
                        notifier.dismissSignal()
                        finish()
                    })
                }
            }
        }
    }

    override fun onNewIntent(intent: Intent) {
        super.onNewIntent(intent)
        setIntent(intent)
        CaughtSignal.fromBundle(intent.extras)?.let { launched = it }
    }

    private fun openStream() {
        notifier.silenceSignal()
        val keyguard = getSystemService(KeyguardManager::class.java)
        if (keyguard.isKeyguardLocked) {
            keyguard.requestDismissKeyguard(this, object : KeyguardManager.KeyguardDismissCallback() {
                override fun onDismissSucceeded() = launchStream()
            })
        } else {
            launchStream()
        }
    }

    private fun launchStream() {
        startActivity(MainActivity.intent(this, openLive = true))
        notifier.dismissSignal()
        finish()
    }

    private fun showOverLockScreen() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O_MR1) {
            setShowWhenLocked(true)
            setTurnScreenOn(true)
        } else {
            @Suppress("DEPRECATION")
            window.addFlags(WindowManager.LayoutParams.FLAG_SHOW_WHEN_LOCKED or WindowManager.LayoutParams.FLAG_TURN_SCREEN_ON)
        }
        window.addFlags(WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON)
    }
}
