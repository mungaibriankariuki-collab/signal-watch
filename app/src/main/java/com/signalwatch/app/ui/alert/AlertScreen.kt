package com.signalwatch.app.ui.alert

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.safeDrawingPadding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.NotificationsActive
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.signalwatch.app.data.repo.CaughtSignal
import com.signalwatch.app.ui.hhmm
import com.signalwatch.app.ui.theme.PlexSans
import com.signalwatch.app.ui.theme.SW
import com.signalwatch.app.ui.theme.mono
import com.signalwatch.parser.Direction

@Composable
fun AlertScreen(caught: CaughtSignal, onOpenStream: () -> Unit, onDismiss: () -> Unit) {
    val s = caught.signal
    val accent = SW.accent(s.direction)
    val background = if (s.direction == Direction.BUY) SW.AlertBuyBg else SW.AlertSellBg

    Column(
        Modifier
            .fillMaxSize()
            .background(background)
            .safeDrawingPadding()
            .padding(horizontal = 20.dp),
    ) {
        Column(
            Modifier
                .weight(1f)
                .verticalScroll(rememberScrollState()),
            horizontalAlignment = Alignment.CenterHorizontally,
        ) {
            Spacer(Modifier.height(28.dp))
            Box(
                Modifier
                    .size(112.dp)
                    .background(Brush.radialGradient(listOf(accent.copy(alpha = 0.45f), accent.copy(alpha = 0f))), CircleShape),
                contentAlignment = Alignment.Center,
            ) {
                Box(
                    Modifier
                        .size(68.dp)
                        .background(accent.copy(alpha = 0.18f), CircleShape)
                        .border(1.5.dp, accent, CircleShape),
                    contentAlignment = Alignment.Center,
                ) {
                    Icon(Icons.Filled.NotificationsActive, contentDescription = null, tint = accent, modifier = Modifier.size(34.dp))
                }
            }
            Spacer(Modifier.height(16.dp))
            Text(
                if (caught.isTest) "TEST SIGNAL" else "NEW SIGNAL",
                fontFamily = PlexSans,
                fontWeight = FontWeight.SemiBold,
                fontSize = 13.sp,
                letterSpacing = 4.sp,
                color = SW.Muted,
            )
            Spacer(Modifier.height(4.dp))
            Text(
                "${caught.at.hhmm()} · from ${caught.author} (${caught.role.label})",
                style = MaterialTheme.typography.bodyMedium,
                color = SW.Muted,
            )
            Spacer(Modifier.height(12.dp))
            Text(
                if (s.direction == Direction.BUY) "BUY" else "SELL",
                fontFamily = PlexSans,
                fontWeight = FontWeight.Bold,
                fontSize = 64.sp,
                lineHeight = 68.sp,
                color = accent,
            )
            Text(s.symbol, style = mono(28, FontWeight.SemiBold))
            Spacer(Modifier.height(20.dp))

            Column(
                Modifier
                    .fillMaxWidth()
                    .background(SW.Surface.copy(alpha = 0.7f), RoundedCornerShape(SW.CardRadius))
                    .border(1.dp, SW.Border, RoundedCornerShape(SW.CardRadius))
                    .padding(horizontal = 18.dp, vertical = 6.dp),
            ) {
                LevelRow("Entry", s.entryLabel)
                LevelRow("Stop loss", s.stopLoss)
                LevelRow("Take profit", s.takeProfits.firstOrNull(), last = s.takeProfits.size <= 1)
                s.takeProfits.drop(1).forEachIndexed { i, tp ->
                    LevelRow("TP${i + 2}", tp, last = i == s.takeProfits.size - 2)
                }
            }
            Spacer(Modifier.height(18.dp))

            Text(
                "ORIGINAL CHAT MESSAGE",
                style = MaterialTheme.typography.labelMedium,
                color = SW.Muted,
                letterSpacing = 1.5.sp,
                modifier = Modifier.fillMaxWidth(),
            )
            Spacer(Modifier.height(6.dp))
            Text(
                s.raw,
                style = mono(14, FontWeight.Normal),
                modifier = Modifier
                    .fillMaxWidth()
                    .background(SW.Background, RoundedCornerShape(10.dp))
                    .border(1.dp, SW.Border, RoundedCornerShape(10.dp))
                    .padding(12.dp),
            )
            Spacer(Modifier.height(12.dp))
            Text("Check the live price before you enter.", style = MaterialTheme.typography.bodySmall)
            Spacer(Modifier.height(16.dp))
        }

        Button(
            onClick = onOpenStream,
            shape = RoundedCornerShape(SW.ButtonRadius),
            colors = ButtonDefaults.buttonColors(containerColor = accent, contentColor = SW.ink(s.direction)),
            modifier = Modifier.fillMaxWidth().height(56.dp),
        ) {
            Text("Open stream", fontSize = 17.sp, fontWeight = FontWeight.SemiBold, fontFamily = PlexSans, color = SW.ink(s.direction))
        }
        Spacer(Modifier.height(10.dp))
        OutlinedButton(
            onClick = onDismiss,
            shape = RoundedCornerShape(SW.ButtonRadius),
            border = BorderStroke(1.dp, SW.Border),
            modifier = Modifier.fillMaxWidth().height(52.dp),
        ) {
            Text("Dismiss", fontSize = 16.sp, fontFamily = PlexSans, color = SW.Text)
        }
        Spacer(Modifier.height(16.dp))
    }
}

@Composable
private fun LevelRow(label: String, value: String?, last: Boolean = false) {
    Row(
        Modifier
            .fillMaxWidth()
            .padding(vertical = 10.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.SpaceBetween,
    ) {
        Text(label, style = MaterialTheme.typography.bodyLarge, color = SW.Muted)
        Text(value ?: "—", style = mono(26))
    }
    if (!last) Box(Modifier.fillMaxWidth().height(1.dp).background(SW.Divider))
}
