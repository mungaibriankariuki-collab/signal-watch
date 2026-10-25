package com.signalwatch.app.ui.settings

import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.OutlinedTextFieldDefaults
import androidx.compose.material3.Slider
import androidx.compose.material3.SliderDefaults
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.unit.dp
import com.signalwatch.app.data.quota.QuotaTracker
import com.signalwatch.app.data.settings.Settings
import com.signalwatch.app.debug.DebugTools
import com.signalwatch.app.ui.HandleStatus
import com.signalwatch.app.ui.components.OutlinedSquareIconButton
import com.signalwatch.app.ui.components.RowDivider
import com.signalwatch.app.ui.components.SwCard
import com.signalwatch.app.ui.components.SwitchRow
import com.signalwatch.app.ui.components.ValueRow
import com.signalwatch.app.ui.intervalLabel
import com.signalwatch.app.ui.theme.SW
import com.signalwatch.app.ui.theme.mono
import java.text.NumberFormat
import kotlin.math.roundToInt

@Composable
fun SettingsScreen(
    settings: Settings,
    handleStatus: HandleStatus,
    quotaUsed: Int,
    notificationsAllowed: Boolean,
    fullScreenAllowed: Boolean,
    onBack: () -> Unit,
    onSaveHandle: (String) -> Unit,
    onInterval: (Int) -> Unit,
    onGoLiveAlert: (Boolean) -> Unit,
    onSignalAlerts: (Boolean) -> Unit,
    onPickSound: () -> Unit,
    onPickTrust: () -> Unit,
    onRequestNotifications: () -> Unit,
    onOpenFullScreenSettings: () -> Unit,
    onSimulate: (Boolean) -> Unit,
    onTestAlert: () -> Unit,
) {
    Column(Modifier.fillMaxSize()) {
        Row(
            Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 8.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            OutlinedSquareIconButton(Icons.AutoMirrored.Filled.ArrowBack, "Back", onBack)
            Spacer(Modifier.width(12.dp))
            Text("Settings", style = MaterialTheme.typography.titleLarge)
        }

        Column(
            Modifier
                .weight(1f)
                .verticalScroll(rememberScrollState())
                .padding(horizontal = 16.dp),
        ) {
            SectionLabel("CHANNEL")
            SwCard(Modifier.fillMaxWidth()) {
                Column(Modifier.padding(16.dp)) {
                    var handle by rememberSaveable { mutableStateOf(settings.handle) }
                    OutlinedTextField(
                        value = handle,
                        onValueChange = { handle = it },
                        label = { Text("Channel handle") },
                        placeholder = { Text("@handle") },
                        singleLine = true,
                        keyboardOptions = KeyboardOptions(imeAction = ImeAction.Done),
                        keyboardActions = KeyboardActions(onDone = { onSaveHandle(handle) }),
                        shape = RoundedCornerShape(SW.ButtonRadius),
                        colors = OutlinedTextFieldDefaults.colors(
                            focusedBorderColor = SW.Buy,
                            unfocusedBorderColor = SW.Border,
                            focusedLabelColor = SW.Buy,
                            cursorColor = SW.Buy,
                        ),
                        modifier = Modifier.fillMaxWidth(),
                    )
                    Spacer(Modifier.height(10.dp))
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        val status = when (handleStatus) {
                            HandleStatus.Idle -> settings.channelId?.let { "${settings.displayName} · $it" } ?: "Not set"
                            HandleStatus.Resolving -> "Looking up…"
                            is HandleStatus.Resolved -> "Saved: ${handleStatus.title}"
                            is HandleStatus.Failed -> handleStatus.message
                        }
                        Text(
                            status,
                            color = if (handleStatus is HandleStatus.Failed) SW.Sell else SW.Muted,
                            style = MaterialTheme.typography.bodySmall,
                            modifier = Modifier.weight(1f),
                        )
                        Spacer(Modifier.width(12.dp))
                        Button(
                            onClick = { onSaveHandle(handle) },
                            enabled = handleStatus != HandleStatus.Resolving,
                            shape = RoundedCornerShape(SW.ButtonRadius),
                            colors = ButtonDefaults.buttonColors(containerColor = SW.Buy, contentColor = SW.BuyInk),
                            modifier = Modifier.height(SW.MinTouch),
                        ) { Text("Save", color = SW.BuyInk) }
                    }
                }
            }

            SectionLabel("ALERTS")
            SwCard(Modifier.fillMaxWidth()) {
                SwitchRow("Go-live alert", settings.goLiveAlert, onGoLiveAlert)
                RowDivider()
                SwitchRow("Signal alerts", settings.signalAlerts, onSignalAlerts)
                RowDivider()
                ValueRow("Alert sound", settings.alertSoundTitle ?: "Default alarm", onPickSound)
                RowDivider()
                ValueRow("Trust signals from", settings.trust.label, onPickTrust)
                RowDivider()
                Column(Modifier.padding(16.dp)) {
                    var interval by rememberSaveable(settings.intervalSec) { mutableFloatStateOf(settings.intervalSec.toFloat()) }
                    Text("Live check interval", style = MaterialTheme.typography.bodyLarge)
                    Text(intervalLabel(snap(interval)), style = MaterialTheme.typography.bodySmall)
                    Slider(
                        value = interval,
                        onValueChange = { interval = it },
                        onValueChangeFinished = { onInterval(snap(interval)) },
                        valueRange = Settings.MIN_INTERVAL_SEC.toFloat()..Settings.MAX_INTERVAL_SEC.toFloat(),
                        steps = (Settings.MAX_INTERVAL_SEC - Settings.MIN_INTERVAL_SEC) / 30 - 1,
                        colors = SliderDefaults.colors(thumbColor = SW.Buy, activeTrackColor = SW.Buy, inactiveTrackColor = SW.Border),
                    )
                }
            }

            SectionLabel("PERMISSIONS")
            SwCard(Modifier.fillMaxWidth()) {
                ValueRow("Notifications", if (notificationsAllowed) "Allowed" else "Tap to allow", onRequestNotifications)
                RowDivider()
                ValueRow("Full-screen alerts", if (fullScreenAllowed) "Allowed" else "Tap to allow", onOpenFullScreenSettings)
            }

            SectionLabel("API USAGE TODAY")
            SwCard(Modifier.fillMaxWidth()) {
                Column(Modifier.padding(16.dp)) {
                    val nf = NumberFormat.getIntegerInstance()
                    Text(
                        "${nf.format(quotaUsed)} / ${nf.format(QuotaTracker.DAILY_LIMIT)} units",
                        style = mono(17),
                    )
                    Spacer(Modifier.height(4.dp))
                    Text(
                        "Estimated by this app. Resets at midnight Pacific. Costs: channels.list " +
                            "${QuotaTracker.COST_CHANNELS_LIST}, videos.list ${QuotaTracker.COST_VIDEOS_LIST}, " +
                            "liveChatMessages.list ${QuotaTracker.COST_LIVE_CHAT_MESSAGES_LIST}. " +
                            "Chat polling at the 8 s floor uses about 2,250 units an hour.",
                        style = MaterialTheme.typography.bodySmall,
                    )
                }
            }

            if (DebugTools.AVAILABLE) {
                SectionLabel("DEBUG")
                SwCard(Modifier.fillMaxWidth()) {
                    SwitchRow("Simulate live stream", settings.simulate, onSimulate)
                }
            }

            Spacer(Modifier.height(16.dp))
            OutlinedButton(
                onClick = onTestAlert,
                shape = RoundedCornerShape(SW.ButtonRadius),
                modifier = Modifier.fillMaxWidth().height(52.dp),
            ) { Text("Send test alert", color = SW.Text) }
            Spacer(Modifier.height(24.dp))
        }
    }
}

private fun snap(v: Float): Int = ((v / 30f).roundToInt() * 30).coerceIn(Settings.MIN_INTERVAL_SEC, Settings.MAX_INTERVAL_SEC)

@Composable
private fun SectionLabel(text: String) {
    Text(
        text,
        style = MaterialTheme.typography.labelMedium,
        color = SW.Muted,
        modifier = Modifier.padding(top = 20.dp, bottom = 8.dp, start = 4.dp),
    )
}
