package com.scanner.app.util

import androidx.compose.ui.graphics.toArgb
import com.scanner.app.R
import org.junit.Assert.*
import org.junit.Test

/**
 * Note: SignalHelper.signalColor() returns androidx.compose.ui.graphics.Color.
 * Color is a value class backed by a ULong — it can be instantiated in JVM unit
 * tests without an Android device (no Android framework dependency).
 */
class SignalHelperTest {

    // ─── signalFraction ──────────────────────────────────────────────────────────

    @Test
    fun `signalFraction at max dBm returns 1_0`() {
        assertEquals(1.0f, SignalHelper.signalFraction(-30), 0.001f)
    }

    @Test
    fun `signalFraction at min dBm returns 0_0`() {
        assertEquals(0.0f, SignalHelper.signalFraction(-100), 0.001f)
    }

    @Test
    fun `signalFraction clamps above max`() {
        assertEquals(1.0f, SignalHelper.signalFraction(0), 0.001f)
    }

    @Test
    fun `signalFraction clamps below min`() {
        assertEquals(0.0f, SignalHelper.signalFraction(-150), 0.001f)
    }

    @Test
    fun `signalFraction at midpoint is approximately 0_5`() {
        // midpoint between -100 and -30 is -65
        val fraction = SignalHelper.signalFraction(-65)
        assertEquals(0.5f, fraction, 0.02f)
    }

    // ─── wifiQualityResId ────────────────────────────────────────────────────────
    @Test
    fun `wifiQualityResId at -45 dBm is excellent`() {
        assertEquals(R.string.quality_excellent, SignalHelper.wifiQualityResId(-45))
    }

    @Test
    fun `wifiQualityResId at -55 dBm is very good`() {
        assertEquals(R.string.quality_very_good, SignalHelper.wifiQualityResId(-55))
    }

    @Test
    fun `wifiQualityResId at -65 dBm is good`() {
        assertEquals(R.string.quality_good, SignalHelper.wifiQualityResId(-65))
    }

    @Test
    fun `wifiQualityResId at -75 dBm is fair`() {
        assertEquals(R.string.quality_fair, SignalHelper.wifiQualityResId(-75))
    }

    @Test
    fun `wifiQualityResId at -90 dBm is poor`() {
        assertEquals(R.string.quality_poor, SignalHelper.wifiQualityResId(-90))
    }

    @Test
    fun `wifiQualityResId boundary at exactly -50 dBm is excellent`() {
        assertEquals(R.string.quality_excellent, SignalHelper.wifiQualityResId(-50))
    }

    @Test
    fun `wifiQualityResId boundary at exactly -60 dBm is very good`() {
        assertEquals(R.string.quality_very_good, SignalHelper.wifiQualityResId(-60))
    }

    // ─── bluetoothQualityResId ──────────────────────────────────────────────────
    @Test
    fun `bluetoothQualityResId at -50 is strong`() {
        assertEquals(R.string.quality_strong, SignalHelper.bluetoothQualityResId(-50))
    }

    @Test
    fun `bluetoothQualityResId at -70 is fair`() {
        assertEquals(R.string.quality_fair, SignalHelper.bluetoothQualityResId(-70))
    }

    @Test
    fun `bluetoothQualityResId at -90 is poor`() {
        assertEquals(R.string.quality_poor, SignalHelper.bluetoothQualityResId(-90))
    }

    // ─── signalColor (Compose Color, accessible in JVM unit tests) ───────────────

    @Test
    fun `signalColor for strong signal is green`() {
        val color = SignalHelper.signalColor(0.8f)
        // Color(0xFF4CAF50) — compare packed value
        assertEquals(0xFF4CAF50.toInt(), color.toArgb())
    }

    @Test
    fun `signalColor for medium signal is orange`() {
        val color = SignalHelper.signalColor(0.5f)
        assertEquals(0xFFFF9800.toInt(), color.toArgb())
    }

    @Test
    fun `signalColor for weak signal is red`() {
        val color = SignalHelper.signalColor(0.2f)
        assertEquals(0xFFF44336.toInt(), color.toArgb())
    }

    @Test
    fun `signalColor boundary 0_7 is green`() {
        // fraction >= 0.7 is green
        val color = SignalHelper.signalColor(0.7f)
        assertEquals(0xFF4CAF50.toInt(), color.toArgb())
    }

    @Test
    fun `signalColor boundary 0_4 is orange`() {
        // fraction >= 0.4 (but < 0.7) is orange
        val color = SignalHelper.signalColor(0.4f)
        assertEquals(0xFFFF9800.toInt(), color.toArgb())
    }
}
