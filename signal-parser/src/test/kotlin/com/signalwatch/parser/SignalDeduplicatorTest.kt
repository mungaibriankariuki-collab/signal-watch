package com.signalwatch.parser

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class SignalDeduplicatorTest {

    private val parser = SignalParser()
    private fun sig(msg: String) = parser.parse(msg)!!
    private val min = 60_000L

    @Test fun `mod repost with more levels upgrades and does not re-alert`() {
        val dedup = SignalDeduplicator()
        assertTrue(dedup.offer(sig("Sell gold @ 2368 sl 2375"), 0) is SignalDeduplicator.Result.New)
        val r = dedup.offer(sig("SELL XAUUSD 2368 SL 2375 TP1 2360 TP2 2352"), 2 * min)
        assertTrue(r is SignalDeduplicator.Result.Improved)
        assertEquals(listOf("2360", "2352"), r.signal.takeProfits)
    }

    @Test fun `identical repost is a duplicate`() {
        val dedup = SignalDeduplicator()
        dedup.offer(sig("Sell gold @ 2368 sl 2375 tp 2360"), 0)
        val r = dedup.offer(sig("sell gold 2368 sl 2375 tp 2360"), 3 * min)
        assertTrue(r is SignalDeduplicator.Result.Duplicate)
    }

    @Test fun `less complete repost keeps the fuller version`() {
        val dedup = SignalDeduplicator()
        dedup.offer(sig("Sell gold @ 2368 sl 2375 tp 2360"), 0)
        val r = dedup.offer(sig("sell gold 2368"), min)
        assertTrue(r is SignalDeduplicator.Result.Duplicate)
        assertEquals("2375", r.signal.stopLoss)
    }

    @Test fun `repost without entry merges with the earlier entry`() {
        val dedup = SignalDeduplicator()
        dedup.offer(sig("sell gold 2368"), 0)
        val r = dedup.offer(sig("sell gold sl 2375 tp 2360"), min)
        assertTrue(r is SignalDeduplicator.Result.Improved)
        assertEquals("2368", r.signal.entry)
        assertEquals("2375", r.signal.stopLoss)
    }

    @Test fun `same call after ten minutes alerts again`() {
        val dedup = SignalDeduplicator()
        dedup.offer(sig("Sell gold @ 2368 sl 2375"), 0)
        assertTrue(dedup.offer(sig("Sell gold @ 2368 sl 2375"), 11 * min) is SignalDeduplicator.Result.New)
    }

    @Test fun `different entry is a new signal`() {
        val dedup = SignalDeduplicator()
        dedup.offer(sig("Sell gold @ 2368 sl 2375"), 0)
        assertTrue(dedup.offer(sig("Sell gold @ 2380 sl 2388"), min) is SignalDeduplicator.Result.New)
    }

    @Test fun `opposite direction is a new signal`() {
        val dedup = SignalDeduplicator()
        dedup.offer(sig("Sell gold @ 2368 sl 2375"), 0)
        assertTrue(dedup.offer(sig("Buy gold @ 2368 sl 2360"), min) is SignalDeduplicator.Result.New)
    }

    @Test fun `different symbol is a new signal`() {
        val dedup = SignalDeduplicator()
        dedup.offer(sig("Sell gold @ 2368 sl 2375"), 0)
        assertTrue(dedup.offer(sig("Sell eurusd @ 1.0845 sl 1.0870"), min) is SignalDeduplicator.Result.New)
    }
}
