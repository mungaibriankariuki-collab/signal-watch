package com.signalwatch.app.ui.offline

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.Settings
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.signalwatch.app.data.settings.Settings
import com.signalwatch.app.service.WatchState
import com.signalwatch.app.ui.components.Avatar
import com.signalwatch.app.ui.components.OutlinedSquareIconButton
import com.signalwatch.app.ui.components.Pill
import com.signalwatch.app.ui.components.RowDivider
import com.signalwatch.app.ui.components.SwCard
import com.signalwatch.app.ui.components.SwitchRow
import com.signalwatch.app.ui.components.ValueRow
import com.signalwatch.app.ui.hhmm
import com.signalwatch.app.ui.intervalLabel
import com.signalwatch.app.ui.theme.SW

@Composable
fun OfflineScreen(
    settings: Settings,
    state: WatchState.Offline,
    warnings: List<Pair<String, () -> Unit>>,
    onOpenSettings: () -> Unit,
    onGoLiveAlert: (Boolean) -> Unit,
    onSignalAlerts: (Boolean) -> Unit,
    onPickSound: () -> Unit,
    onPickTrust: () -> Unit,
    onTestAlert: () -> Unit,
) {
    Column(Modifier.fillMaxSize()) {
        Row(
            Modifier
                .fillMaxWidth()
                .padding(horizontal = 16.dp, vertical = 8.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Text("Signal Watch", style = MaterialTheme.typography.titleLarge, modifier = Modifier.weight(1f))
            OutlinedSquareIconButton(Icons.Outlined.Settings, "Settings", onOpenSettings)
        }

        Column(
            Modifier
                .weight(1f)
                .verticalScroll(rememberScrollState())
                .padding(horizontal = 16.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
        ) {
            Spacer(Modifier.height(24.dp))
            Avatar(settings.channelThumbUrl, settings.displayName, 96.dp)
            Spacer(Modifier.height(14.dp))
            Text(settings.displayName, style = MaterialTheme.typography.titleLarge)
            Spacer(Modifier.height(10.dp))
            Pill("OFFLINE", SW.Border, SW.Muted)
            Spacer(Modifier.height(12.dp))
            Text(
                "You'll get a loud alert the moment he goes live.",
                color = SW.Muted,
                style = MaterialTheme.typography.bodyMedium,
                textAlign = TextAlign.Center,
            )
            Spacer(Modifier.height(24.dp))

            warnings.forEach { (text, action) ->
                SwCard(Modifier.fillMaxWidth().padding(bottom = 12.dp).clickable(onClick = action)) {
                    Text(
                        text,
                        color = SW.Sell,
                        style = MaterialTheme.typography.bodyMedium,
                        modifier = Modifier.padding(16.dp),
                    )
                }
            }

            SwCard(Modifier.fillMaxWidth()) {
                Row(Modifier.padding(16.dp), verticalAlignment = Alignment.CenterVertically) {
                    Box(Modifier.size(10.dp).background(SW.Buy, CircleShape))
                    Spacer(Modifier.width(12.dp))
                    Column {
                        Text("Checking for a live stream", style = MaterialTheme.typography.bodyLarge)
                        val last = state.lastCheck?.hhmm() ?: "--:--"
                        Text(
                            "${intervalLabel(settings.intervalSec)} · last check $last",
                            color = SW.Muted,
                            style = MaterialTheme.typography.bodySmall,
                        )
                        state.message?.let {
                            Spacer(Modifier.height(4.dp))
                            Text(it, color = SW.Sell, style = MaterialTheme.typography.bodySmall)
                        }
                    }
                }
            }
            Spacer(Modifier.height(12.dp))

            SwCard(Modifier.fillMaxWidth()) {
                SwitchRow("Go-live alert", settings.goLiveAlert, onGoLiveAlert)
                RowDivider()
                SwitchRow("Signal alerts", settings.signalAlerts, onSignalAlerts)
                RowDivider()
                ValueRow("Alert sound", settings.alertSoundTitle ?: "Default alarm", onPickSound)
                RowDivider()
                ValueRow("Trust signals from", settings.trust.label, onPickTrust)
            }
            Spacer(Modifier.height(16.dp))
        }

        Button(
            onClick = onTestAlert,
            shape = RoundedCornerShape(SW.ButtonRadius),
            colors = ButtonDefaults.buttonColors(containerColor = SW.Buy, contentColor = SW.BuyInk),
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 16.dp, vertical = 12.dp)
                .height(52.dp),
        ) {
            Text("Send test alert", fontSize = 16.sp, style = MaterialTheme.typography.labelLarge, color = SW.BuyInk)
        }
    }
}
