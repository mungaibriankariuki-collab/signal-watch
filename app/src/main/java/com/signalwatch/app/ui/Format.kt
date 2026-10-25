package com.signalwatch.app.ui

import java.time.Duration
import java.time.Instant
import java.time.ZoneId
import java.time.format.DateTimeFormatter

private val hhmm = DateTimeFormatter.ofPattern("HH:mm")

fun Instant.hhmm(): String = hhmm.format(atZone(ZoneId.systemDefault()))

fun timeAgo(from: Instant, now: Instant): String {
    val s = Duration.between(from, now).seconds.coerceAtLeast(0)
    return when {
        s < 45 -> "just now"
        s < 3600 -> "${(s + 30) / 60}m ago"
        else -> "${s / 3600}h ago"
    }
}

fun intervalLabel(seconds: Int): String = when {
    seconds <= 90 -> "Every $seconds seconds"
    seconds % 60 == 0 -> "Every ${seconds / 60} minutes"
    else -> "Every ${seconds / 60}m ${seconds % 60}s"
}
