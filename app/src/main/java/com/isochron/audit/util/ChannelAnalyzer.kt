package com.scanner.app.util

import com.scanner.app.data.WifiNetwork

/**
 * Represents channel utilization data for a single WiFi channel.
 */
data class ChannelInfo(
    val channel: Int,           // Channel number (e.g., 1, 6, 11)
    val frequency: Int,         // Center frequency in MHz
    val band: String,            // "2.4 GHz" or "5 GHz"
    val networkCount: Int,       // Number of networks centered on this channel
    val networks: List<WifiNetwork>,
    val averageSignal: Int?,     // Average RSSI of all centered networks
    val strongestSignal: Int?,   // Maximum RSSI on this channel
    val overlapScore: Float      // Congestion metric: 0.0 (completely free) to 1.0 (highly congested)
)

/**
 * Recommendation for the best channel to use in a given band.
 */
data class ChannelRecommendation(
    val channel: Int,
    val band: String,
    val reason: String,         // Human-readable explanation for the recommendation
    val score: Float            // Fitness score: 0.0 (worst/congested) to 1.0 (optimal)
)

/**
 * Summary of WiFi environment analysis.
 */
data class ChannelAnalysis(
    val channels24: List<ChannelInfo>,
    val channels5: List<ChannelInfo>,
    val recommendations24: List<ChannelRecommendation>,
    val recommendations5: List<ChannelRecommendation>,
    val totalNetworks: Int,
    val networks24Count: Int,
    val networks5Count: Int,
    val connectedChannel: Int?,
    val connectedBand: String?
)

/**
 * Utility for analyzing the WiFi environment and identifying optimal channels.
 * Accounts for 2.4 GHz adjacent-channel interference (overlapping) and 5 GHz isolation.
 */
object ChannelAnalyzer {

    // 2.4 GHz non-overlapping channels
    private val CHANNELS_24 = (1..13).toList()
    // 5 GHz common channels
    private val CHANNELS_5 = listOf(36, 40, 44, 48, 52, 56, 60, 64, 100, 104, 108, 112, 116, 120, 124, 128, 132, 136, 140, 149, 153, 157, 161, 165)

    /**
     * Performs a comprehensive channel audit on the provided WiFi scan results.
     * Calculates overlap scores and generates usage recommendations for both 2.4 GHz and 5 GHz bands.
     *
     * @param networks List of [WifiNetwork]s discovered in a scan.
     * @return A summarized [ChannelAnalysis] report.
     */
    fun analyze(networks: List<WifiNetwork>): ChannelAnalysis {
        val networks24 = networks.filter { it.band == "2.4 GHz" }
        val networks5 = networks.filter { it.band == "5 GHz" }
        val connected = networks.find { it.isConnected }

        val channels24 = CHANNELS_24.map { ch ->
            buildChannelInfo(ch, "2.4 GHz", networks24, channelWidth = 5)
        }
        val channels5 = CHANNELS_5.map { ch ->
            buildChannelInfo(ch, "5 GHz", networks5, channelWidth = 4)
        }

        val recs24 = generateRecommendations(channels24, "2.4 GHz")
        val recs5 = generateRecommendations(channels5, "5 GHz")

        return ChannelAnalysis(
            channels24 = channels24,
            channels5 = channels5,
            recommendations24 = recs24,
            recommendations5 = recs5,
            totalNetworks = networks.size,
            networks24Count = networks24.size,
            networks5Count = networks5.size,
            connectedChannel = connected?.channel,
            connectedBand = connected?.band
        )
    }

    private fun buildChannelInfo(
        channel: Int,
        band: String,
        networks: List<WifiNetwork>,
        @Suppress("UNUSED_PARAMETER") channelWidth: Int
    ): ChannelInfo {
        // In 2.4 GHz, each channel overlaps ±2 channels (20 MHz width, 5 MHz spacing)
        // In 5 GHz, channels generally don't overlap (20 MHz width, 20 MHz spacing)
        val overlapping = if (band == "2.4 GHz") {
            networks.filter { kotlin.math.abs(it.channel - channel) <= 2 }
        } else {
            networks.filter { it.channel == channel }
        }

        val directNetworks = networks.filter { it.channel == channel }
        val signals = overlapping.map { it.signalStrength }

        // Overlap score: combination of number of networks and their signal strength
        val overlapScore = if (overlapping.isEmpty()) 0f
        else {
            val countFactor = (overlapping.size.toFloat() / 10f).coerceAtMost(1f)
            val signalFactor = signals.map { normalizeSignal(it) }.average().toFloat()
            (countFactor * 0.6f + signalFactor * 0.4f).coerceIn(0f, 1f)
        }

        return ChannelInfo(
            channel = channel,
            frequency = channelToFrequency(channel),
            band = band,
            networkCount = directNetworks.size,
            networks = directNetworks,
            averageSignal = signals.takeIf { it.isNotEmpty() }?.average()?.toInt(),
            strongestSignal = signals.maxOrNull(),
            overlapScore = overlapScore
        )
    }

    private fun generateRecommendations(
        channels: List<ChannelInfo>,
        band: String
    ): List<ChannelRecommendation> {
        // For 2.4 GHz, only recommend non-overlapping channels (1, 6, 11)
        val candidates = if (band == "2.4 GHz") {
            channels.filter { it.channel in listOf(1, 6, 11) }
        } else {
            channels
        }

        return candidates
            .map { ch ->
                val score = 1f - ch.overlapScore
                val reason = when {
                    ch.networkCount == 0 -> "Frei — kein Netzwerk auf diesem Kanal"
                    ch.networkCount == 1 && (ch.strongestSignal ?: -100) < -75 ->
                        "Nur 1 schwaches Netzwerk (${ch.strongestSignal} dBm)"
                    ch.networkCount <= 2 -> "${ch.networkCount} Netzwerke, geringe Auslastung"
                    ch.networkCount <= 5 -> "${ch.networkCount} Netzwerke, moderate Auslastung"
                    else -> "${ch.networkCount} Netzwerke, stark ausgelastet"
                }
                ChannelRecommendation(
                    channel = ch.channel,
                    band = band,
                    reason = reason,
                    score = score
                )
            }
            .sortedByDescending { it.score }
    }

    /**
     * Normalizes a signal strength (RSSI) from dBm to a 0.0..1.0 range for scoring.
     */
    private fun normalizeSignal(dbm: Int): Float {
        return ((dbm + 100f) / 70f).coerceIn(0f, 1f)
    }

    /**
     * Converts a WiFi channel number to its center frequency in MHz.
     */
    private fun channelToFrequency(channel: Int): Int = when {
        channel in 1..13 -> 2412 + (channel - 1) * 5
        channel == 14 -> 2484
        channel in 36..64 -> 5180 + (channel - 36) * 5
        channel in 100..144 -> 5500 + (channel - 100) * 5
        channel in 149..165 -> 5745 + (channel - 149) * 5
        else -> 0
    }

    /**
     * Get overlapping channels for a given 2.4 GHz channel.
     * Returns list of channel numbers that overlap.
     */
    fun getOverlappingChannels(channel: Int): List<Int> {
        if (channel < 1 || channel > 13) return emptyList()
        return (maxOf(1, channel - 2)..minOf(13, channel + 2)).toList()
    }
}
