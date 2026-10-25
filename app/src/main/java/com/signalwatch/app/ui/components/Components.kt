package com.signalwatch.app.ui.components

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.Switch
import androidx.compose.material3.SwitchDefaults
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import coil3.compose.AsyncImage
import com.signalwatch.app.data.repo.Role
import com.signalwatch.app.ui.theme.PlexSans
import com.signalwatch.app.ui.theme.SW
import com.signalwatch.parser.Direction

@Composable
fun SwCard(modifier: Modifier = Modifier, content: @Composable ColumnScope.() -> Unit) {
    Column(
        modifier
            .clip(RoundedCornerShape(SW.CardRadius))
            .background(SW.Surface)
            .border(1.dp, SW.Border, RoundedCornerShape(SW.CardRadius)),
        content = content,
    )
}

/** 44dp outlined square icon button. */
@Composable
fun OutlinedSquareIconButton(icon: ImageVector, description: String, onClick: () -> Unit, tint: Color = SW.Text) {
    Box(
        Modifier
            .size(SW.MinTouch)
            .clip(RoundedCornerShape(SW.ButtonRadius))
            .border(1.dp, SW.Border, RoundedCornerShape(SW.ButtonRadius))
            .clickable(onClick = onClick),
        contentAlignment = Alignment.Center,
    ) {
        Icon(icon, contentDescription = description, tint = tint, modifier = Modifier.size(22.dp))
    }
}

@Composable
fun Pill(text: String, background: Color, color: Color, modifier: Modifier = Modifier, border: BorderStroke? = null) {
    Box(
        modifier
            .clip(RoundedCornerShape(50))
            .background(background)
            .then(if (border != null) Modifier.border(border, RoundedCornerShape(50)) else Modifier)
            .padding(horizontal = 10.dp, vertical = 3.dp),
    ) {
        Text(text, color = color, fontFamily = PlexSans, fontSize = 12.sp, fontWeight = FontWeight.Bold, letterSpacing = 0.8.sp)
    }
}

@Composable
fun LivePill() = Pill("LIVE", SW.Live, Color.White)

@Composable
fun DirectionPill(direction: Direction, modifier: Modifier = Modifier) =
    Pill(if (direction == Direction.BUY) "BUY" else "SELL", SW.accent(direction), SW.ink(direction), modifier)

@Composable
fun RoleBadge(role: Role) {
    when (role) {
        Role.OWNER -> Pill("OWNER", SW.Owner.copy(alpha = 0.16f), SW.Owner)
        Role.MODERATOR -> Pill("MOD", SW.Mod.copy(alpha = 0.16f), SW.Mod)
        Role.VIEWER -> Unit
    }
}

fun roleColor(role: Role) = when (role) {
    Role.OWNER -> SW.Owner
    Role.MODERATOR -> SW.Mod
    Role.VIEWER -> SW.Viewer
}

/** Channel thumbnail, falling back to the first letter of the name. */
@Composable
fun Avatar(url: String?, name: String, size: Dp) {
    Box(
        Modifier
            .size(size)
            .clip(CircleShape)
            .background(SW.Border),
        contentAlignment = Alignment.Center,
    ) {
        Text(
            name.trimStart('@').firstOrNull()?.uppercase() ?: "?",
            color = SW.Text,
            fontFamily = PlexSans,
            fontWeight = FontWeight.SemiBold,
            fontSize = (size.value * 0.42f).sp,
        )
        if (url != null) {
            AsyncImage(
                model = url,
                contentDescription = name,
                contentScale = ContentScale.Crop,
                modifier = Modifier.size(size).clip(CircleShape),
            )
        }
    }
}

@Composable
fun SwitchRow(title: String, checked: Boolean, onChange: (Boolean) -> Unit) {
    Row(
        Modifier
            .fillMaxWidth()
            .heightIn(min = 56.dp)
            .clickable { onChange(!checked) }
            .padding(horizontal = 16.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Text(title, style = androidx.compose.material3.MaterialTheme.typography.bodyLarge, modifier = Modifier.weight(1f))
        Switch(
            checked = checked,
            onCheckedChange = onChange,
            colors = SwitchDefaults.colors(
                checkedThumbColor = SW.BuyInk,
                checkedTrackColor = SW.Buy,
                uncheckedThumbColor = SW.Muted,
                uncheckedTrackColor = SW.Background,
                uncheckedBorderColor = SW.Border,
            ),
        )
    }
}

@Composable
fun ValueRow(title: String, value: String, onClick: () -> Unit) {
    Row(
        Modifier
            .fillMaxWidth()
            .heightIn(min = 56.dp)
            .clickable(onClick = onClick)
            .padding(horizontal = 16.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Text(title, style = androidx.compose.material3.MaterialTheme.typography.bodyLarge, modifier = Modifier.weight(1f))
        Spacer(Modifier.width(12.dp))
        Text(value, color = SW.Muted, fontFamily = PlexSans, fontSize = 15.sp, maxLines = 1, overflow = TextOverflow.Ellipsis)
    }
}

@Composable
fun RowDivider() = HorizontalDivider(color = SW.Divider, thickness = 1.dp)

@Composable
fun SegmentedToggle(options: List<String>, selected: Int, onSelect: (Int) -> Unit, modifier: Modifier = Modifier) {
    Row(
        modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(SW.ButtonRadius))
            .background(SW.Surface)
            .border(1.dp, SW.Border, RoundedCornerShape(SW.ButtonRadius))
            .padding(3.dp),
        horizontalArrangement = Arrangement.spacedBy(3.dp),
    ) {
        options.forEachIndexed { i, label ->
            val on = i == selected
            Box(
                Modifier
                    .weight(1f)
                    .heightIn(min = SW.MinTouch - 6.dp)
                    .clip(RoundedCornerShape(SW.ButtonRadius - 3.dp))
                    .background(if (on) SW.Border else Color.Transparent)
                    .clickable { onSelect(i) },
                contentAlignment = Alignment.Center,
            ) {
                Text(
                    label,
                    color = if (on) SW.Text else SW.Muted,
                    fontFamily = PlexSans,
                    fontSize = 14.sp,
                    fontWeight = if (on) FontWeight.SemiBold else FontWeight.Medium,
                )
            }
        }
    }
}
