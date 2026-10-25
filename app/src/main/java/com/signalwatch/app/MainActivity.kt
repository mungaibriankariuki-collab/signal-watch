package com.signalwatch.app

import android.Manifest
import android.app.Activity
import android.content.Context
import android.content.Intent
import android.content.pm.ActivityInfo
import android.media.RingtoneManager
import android.net.Uri
import android.os.Build
import android.os.Bundle
import android.provider.Settings as AndroidSettings
import android.view.View
import androidx.activity.ComponentActivity
import androidx.activity.SystemBarStyle
import androidx.activity.compose.BackHandler
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.safeDrawingPadding
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.RadioButton
import androidx.compose.material3.RadioButtonDefaults
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.toArgb
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import androidx.compose.ui.viewinterop.AndroidView
import androidx.core.content.IntentCompat
import androidx.core.view.WindowCompat
import androidx.core.view.WindowInsetsCompat
import androidx.core.view.WindowInsetsControllerCompat
import androidx.hilt.lifecycle.viewmodel.compose.hiltViewModel
import androidx.lifecycle.DefaultLifecycleObserver
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleOwner
import androidx.lifecycle.compose.LifecycleEventEffect
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.signalwatch.app.data.settings.TrustLevel
import com.signalwatch.app.notify.Notifier
import com.signalwatch.app.service.WatchEngine
import com.signalwatch.app.service.WatchService
import com.signalwatch.app.service.WatchState
import com.signalwatch.app.ui.MainViewModel
import com.signalwatch.app.ui.live.LiveScreen
import com.signalwatch.app.ui.offline.OfflineScreen
import com.signalwatch.app.ui.settings.SettingsScreen
import com.signalwatch.app.ui.theme.SW
import com.signalwatch.app.ui.theme.SignalWatchTheme
import dagger.hilt.android.AndroidEntryPoint
import javax.inject.Inject

@AndroidEntryPoint
class MainActivity : ComponentActivity() {

    @Inject lateinit var engine: WatchEngine
    @Inject lateinit var notifier: Notifier

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        val bars = SW.Background.toArgb()
        enableEdgeToEdge(SystemBarStyle.dark(bars), SystemBarStyle.dark(bars))
        handleIntent(intent)

        // Poll while the UI is visible even if the background alerts are switched off.
        lifecycle.addObserver(object : DefaultLifecycleObserver {
            override fun onStart(owner: LifecycleOwner) = engine.acquire()
            override fun onStop(owner: LifecycleOwner) = engine.release()
        })

        setContent {
            SignalWatchTheme {
                AppRoot(notifier)
            }
        }
    }

    override fun onNewIntent(intent: Intent) {
        super.onNewIntent(intent)
        handleIntent(intent)
    }

    private fun handleIntent(intent: Intent?) {
        // "Open stream" from the signal notification: stop the alarm.
        if (intent?.getBooleanExtra(EXTRA_OPEN_LIVE, false) == true) notifier.dismissSignal()
    }

    companion object {
        private const val EXTRA_OPEN_LIVE = "open_live"

        fun intent(context: Context, openLive: Boolean): Intent =
            Intent(context, MainActivity::class.java)
                .putExtra(EXTRA_OPEN_LIVE, openLive)
                .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TOP or Intent.FLAG_ACTIVITY_SINGLE_TOP)
    }
}

@Composable
private fun AppRoot(notifier: Notifier, vm: MainViewModel = hiltViewModel()) {
    val context = LocalContext.current
    val activity = context as Activity
    val settings by vm.settings.collectAsStateWithLifecycle()
    val state by vm.state.collectAsStateWithLifecycle()
    val chat by vm.chat.collectAsStateWithLifecycle()
    val latestSignal by vm.latestSignal.collectAsStateWithLifecycle()
    val quotaUsed by vm.quotaUsed.collectAsStateWithLifecycle()
    val handleStatus by vm.handleStatus.collectAsStateWithLifecycle()

    // Re-read permission state whenever we come back from a settings page.
    var resumeTick by remember { mutableIntStateOf(0) }
    LifecycleEventEffect(Lifecycle.Event.ON_RESUME) { resumeTick++ }
    val notificationsAllowed = remember(resumeTick) { notifier.notificationsAllowed() }
    val fullScreenAllowed = remember(resumeTick) { notifier.canUseFullScreenIntent() }

    val notificationPermission = rememberLauncherForActivityResult(ActivityResultContracts.RequestPermission()) {
        resumeTick++
    }
    LaunchedEffect(Unit) {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU && !notifier.notificationsAllowed()) {
            notificationPermission.launch(Manifest.permission.POST_NOTIFICATIONS)
        }
    }

    val soundPicker = rememberLauncherForActivityResult(ActivityResultContracts.StartActivityForResult()) { result ->
        val data = result.data
        if (result.resultCode == Activity.RESULT_OK && data != null) {
            val uri = IntentCompat.getParcelableExtra(data, RingtoneManager.EXTRA_RINGTONE_PICKED_URI, Uri::class.java)
            val title = uri?.let { runCatching { RingtoneManager.getRingtone(context, it)?.getTitle(context) }.getOrNull() }
            vm.setAlertSound(uri?.toString(), title)
        }
    }

    val s = settings ?: return Box(Modifier.fillMaxSize().background(SW.Background))

    LaunchedEffect(s.goLiveAlert, s.signalAlerts) {
        WatchService.sync(context, s.goLiveAlert || s.signalAlerts)
    }

    val pickSound = {
        soundPicker.launch(
            Intent(RingtoneManager.ACTION_RINGTONE_PICKER)
                .putExtra(RingtoneManager.EXTRA_RINGTONE_TYPE, RingtoneManager.TYPE_ALARM)
                .putExtra(RingtoneManager.EXTRA_RINGTONE_TITLE, "Alert sound")
                .putExtra(RingtoneManager.EXTRA_RINGTONE_SHOW_SILENT, false)
                .putExtra(RingtoneManager.EXTRA_RINGTONE_SHOW_DEFAULT, true)
                .putExtra(RingtoneManager.EXTRA_RINGTONE_EXISTING_URI, s.alertSoundUri?.let(Uri::parse))
        )
    }
    val openNotificationSettings = {
        context.startActivity(
            Intent(AndroidSettings.ACTION_APP_NOTIFICATION_SETTINGS)
                .putExtra(AndroidSettings.EXTRA_APP_PACKAGE, context.packageName)
        )
    }
    val openFullScreenSettings = {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.UPSIDE_DOWN_CAKE) {
            context.startActivity(
                Intent(AndroidSettings.ACTION_MANAGE_APP_USE_FULL_SCREEN_INTENT, Uri.parse("package:${context.packageName}"))
            )
        }
    }

    var showSettings by rememberSaveable { mutableStateOf(false) }
    var showTrustDialog by remember { mutableStateOf(false) }
    var fullscreenView by remember { mutableStateOf<View?>(null) }
    var exitFullscreen by remember { mutableStateOf<(() -> Unit)?>(null) }

    fun setFullscreen(view: View?, exit: (() -> Unit)?) {
        fullscreenView = view
        exitFullscreen = exit
        val controller = WindowCompat.getInsetsController(activity.window, activity.window.decorView)
        if (view != null) {
            activity.requestedOrientation = ActivityInfo.SCREEN_ORIENTATION_SENSOR_LANDSCAPE
            controller.systemBarsBehavior = WindowInsetsControllerCompat.BEHAVIOR_SHOW_TRANSIENT_BARS_BY_SWIPE
            controller.hide(WindowInsetsCompat.Type.systemBars())
        } else {
            activity.requestedOrientation = ActivityInfo.SCREEN_ORIENTATION_UNSPECIFIED
            controller.show(WindowInsetsCompat.Type.systemBars())
        }
    }

    // Stream ended while in fullscreen: leave fullscreen with it.
    LaunchedEffect(state is WatchState.Live) {
        if (state !is WatchState.Live && fullscreenView != null) setFullscreen(null, null)
    }

    val warnings = buildList<Pair<String, () -> Unit>> {
        if (!notificationsAllowed) add("Notifications are off, so alerts can't reach you. Tap to allow them." to openNotificationSettings)
        if (!fullScreenAllowed) add("Full-screen alerts are off. Tap to allow them so signals show over the lock screen." to openFullScreenSettings)
    }

    Box(Modifier.fillMaxSize().background(SW.Background)) {
        Box(Modifier.fillMaxSize().safeDrawingPadding()) {
            when (val st = state) {
                is WatchState.Live -> LiveScreen(
                    settings = s,
                    video = st.video,
                    chat = chat,
                    latestSignal = latestSignal,
                    onToggleSignalAlerts = { vm.setSignalAlerts(!s.signalAlerts) },
                    onEnterFullscreen = { view, exit -> setFullscreen(view, exit) },
                    onExitFullscreen = { setFullscreen(null, null) },
                )
                is WatchState.Offline -> OfflineScreen(
                    settings = s,
                    state = st,
                    warnings = warnings,
                    onOpenSettings = { showSettings = true },
                    onGoLiveAlert = vm::setGoLiveAlert,
                    onSignalAlerts = vm::setSignalAlerts,
                    onPickSound = pickSound,
                    onPickTrust = { showTrustDialog = true },
                    onTestAlert = vm::sendTestAlert,
                )
            }
        }

        // Drawn over the main screen so a playing stream isn't torn down while settings are open.
        if (showSettings) {
            Box(Modifier.fillMaxSize().background(SW.Background).safeDrawingPadding()) {
                SettingsScreen(
                    settings = s,
                    handleStatus = handleStatus,
                    quotaUsed = quotaUsed,
                    notificationsAllowed = notificationsAllowed,
                    fullScreenAllowed = fullScreenAllowed,
                    onBack = { showSettings = false },
                    onSaveHandle = vm::saveHandle,
                    onInterval = vm::setInterval,
                    onGoLiveAlert = vm::setGoLiveAlert,
                    onSignalAlerts = vm::setSignalAlerts,
                    onPickSound = pickSound,
                    onPickTrust = { showTrustDialog = true },
                    onRequestNotifications = openNotificationSettings,
                    onOpenFullScreenSettings = openFullScreenSettings,
                    onSimulate = vm::setSimulate,
                    onTestAlert = vm::sendTestAlert,
                )
            }
        }

        fullscreenView?.let { view ->
            AndroidView(factory = { view }, modifier = Modifier.fillMaxSize().background(Color.Black))
        }
    }

    BackHandler(enabled = fullscreenView != null) { exitFullscreen?.invoke() }
    BackHandler(enabled = showSettings && fullscreenView == null) { showSettings = false }

    if (showTrustDialog) {
        TrustDialog(
            current = s.trust,
            onPick = {
                vm.setTrust(it)
                showTrustDialog = false
            },
            onDismiss = { showTrustDialog = false },
        )
    }
}

@Composable
private fun TrustDialog(current: TrustLevel, onPick: (TrustLevel) -> Unit, onDismiss: () -> Unit) {
    AlertDialog(
        onDismissRequest = onDismiss,
        containerColor = SW.Surface,
        title = { Text("Trust signals from") },
        text = {
            Column {
                TrustLevel.entries.forEach { level ->
                    androidx.compose.foundation.layout.Row(
                        Modifier
                            .fillMaxWidth()
                            .heightIn(min = SW.MinTouch)
                            .clickable { onPick(level) }
                            .padding(vertical = 4.dp),
                        verticalAlignment = Alignment.CenterVertically,
                    ) {
                        RadioButton(
                            selected = level == current,
                            onClick = { onPick(level) },
                            colors = RadioButtonDefaults.colors(selectedColor = SW.Buy),
                        )
                        Text(level.label)
                    }
                }
            }
        },
        confirmButton = { TextButton(onClick = onDismiss) { Text("Close", color = SW.Buy) } },
    )
}
