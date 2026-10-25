package com.signalwatch.parser

/**
 * Turns a free-form chat message into a [Signal], or null if it isn't one.
 *
 * A message is a signal only when it has a direction, a symbol and at least one of
 * entry / stop loss / take profit. Recaps ("TP1 hit", "closed my buy"), questions and
 * negations ("don't sell gold") are rejected unless they carry a full entry + exit.
 */
class SignalParser {

    fun parse(message: String): Signal? {
        val text = normalize(message)
        if (text.isBlank()) return null
        if (NEGATION.containsMatchIn(text)) return null

        val dirMatch = DIRECTION.find(text) ?: return null
        val direction = when (dirMatch.groupValues[1]) {
            "buy", "buying", "long" -> Direction.BUY
            else -> Direction.SELL
        }
        val symbolMatch = findSymbol(text) ?: return null

        // Take SL/TP first and blank them out, so "sl at 2338" can't be read as an entry.
        val blanked = StringBuilder(text)
        val stopLoss = STOP_LOSS.find(text)?.let { m ->
            blank(blanked, m.range)
            m.groupValues[1]
        }
        STOP_LOSS.findAll(text).forEach { blank(blanked, it.range) }

        val takeProfits = mutableListOf<Pair<Int, String>>()
        TAKE_PROFIT.findAll(text).forEachIndexed { order, m ->
            val index = m.groupValues[1].toIntOrNull()
            NUMBER.findAll(m.groupValues[2]).forEachIndexed { i, n ->
                // Indexed TPs ("tp2 2352") sort by index; plain ones keep their order.
                takeProfits += ((index ?: (order + 1)) * 100 + i) to n.value
            }
            blank(blanked, m.range)
        }
        val tps = takeProfits.sortedBy { it.first }.map { it.second }.distinct()

        val rest = blanked.toString()
        val entry = ENTRY.find(rest)?.groupValues?.get(1)?.let(::normalizeRange)
            ?: implicitEntry(rest, dirMatch.range.last + 1)
            ?: implicitEntry(rest, symbolMatch.second.last + 1)
        val market = MARKET.containsMatchIn(rest)

        val hasEntry = entry != null || market
        val hasExit = stopLoss != null || tps.isNotEmpty()
        if (!hasEntry && !hasExit) return null

        // Recaps and questions only count when they read like a complete fresh call.
        val complete = hasEntry && hasExit
        if (!complete && (RECAP.containsMatchIn(text) || text.contains('?'))) return null
        if (RECAP_STRONG.containsMatchIn(text)) return null

        return Signal(
            direction = direction,
            symbol = symbolMatch.first,
            entry = entry,
            marketEntry = market && entry == null,
            stopLoss = stopLoss,
            takeProfits = tps,
            raw = message.trim(),
        )
    }

    private fun implicitEntry(text: String, from: Int): String? {
        if (from >= text.length) return null
        return IMPLICIT_ENTRY.find(text.substring(from))?.groupValues?.get(1)?.let(::normalizeRange)
    }

    private fun findSymbol(text: String): Pair<String, IntRange>? {
        var best: Pair<String, IntRange>? = null
        for ((regex, canonical) in SYMBOL_PATTERNS) {
            val m = regex.find(text) ?: continue
            val symbol = canonical ?: (m.groupValues[1] + m.groupValues[2]).uppercase()
            if (best == null || m.range.first < best.second.first) best = symbol to m.range
        }
        return best
    }

    private fun blank(sb: StringBuilder, range: IntRange) {
        for (i in range) sb.setCharAt(i, ' ')
    }

    private fun normalizeRange(value: String): String =
        value.replace(Regex("\\s*-\\s*"), "-")

    companion object {
        private const val NUM = "(?<![a-z\\d.])\\d+(?:\\.\\d+)?(?![a-z\\d])"
        private val NUMBER = Regex(NUM)

        private val DIRECTION = Regex("\\b(buy|buying|long|sell|selling|short)\\b")

        private val NEGATION = Regex(
            "\\b(?:don't|dont|do not|never|avoid|no|not)\\s+(?:\\w+\\s+)?(?:buy|buying|long|sell|selling|short)\\b"
        )

        /** Words that mark a trade update or recap rather than a new call. */
        private val RECAP = Regex(
            "\\b(?:hit|hits|close|closed|closing|booked|secured?|running|banked|move|moved|breakeven|missed|congrats)\\b"
        )

        /** Updates that are never a fresh call, even when they quote prices. */
        private val RECAP_STRONG = Regex(
            "\\b(?:tp\\s?\\d?|sl|stop loss)\\s+(?:was\\s+|got\\s+|is\\s+)?hit\\b|\\bcancel(?:led|ed)?\\b|\\bclosed\\b"
        )

        private val MARKET = Regex("\\b(?:now|market|mkt|cmp|instant)\\b")

        private val STOP_LOSS = Regex(
            "(?:\\bsl\\b|\\bstop\\s?loss\\b|(?<!buy |sell )\\bstop\\b)\\s*(?:at\\s+|@\\s*|is\\s+|price\\s+)?($NUM)"
        )

        private val TAKE_PROFIT = Regex(
            "\\b(?:tp|take\\s?profit)\\s?([1-9])?\\b\\s*(?:at\\s+|@\\s*|is\\s+)?($NUM(?:\\s?[/&]?\\s?$NUM)*)"
        )

        private val ENTRY = Regex(
            "(?:\\b(?:entry|enter|ep|at)\\b|@)\\s*(?:(?:price|zone|around|near|point)\\s+)*($NUM(?:\\s?-\\s?$NUM)?)"
        )

        private val IMPLICIT_ENTRY = Regex(
            "^\\s*(?:(?:limit|stop|now|order|zone|@)\\s*)*($NUM(?:\\s?-\\s?$NUM)?)"
        )

        private const val CCY = "eur|gbp|usd|jpy|aud|nzd|cad|chf"

        private val ALIASES: Map<String, List<String>> = mapOf(
            "XAUUSD" to listOf("xauusd", "xau", "gold"),
            "XAGUSD" to listOf("xagusd", "xag", "silver"),
            "US30" to listOf("us30", "dj30", "dow jones", "dow", "wall street", "wallstreet"),
            "NAS100" to listOf("nas100", "nasdaq100", "nasdaq", "nas", "us100", "ustec", "nq"),
            "SPX500" to listOf("spx500", "sp500", "spx", "us500", "s&p500", "s&p"),
            "GER40" to listOf("ger40", "de40", "ger30", "dax"),
            "UK100" to listOf("uk100", "ftse"),
            "BTCUSD" to listOf("btcusd", "btc", "bitcoin"),
            "ETHUSD" to listOf("ethusd", "eth", "ethereum"),
            "USOIL" to listOf("usoil", "xtiusd", "wti", "oil"),
        )

        /** (pattern, canonical symbol); a null canonical means "build it from the two currency groups". */
        private val SYMBOL_PATTERNS: List<Pair<Regex, String?>> = buildList {
            for ((canonical, aliases) in ALIASES) {
                val alt = aliases.sortedByDescending { it.length }
                    .joinToString("|") { Regex.escape(it).replace(" ", "\\s?") }
                add(Regex("(?<![a-z0-9&])(?:$alt)(?:\\.[a-z]{1,3})?(?![a-z0-9&])") to canonical)
            }
            add(Regex("(?<![a-z0-9])($CCY|xau|xag)($CCY)(?:\\.[a-z]{1,3}|m)?(?![a-z0-9])") to null)
        }

        internal fun normalize(message: String): String {
            var t = message.lowercase()
            t = t.replace('–', '-').replace('—', '-').replace('’', '\'')
            t = t.replace(Regex("\\bs\\s?[/.]\\s?l\\b\\.?"), "sl")
            t = t.replace(Regex("\\bt\\s?[/.]\\s?p(\\d?)\\b\\.?"), "tp$1")
            // "xau/usd", "eur / usd" -> "xauusd", "eurusd"
            t = t.replace(Regex("\\b([a-z]{3})\\s*/\\s*([a-z]{3})\\b"), "$1$2")
            // Thousands separators: "19,850" -> "19850"
            repeat(3) { t = t.replace(Regex("(\\d),(\\d{3})(?!\\d)"), "$1$2") }
            // Clock times are never prices: "14:30", "8:05pm"
            t = t.replace(Regex("\\b\\d{1,2}:\\d{2}\\s*(?:am|pm)?\\b"), " ~ ")
            // Numbers with units (lots, pips, percent, times) are never prices either.
            t = t.replace(
                Regex("(?<![a-z\\d.])\\d+(?:\\.\\d+)?\\s*(?:%|(?:am|pm|pips?|points?|pts|lots?|k|mins?|minutes?|hours?|hrs?|h|x|rr|usd|dollars?|bucks)(?![a-z0-9]))"),
                " ~ "
            )
            // Direction words that are not trade directions.
            t = t.replace(Regex("\\b(?:long|short)[\\s-]term\\b"), " term ")
            t = t.replace(Regex("\\b(?:how long|as long as|so long|long time|in short)\\b"), " ~ ")
            t = t.replace("@", " @ ")
            // Dashes are separators unless they join two numbers (a zone like 2340-2343).
            t = t.replace(Regex("(?<!\\d)\\s*-|-\\s*(?!\\d)"), " ")
            t = t.replace(Regex("[^a-z0-9.@\\-/&'~? ]"), " ")
            t = t.replace(Regex("(?<!\\d)\\.|\\.(?!\\d)"), " ")
            return t.replace(Regex("\\s+"), " ").trim()
        }
    }
}
