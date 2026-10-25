package com.signalwatch.app.ui.live

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Notifications
import androidx.compose.material.icons.outlined.NotificationsOff
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.produceState
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.runtime.snapshotFlow
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.signalwatch.app.data.repo.CaughtSignal
import com.signalwatch.app.data.repo.ChatItem
import com.signalwatch.app.data.repo.LiveVideo
import com.signalwatch.app.data.settings.Settings
import com.signalwatch.app.ui.components.Avatar
import com.signalwatch.app.ui.components.DirectionPill
import com.signalwatch.app.ui.components.LivePill
import com.signalwatch.app.ui.components.OutlinedSquareIconButton
import com.signalwatch.app.ui.components.RoleBadge
import com.signalwatch.app.ui.components.SegmentedToggle
import com.signalwatch.app.ui.components.SwCard
import com.signalwatch.app.ui.components.roleColor
import com.signalwatch.app.ui.hhmm
import com.signalwatch.app.ui.theme.SW
import com.signalwatch.app.ui.theme.mono
import com.signalwatch.app.ui.timeAgo
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.filter
import java.time.Instant

@Composable
fun LiveScreen(
    settings: Settings,
    video: LiveVideo,
    chat: List<ChatItem>,
    latestSignal: CaughtSignal?,
    onToggleSignalAlerts: () -> Unit,
    onEnterFullscreen: (android.view.View, () -> Unit) -> Unit,
    onExitFullscreen: () -> Unit,
) {
    var signalsOnly by rememberSaveable { mutableStateOf(false) }
    val now by produceState(Instant.now()) {
        while (true) {
            delay(15_000)
            value = Instant.now()
        }
    }

    Column(Modifier.fillMaxSize()) {
        Row(
            Modifier
                .fillMaxWidth()
                .padding(horizontal = 16.dp, vertical = 8.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Avatar(settings.channelThumbUrl, settings.displayName, 40.dp)
            Spacer(Modifier.width(12.dp))
            Column(Modifier.weight(1f)) {
                Text(settings.displayName, style = MaterialTheme.typography.titleMedium, maxLines = 1)
                Text(
                    "Live since ${video.startedAt?.hhmm() ?: "--:--"}",
                    style = MaterialTheme.typography.bodySmall,
                )
            }
            LivePill()
            Spacer(Modifier.width(10.dp))
            OutlinedSquareIconButton(
                icon = if (settings.signalAlerts) Icons.Filled.Notifications else Icons.Outlined.NotificationsOff,
                description = if (settings.signalAlerts) "Signal alerts on" else "Signal alerts off",
                onClick = onToggleSignalAlerts,
                tint = if (settings.signalAlerts) SW.Buy else SW.Muted,
            )
        }

        YouTubePlayer(
            videoId = video.videoId,
            onEnterFullscreen = onEnterFullscreen,
            onExitFullscreen = onExitFullscreen,
            modifier = Modifier
                .fillMaxWidth()
                .aspectRatio(16f / 9f),
        )

        Column(
            Modifier
                .weight(1f)
                .padding(horizontal = 16.dp),
        ) {
            if (latestSignal != null) {
                Spacer(Modifier.height(12.dp))
                LatestSignalCard(latestSignal, now)
            }
            Spacer(Modifier.height(12.dp))
            SegmentedToggle(
                options = listOf("All chat", "Signals only"),
                selected = if (signalsOnly) 1 else 0,
                onSelect = { signalsOnly = it == 1 },
            )
            Spacer(Modifier.height(8.dp))
            val visible = remember(chat, signalsOnly) { if (signalsOnly) chat.filter { it.signal != null } else chat }
            ChatList(visible, Modifier.weight(1f))
            Text(
                "Read-only chat. Signals are taken from the owner and moderators only.",
                style = MaterialTheme.typography.bodySmall,
                modifier = Modifier.padding(vertical = 10.dp),
            )
        }
    }
}

@Composable
private fun LatestSignalCard(caught: CaughtSignal, now: Instant) {
    val s = caught.signal
    SwCard(Modifier.fillMaxWidth()) {
        Column(Modifier.padding(14.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Text(
                    "LATEST SIGNAL",
                    style = MaterialTheme.typography.labelMedium,
                    color = SW.Muted,
                    letterSpacing = 1.5.sp,
                    modifier = Modifier.weight(1f),
                )
                Text(timeAgo(caught.at, now), style = MaterialTheme.typography.bodySmall)
            }
            Spacer(Modifier.height(10.dp))
            Row(verticalAlignment = Alignment.CenterVertically) {
                DirectionPill(s.direction)
                Spacer(Modifier.width(10.dp))
                Text(s.symbol, style = mono(20, FontWeight.SemiBold))
            }
            Spacer(Modifier.height(12.dp))
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                Level("Entry", s.entryLabel, Modifier.weight(1f))
                Level("Stop loss", s.stopLoss, Modifier.weight(1f))
                Level("Take profit", s.takeProfits.firstOrNull(), Modifier.weight(1f))
            }
            if (s.takeProfits.size > 1) {
                Spacer(Modifier.height(6.dp))
                Text(
                    s.takeProfits.drop(1).mapIndexed { i, tp -> "TP${i + 2} $tp" }.joinToString("  ·  "),
                    style = mono(13, color = SW.Muted),
                )
            }
            Spacer(Modifier.height(10.dp))
            Text(
                "From ${caught.author} (${caught.role.label}) at ${caught.at.hhmm()}",
                style = MaterialTheme.typography.bodySmall,
            )
        }
    }
}

@Composable
private fun Level(label: String, value: String?, modifier: Modifier) {
    Column(modifier) {
        Text(label, style = MaterialTheme.typography.bodySmall)
        Spacer(Modifier.height(2.dp))
        Text(value ?: "—", style = mono(17), maxLines = 1)
    }
}

@Composable
private fun ChatList(items: List<ChatItem>, modifier: Modifier) {
    val listState = rememberLazyListState()
    // Follow the newest message until the user scrolls up; resume once they're back at the bottom.
    var follow by remember { mutableStateOf(true) }
    LaunchedEffect(listState) {
        snapshotFlow { listState.isScrollInProgress }
            .filter { !it }
            .collect { follow = !listState.canScrollForward }
    }
    LaunchedEffect(items.size, follow) {
        if (follow && items.isNotEmpty()) listState.scrollToItem(items.lastIndex)
    }

    if (items.isEmpty()) {
        Column(modifier.fillMaxWidth(), verticalArrangement = Arrangement.Center, horizontalAlignment = Alignment.CenterHorizontally) {
            Text("Waiting for chat…", style = MaterialTheme.typography.bodySmall)
        }
        return
    }
    LazyColumn(
        state = listState,
        modifier = modifier.fillMaxWidth(),
        verticalArrangement = Arrangement.spacedBy(4.dp),
    ) {
        items(items, key = { it.id }) { ChatRow(it) }
    }
}

@Composable
private fun ChatRow(item: ChatItem) {
    val isSignal = item.signal != null
    val shape = RoundedCornerShape(10.dp)
    Column(
        Modifier
            .fillMaxWidth()
            .then(
                if (isSignal) Modifier.background(SW.SignalBg, shape).border(1.dp, SW.SignalBorder, shape)
                else Modifier
            )
            .padding(horizontal = 10.dp, vertical = 7.dp),
    ) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Text(
                item.author,
                color = roleColor(item.role),
                fontWeight = FontWeight.SemiBold,
                fontSize = 13.sp,
                maxLines = 1,
                modifier = Modifier.weight(1f, fill = false),
            )
            Spacer(Modifier.width(6.dp))
            RoleBadge(item.role)
            Spacer(Modifier.weight(1f))
            if (isSignal) {
                Text("Signal caught", color = SW.Buy, fontSize = 11.sp, fontWeight = FontWeight.SemiBold)
            }
        }
        Text(item.text, style = MaterialTheme.typography.bodyMedium)
    }
}
