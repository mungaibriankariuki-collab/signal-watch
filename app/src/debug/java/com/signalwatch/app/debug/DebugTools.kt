package com.signalwatch.app.debug

import java.util.Locale

/** Debug builds only (the release source set has an empty stub). */
object DebugTools {
    const val AVAILABLE = true

    /** Any public, embeddable video works; the player just needs something to show. */
    const val SAMPLE_VIDEO_ID = "jNQXAC9IVRw"

    /**
     * A few minutes of fake chat. Prices shift every [round] so the de-duplicator (10 min window)
     * doesn't swallow the second loop's signals.
     */
    fun script(round: Int, ownerName: String): List<SimMessage> {
        val p = 2360 + round * 7
        fun f(v: Double) = String.format(Locale.US, "%.2f", v)
        val owner = ownerName
        return listOf(
            SimMessage(2_000, "Kwame T", false, false, "gm everyone 🔥"),
            SimMessage(3_000, "Ann W", false, false, "what lot size for gold?"),
            SimMessage(3_000, "Mod Kev", false, true, "risk max 1% per trade"),
            SimMessage(4_000, "random_pips", false, false, "BUY GOLD NOW SL 2300 TP 2500"),
            SimMessage(5_000, owner, true, false, "Sell gold @ ${p + 8} | SL ${p + 15} | TP1 $p | TP2 ${p - 8}"),
            SimMessage(4_000, "Mod Kev", false, true, "SELL XAUUSD ${p + 8} SL ${p + 15} TP1 $p TP2 ${p - 8} TP3 ${p - 20}"),
            SimMessage(3_000, "Brian K", false, false, "in! thanks"),
            SimMessage(4_000, "Lucy M", false, false, "SL hit guys"),
            SimMessage(6_000, owner, true, false, "be patient, no fomo"),
            SimMessage(8_000, owner, true, false,
                "EURUSD long entry ${f(1.0840 + round * 0.001)} stop ${f(1.0815 + round * 0.001)} take profit ${f(1.0890 + round * 0.001)}"),
            SimMessage(4_000, "Joe P", false, false, "how long are we holding?"),
            SimMessage(10_000, "Mod Kev", false, true, "Gold TP1 hit 🎯 move SL to entry"),
            SimMessage(8_000, "Sam", false, false, "🚀🚀🚀"),
        )
    }
}
