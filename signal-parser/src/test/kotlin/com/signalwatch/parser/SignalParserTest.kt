package com.signalwatch.parser

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class SignalParserTest {

    private val parser = SignalParser()

    private fun parse(msg: String): Signal {
        val signal = parser.parse(msg)
        assertNotNull("expected a signal from: $msg", signal)
        return signal!!
    }

    private fun assertNoSignal(msg: String) =
        assertNull("expected NO signal from: $msg", parser.parse(msg))

    // ---- Signals that must be caught ----

    @Test fun `classic one-liner`() {
        val s = parse("BUY XAUUSD 2345.50 SL 2338 TP 2360")
        assertEquals(Direction.BUY, s.direction)
        assertEquals("XAUUSD", s.symbol)
        assertEquals("2345.50", s.entry)
        assertEquals("2338", s.stopLoss)
        assertEquals(listOf("2360"), s.takeProfits)
    }

    @Test fun `pipe separated with three take profits and gold alias`() {
        val s = parse("Sell gold @ 2368 | SL 2375 | TP1 2360 | TP2 2352 | TP3 2340")
        assertEquals(Direction.SELL, s.direction)
        assertEquals("XAUUSD", s.symbol)
        assertEquals("2368", s.entry)
        assertEquals("2375", s.stopLoss)
        assertEquals(listOf("2360", "2352", "2340"), s.takeProfits)
    }

    @Test fun `market entry over line breaks`() {
        val s = parse("gold sell now\nsl: 2375\ntp: 2355")
        assertEquals(Direction.SELL, s.direction)
        assertNull(s.entry)
        assertTrue(s.marketEntry)
        assertEquals("Market", s.entryLabel)
        assertEquals("2375", s.stopLoss)
        assertEquals(listOf("2355"), s.takeProfits)
    }

    @Test fun `long with entry stop and take profit words`() {
        val s = parse("EURUSD long entry 1.0845 stop 1.0820 take profit 1.0890")
        assertEquals(Direction.BUY, s.direction)
        assertEquals("EURUSD", s.symbol)
        assertEquals("1.0845", s.entry)
        assertEquals("1.0820", s.stopLoss)
        assertEquals(listOf("1.0890"), s.takeProfits)
    }

    @Test fun `short index with thousands separators and dashes`() {
        val s = parse("Short NAS100 at 19,850 - SL 19,920 - TP 19,700")
        assertEquals(Direction.SELL, s.direction)
        assertEquals("NAS100", s.symbol)
        assertEquals("19850", s.entry)
        assertEquals("19920", s.stopLoss)
        assertEquals(listOf("19700"), s.takeProfits)
    }

    @Test fun `nasdaq alias with buy limit and slash separated targets`() {
        val s = parse("nasdaq buy limit 19700 sl 19640 tp 19820/19900")
        assertEquals("NAS100", s.symbol)
        assertEquals("19700", s.entry)
        assertEquals(listOf("19820", "19900"), s.takeProfits)
    }

    @Test fun `nas alias`() {
        val s = parse("Nas sell 19880 sl 19950")
        assertEquals("NAS100", s.symbol)
        assertEquals(Direction.SELL, s.direction)
        assertEquals("19880", s.entry)
        assertEquals("19950", s.stopLoss)
    }

    @Test fun `us30 with commas and stop loss words`() {
        val s = parse("US30 SELL 39250, stop loss 39400, tp 39000")
        assertEquals("US30", s.symbol)
        assertEquals("39250", s.entry)
        assertEquals("39400", s.stopLoss)
        assertEquals(listOf("39000"), s.takeProfits)
    }

    @Test fun `gbpjpy with at sign and two targets`() {
        val s = parse("GBPJPY buy @ 191.20 sl 190.70 tp1 191.80 tp2 192.40")
        assertEquals("GBPJPY", s.symbol)
        assertEquals("191.20", s.entry)
        assertEquals("190.70", s.stopLoss)
        assertEquals(listOf("191.80", "192.40"), s.takeProfits)
    }

    @Test fun `slash pair notation`() {
        val s = parse("Buy USD/JPY 151.30 SL 150.90 TP 152.00")
        assertEquals("USDJPY", s.symbol)
        assertEquals("151.30", s.entry)
        assertEquals(listOf("152.00"), s.takeProfits)
    }

    @Test fun `sell stop order is not read as a stop loss`() {
        val s = parse("XAU/USD SELL STOP 2330 SL 2338 TP 2315")
        assertEquals(Direction.SELL, s.direction)
        assertEquals("XAUUSD", s.symbol)
        assertEquals("2330", s.entry)
        assertEquals("2338", s.stopLoss)
    }

    @Test fun `entry zone is kept as a range`() {
        val s = parse("gold buy zone 2340-2343 sl 2335 tp 2350")
        assertEquals("2340-2343", s.entry)
        assertEquals("2335", s.stopLoss)
    }

    @Test fun `caps with emoji and repeated tp`() {
        val s = parse("LONG GOLD NOW 🚀 SL 2331 TP 2352 TP 2360")
        assertEquals(Direction.BUY, s.direction)
        assertTrue(s.marketEntry)
        assertEquals(listOf("2352", "2360"), s.takeProfits)
    }

    @Test fun `colon and comma separators`() {
        val s = parse("sell gbpusd: entry 1.2710, sl 1.2745, tp 1.2650")
        assertEquals("GBPUSD", s.symbol)
        assertEquals("1.2710", s.entry)
        assertEquals("1.2745", s.stopLoss)
        assertEquals(listOf("1.2650"), s.takeProfits)
    }

    @Test fun `take profit only still counts`() {
        val s = parse("buy gold, tp 2360")
        assertEquals(Direction.BUY, s.direction)
        assertNull(s.entryLabel)
        assertNull(s.stopLoss)
        assertEquals(listOf("2360"), s.takeProfits)
    }

    @Test fun `selling and stoploss spelled together`() {
        val s = parse("selling xauusd at 2366 stoploss 2372 tp 2350")
        assertEquals(Direction.SELL, s.direction)
        assertEquals("2366", s.entry)
        assertEquals("2372", s.stopLoss)
    }

    @Test fun `multi line owner template`() {
        val s = parse("XAUUSD\nBUY\nEntry: 2,345\nSL: 2,338\nTP1: 2,352\nTP2: 2,360")
        assertEquals(Direction.BUY, s.direction)
        assertEquals("2345", s.entry)
        assertEquals("2338", s.stopLoss)
        assertEquals(listOf("2352", "2360"), s.takeProfits)
    }

    @Test fun `targets before direction`() {
        val s = parse("TP1 2352 TP2 2360 buy gold 2345 sl 2338")
        assertEquals("2345", s.entry)
        assertEquals("2338", s.stopLoss)
        assertEquals(listOf("2352", "2360"), s.takeProfits)
    }

    @Test fun `s slash l and t slash p abbreviations`() {
        val s = parse("s/l 2338 t/p 2360 buy xau 2345")
        assertEquals("XAUUSD", s.symbol)
        assertEquals("2345", s.entry)
        assertEquals("2338", s.stopLoss)
        assertEquals(listOf("2360"), s.takeProfits)
    }

    @Test fun `buy now with exclamation`() {
        val s = parse("Buy gold now! SL 2338, TP 2360")
        assertTrue(s.marketEntry)
        assertEquals("2338", s.stopLoss)
    }

    @Test fun `crypto alias with thousands`() {
        val s = parse("BTC long 64,200 sl 63,500 tp 66,000")
        assertEquals("BTCUSD", s.symbol)
        assertEquals("64200", s.entry)
        assertEquals("63500", s.stopLoss)
        assertEquals(listOf("66000"), s.takeProfits)
    }

    @Test fun `dax alias`() {
        val s = parse("Sell DAX 18450 SL 18520 TP 18300")
        assertEquals("GER40", s.symbol)
        assertEquals("18450", s.entry)
    }

    @Test fun `full call with management note is still a signal`() {
        val s = parse("Sell gold 2368 sl 2375 tp1 2360 tp2 2352, close half at tp1")
        assertEquals("2368", s.entry)
        assertEquals(listOf("2360", "2352"), s.takeProfits)
    }

    @Test fun `entry price wins over market word`() {
        val s = parse("sell xauusd now @ 2368 sl 2375")
        assertEquals("2368", s.entry)
        assertFalse(s.marketEntry)
    }

    @Test fun `raw message is kept`() {
        val msg = "  BUY XAUUSD 2345 SL 2338 TP 2360  "
        assertEquals(msg.trim(), parse(msg).raw)
    }

    // ---- Messages that must NOT trigger ----

    @Test fun `question about lot size`() = assertNoSignal("what lot size for gold?")

    @Test fun `sl hit chatter`() = assertNoSignal("SL hit guys")

    @Test fun `risk management advice`() = assertNoSignal("risk max 1% per trade")

    @Test fun `tp hit recap with sl move`() = assertNoSignal("Gold sell TP1 hit 🎯 move SL to 2360")

    @Test fun `question with a price`() = assertNoSignal("Should I buy gold at 2350?")

    @Test fun `negated direction`() = assertNoSignal("Don't sell gold here, wait for 2380")

    @Test fun `market commentary`() = assertNoSignal("gold looking bullish today")

    @Test fun `closed trade recap`() = assertNoSignal("Closed my gold buy at 2360, +150 pips")

    @Test fun `closed trade recap with levels`() = assertNoSignal("Closed gold buy at 2360, sl was 2338")

    @Test fun `direction and symbol without levels`() = assertNoSignal("buy gold")

    @Test fun `stream time is not an entry`() = assertNoSignal("Going live at 8pm, we'll buy gold if it breaks")

    @Test fun `clock time is not an entry`() = assertNoSignal("buy the dip on gold at 14:30 today")

    @Test fun `long term is not a direction`() = assertNoSignal("long term I'm bullish on gold, entry soon")

    @Test fun `how long is not a direction`() = assertNoSignal("how long are we holding the eurusd sell?")

    @Test fun `lot sizes are not prices`() = assertNoSignal("Use 0.5 lots on gold buys")

    @Test fun `no symbol`() = assertNoSignal("Sell 2350 sl 2360 tp 2340")

    @Test fun `running trade recap`() = assertNoSignal("running +80 pips on the gold sell from 2368")

    @Test fun `cancelled order`() = assertNoSignal("cancel the sell gold limit 2370")

    @Test fun `congrats with question`() = assertNoSignal("Gold hit TP2, congrats! next sell gold?")

    @Test fun `tp hit with price`() = assertNoSignal("TP 2360 hit ✅ on gold buy")

    @Test fun `past tense trade`() = assertNoSignal("we sold gold at 2368 earlier, nice")

    @Test fun `bias without levels`() = assertNoSignal("short is my bias on gold today, waiting")

    @Test fun `account size talk`() = assertNoSignal("Lot size 0.01 per \$100, sell gold only with confirmation")

    @Test fun `empty message`() = assertNoSignal("   ")
}
