package com.scanner.app.util

import org.junit.Assert.*
import org.junit.Test

class ChannelAnalyzerTest {

    // ─── getOverlappingChannels ──────────────────────────────────────────────────

    @Test
    fun `channel 1 overlaps channels 1 to 3`() {
        assertEquals(listOf(1, 2, 3), ChannelAnalyzer.getOverlappingChannels(1))
    }

    @Test
    fun `channel 6 overlaps channels 4 to 8`() {
        assertEquals(listOf(4, 5, 6, 7, 8), ChannelAnalyzer.getOverlappingChannels(6))
    }

    @Test
    fun `channel 13 overlaps channels 11 to 13`() {
        assertEquals(listOf(11, 12, 13), ChannelAnalyzer.getOverlappingChannels(13))
    }

    @Test
    fun `out of range channel returns empty list`() {
        assertTrue(ChannelAnalyzer.getOverlappingChannels(0).isEmpty())
        assertTrue(ChannelAnalyzer.getOverlappingChannels(14).isEmpty())
    }

    // ─── analyze — empty input ───────────────────────────────────────────────────

    @Test
    fun `analyze with no networks returns zero counts`() {
        val result = ChannelAnalyzer.analyze(emptyList())
        assertEquals(0, result.totalNetworks)
        assertEquals(0, result.networks24Count)
        assertEquals(0, result.networks5Count)
        assertNull(result.connectedChannel)
        assertNull(result.connectedBand)
    }

    @Test
    fun `analyze with no networks returns 13 channels for 2_4GHz band`() {
        val result = ChannelAnalyzer.analyze(emptyList())
        // Channels 1–13 are always present in the analysis
        assertEquals(13, result.channels24.size)
    }

    @Test
    fun `analyze with no networks all channels have zero network count`() {
        val result = ChannelAnalyzer.analyze(emptyList())
        assertTrue(result.channels24.all { it.networkCount == 0 })
        assertTrue(result.channels5.all { it.networkCount == 0 })
    }

    // ─── analyze — with networks ─────────────────────────────────────────────────

    @Test
    fun `analyze counts 2_4GHz and 5GHz networks correctly`() {
        val networks = listOf(
            makeWifi(band = "2.4 GHz", channel = 6),
            makeWifi(band = "2.4 GHz", channel = 11),
            makeWifi(band = "5 GHz", channel = 36)
        )
        val result = ChannelAnalyzer.analyze(networks)
        assertEquals(3, result.totalNetworks)
        assertEquals(2, result.networks24Count)
        assertEquals(1, result.networks5Count)
    }

    @Test
    fun `analyze identifies connected network`() {
        val networks = listOf(
            makeWifi(band = "2.4 GHz", channel = 1, isConnected = true)
        )
        val result = ChannelAnalyzer.analyze(networks)
        assertEquals(1, result.connectedChannel)
        assertEquals("2.4 GHz", result.connectedBand)
    }

    @Test
    fun `analyze 2_4GHz recommendations only include channels 1 6 11`() {
        val result = ChannelAnalyzer.analyze(emptyList())
        val recChannels = result.recommendations24.map { it.channel }.toSet()
        assertEquals(setOf(1, 6, 11), recChannels)
    }

    @Test
    fun `analyze free channel gets higher score than congested channel`() {
        // Channel 6 has 8 networks, channel 11 is free
        val networks = (1..8).map { makeWifi(band = "2.4 GHz", channel = 6) }
        val result = ChannelAnalyzer.analyze(networks)
        val ch6rec = result.recommendations24.first { it.channel == 6 }
        val ch11rec = result.recommendations24.first { it.channel == 11 }
        assertTrue("Ch11 score should be >= ch6 score", ch11rec.score >= ch6rec.score)
    }

    // ─── Helper ──────────────────────────────────────────────────────────────────

    private fun makeWifi(
        band: String = "2.4 GHz",
        channel: Int = 6,
        signalStrength: Int = -65,
        isConnected: Boolean = false
    ) = com.scanner.app.data.WifiNetwork(
        ssid = "TestNet",
        bssid = "AA:BB:CC:DD:EE:FF",
        signalStrength = signalStrength,
        frequency = if (band == "2.4 GHz") 2437 else 5180,
        channel = channel,
        securityType = "WPA2",
        isConnected = isConnected,
        band = band
    )
}
