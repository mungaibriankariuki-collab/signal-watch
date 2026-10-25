package com.signalwatch.app.ui.theme

import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Typography
import androidx.compose.material3.darkColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.Font
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.signalwatch.app.R
import com.signalwatch.parser.Direction

object SW {
    val Background = Color(0xFF0E1116)
    val Surface = Color(0xFF171B22)
    val Border = Color(0xFF2A313C)
    val Divider = Color(0xFF232A35)
    val Text = Color(0xFFE8EAED)
    val Muted = Color(0xFF9AA3AE)
    val Live = Color(0xFFC62B2F)
    val Buy = Color(0xFF3D9BFF)
    val Sell = Color(0xFFFF8A3D)
    val BuyInk = Color(0xFF06121F)
    val SellInk = Color(0xFF1A0E05)
    val Owner = Color(0xFFFFB37A)
    val Mod = Color(0xFF7DB8FF)
    val Viewer = Color(0xFF9AA3AE)
    val SignalBg = Color(0xFF16233A)
    val SignalBorder = Color(0xFF24406A)
    val AlertSellBg = Color(0xFF140C07)
    val AlertBuyBg = Color(0xFF07101C)

    val CardRadius = 14.dp
    val ButtonRadius = 12.dp
    val MinTouch = 44.dp

    fun accent(d: Direction) = if (d == Direction.BUY) Buy else Sell
    fun ink(d: Direction) = if (d == Direction.BUY) BuyInk else SellInk
}

val PlexSans = FontFamily(
    Font(R.font.ibm_plex_sans_regular, FontWeight.Normal),
    Font(R.font.ibm_plex_sans_medium, FontWeight.Medium),
    Font(R.font.ibm_plex_sans_semibold, FontWeight.SemiBold),
    Font(R.font.ibm_plex_sans_bold, FontWeight.Bold),
)

val PlexMono = FontFamily(
    Font(R.font.ibm_plex_mono_regular, FontWeight.Normal),
    Font(R.font.ibm_plex_mono_medium, FontWeight.Medium),
    Font(R.font.ibm_plex_mono_semibold, FontWeight.SemiBold),
)

/** Mono style for every price and symbol. */
fun mono(size: Int, weight: FontWeight = FontWeight.Medium, color: Color = SW.Text) =
    TextStyle(fontFamily = PlexMono, fontSize = size.sp, fontWeight = weight, color = color)

private val base = TextStyle(fontFamily = PlexSans, color = SW.Text)

private val typography = Typography(
    titleLarge = base.copy(fontSize = 20.sp, fontWeight = FontWeight.SemiBold),
    titleMedium = base.copy(fontSize = 16.sp, fontWeight = FontWeight.SemiBold),
    bodyLarge = base.copy(fontSize = 16.sp),
    bodyMedium = base.copy(fontSize = 14.sp),
    bodySmall = base.copy(fontSize = 12.sp, color = SW.Muted),
    labelLarge = base.copy(fontSize = 15.sp, fontWeight = FontWeight.SemiBold),
    labelMedium = base.copy(fontSize = 12.sp, fontWeight = FontWeight.SemiBold),
    labelSmall = base.copy(fontSize = 11.sp, fontWeight = FontWeight.SemiBold),
)

@Composable
fun SignalWatchTheme(content: @Composable () -> Unit) {
    MaterialTheme(
        colorScheme = darkColorScheme(
            background = SW.Background,
            surface = SW.Surface,
            surfaceVariant = SW.Surface,
            onBackground = SW.Text,
            onSurface = SW.Text,
            onSurfaceVariant = SW.Muted,
            primary = SW.Buy,
            onPrimary = SW.BuyInk,
            outline = SW.Border,
            outlineVariant = SW.Divider,
            error = SW.Live,
        ),
        typography = typography,
        content = content,
    )
}
