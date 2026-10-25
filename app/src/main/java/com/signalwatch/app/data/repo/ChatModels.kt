package com.signalwatch.app.data.repo

import android.os.Bundle
import com.signalwatch.parser.Direction
import com.signalwatch.parser.Signal
import java.time.Instant

enum class Role(val label: String) { OWNER("owner"), MODERATOR("moderator"), VIEWER("viewer") }

data class ChatItem(
    val id: String,
    val author: String,
    val role: Role,
    val text: String,
    val publishedAt: Instant,
    /** Set only for trusted authors whose message parsed as a signal. */
    val signal: Signal?,
)

/** A signal plus who posted it and when. */
data class CaughtSignal(
    val signal: Signal,
    val author: String,
    val role: Role,
    val at: Instant,
    val messageId: String,
    val isTest: Boolean = false,
) {
    fun toBundle(): Bundle = Bundle().apply {
        putString("direction", signal.direction.name)
        putString("symbol", signal.symbol)
        putString("entry", signal.entry)
        putBoolean("market", signal.marketEntry)
        putString("sl", signal.stopLoss)
        putStringArray("tps", signal.takeProfits.toTypedArray())
        putString("raw", signal.raw)
        putString("author", author)
        putString("role", role.name)
        putLong("at", at.toEpochMilli())
        putString("messageId", messageId)
        putBoolean("test", isTest)
    }

    companion object {
        fun fromBundle(b: Bundle?): CaughtSignal? {
            if (b == null) return null
            val direction = b.getString("direction")?.let { runCatching { Direction.valueOf(it) }.getOrNull() } ?: return null
            val symbol = b.getString("symbol") ?: return null
            return CaughtSignal(
                signal = Signal(
                    direction = direction,
                    symbol = symbol,
                    entry = b.getString("entry"),
                    marketEntry = b.getBoolean("market"),
                    stopLoss = b.getString("sl"),
                    takeProfits = b.getStringArray("tps")?.toList().orEmpty(),
                    raw = b.getString("raw").orEmpty(),
                ),
                author = b.getString("author").orEmpty(),
                role = b.getString("role")?.let { runCatching { Role.valueOf(it) }.getOrNull() } ?: Role.OWNER,
                at = Instant.ofEpochMilli(b.getLong("at")),
                messageId = b.getString("messageId").orEmpty(),
                isTest = b.getBoolean("test"),
            )
        }
    }
}
