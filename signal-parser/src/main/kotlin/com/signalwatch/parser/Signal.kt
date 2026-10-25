package com.signalwatch.parser

enum class Direction { BUY, SELL }

/**
 * A trade signal pulled out of a chat message. Prices are kept as strings so they read
 * exactly as the trader wrote them (minus thousands separators), e.g. "2345.50".
 */
data class Signal(
    val direction: Direction,
    val symbol: String,
    /** Entry price, or a zone like "2340-2343". Null when no price was given. */
    val entry: String?,
    /** True for "buy now" / "sell market" style entries. */
    val marketEntry: Boolean,
    val stopLoss: String?,
    /** TP1, TP2, TP3... in order. */
    val takeProfits: List<String>,
    val raw: String,
) {
    val hasEntry: Boolean get() = entry != null || marketEntry

    /** Entry as shown to the user: the price, "Market", or null. */
    val entryLabel: String? get() = entry ?: if (marketEntry) "Market" else null

    /** How many levels the signal carries; used to keep the most complete repost. */
    val completeness: Int
        get() = (if (hasEntry) 1 else 0) + (if (stopLoss != null) 1 else 0) + takeProfits.size
}
