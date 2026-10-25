package com.signalwatch.parser

/**
 * Collapses reposts of the same call (owner posts it, a mod reposts it) into one alert.
 *
 * Two signals match when direction and symbol are equal and the entries are equal, or one
 * of them has no entry price. Matching only happens within [windowMillis] of the first post.
 */
class SignalDeduplicator(private val windowMillis: Long = 10 * 60_000L) {

    sealed interface Result {
        /** The version to show: for [Improved] and [Duplicate] it's the most complete one seen. */
        val signal: Signal

        data class New(override val signal: Signal) : Result
        data class Improved(override val signal: Signal, val previous: Signal) : Result
        data class Duplicate(override val signal: Signal) : Result
    }

    private class Seen(var signal: Signal, val firstSeenAt: Long)

    private val seen = ArrayList<Seen>()

    @Synchronized
    fun offer(signal: Signal, nowMillis: Long): Result {
        seen.removeAll { nowMillis - it.firstSeenAt > windowMillis }
        val match = seen.firstOrNull { matches(it.signal, signal) }
            ?: return Result.New(signal).also { seen += Seen(signal, nowMillis) }

        val previous = match.signal
        return if (signal.completeness > previous.completeness) {
            match.signal = merge(signal, previous)
            Result.Improved(match.signal, previous)
        } else {
            Result.Duplicate(previous)
        }
    }

    @Synchronized
    fun clear() = seen.clear()

    private fun matches(a: Signal, b: Signal): Boolean {
        if (a.direction != b.direction || a.symbol != b.symbol) return false
        val ea = a.entry
        val eb = b.entry
        return ea == null || eb == null || ea == eb
    }

    /** Keep the richer post but don't lose an entry price the earlier one had. */
    private fun merge(better: Signal, older: Signal): Signal =
        if (better.entry == null && older.entry != null) better.copy(entry = older.entry, marketEntry = false)
        else better
}
