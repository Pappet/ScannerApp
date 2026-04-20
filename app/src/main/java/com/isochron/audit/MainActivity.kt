package com.scanner.app

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.pager.HorizontalPager
import androidx.compose.foundation.pager.rememberPagerState
import androidx.compose.ui.draw.clipToBounds
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.BarChart
import androidx.compose.material.icons.outlined.Bluetooth
import androidx.compose.material.icons.outlined.Inventory2
import androidx.compose.material.icons.outlined.Lan
import androidx.compose.material.icons.outlined.Map
import androidx.compose.material.icons.outlined.MonitorHeart
import androidx.compose.material.icons.outlined.Shield
import androidx.compose.material.icons.outlined.Wifi
import androidx.compose.material3.Scaffold
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.getValue
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import com.scanner.app.ui.components.SpectrumBottomNav
import com.scanner.app.ui.components.SpectrumTab
import com.scanner.app.ui.screens.BluetoothScreen
import com.scanner.app.ui.screens.ChannelAnalysisScreen
import com.scanner.app.ui.screens.InventoryScreen
import com.scanner.app.ui.screens.LanScreen
import com.scanner.app.ui.screens.MapScreen
import com.scanner.app.ui.screens.MonitorScreen
import com.scanner.app.ui.screens.SecurityAuditScreen
import com.scanner.app.ui.screens.WifiScreen
import com.scanner.app.ui.theme.ScannerAppTheme
import com.scanner.app.ui.theme.Spectrum
import kotlinx.coroutines.launch

/**
 * Main entry point of the application.
 * Initialises edge-to-edge display and sets the root Composable.
 */
class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        setContent {
            ScannerAppTheme {
                ScannerApp()
            }
        }
    }
}

// Tab order matches the Spectrum nav: WIFI, CH, BT, LAN, MON, SEC, MAP, INV.
private val SpectrumTabs = listOf(
    SpectrumTab("wifi", Icons.Outlined.Wifi, "WIFI"),
    SpectrumTab("ch", Icons.Outlined.BarChart, "CH"),
    SpectrumTab("bt", Icons.Outlined.Bluetooth, "BT"),
    SpectrumTab("lan", Icons.Outlined.Lan, "LAN"),
    SpectrumTab("mon", Icons.Outlined.MonitorHeart, "MON"),
    SpectrumTab("sec", Icons.Outlined.Shield, "SEC"),
    SpectrumTab("map", Icons.Outlined.Map, "MAP"),
    SpectrumTab("inv", Icons.Outlined.Inventory2, "INV"),
)

@OptIn(ExperimentalFoundationApi::class)
@Composable
fun ScannerApp() {
    val context = androidx.compose.ui.platform.LocalContext.current
    val prefs = remember { context.getSharedPreferences("scanner_prefs", android.content.Context.MODE_PRIVATE) }
    var onboardingComplete by remember { mutableStateOf(prefs.getBoolean("onboarding_complete", false)) }

    if (!onboardingComplete) {
        com.scanner.app.ui.screens.OnboardingScreen(onDone = {
            prefs.edit().putBoolean("onboarding_complete", true).apply()
            onboardingComplete = true
        })
        return
    }

    val pagerState = rememberPagerState(pageCount = { SpectrumTabs.size })
    val scope = rememberCoroutineScope()
    val selectedKey = remember(pagerState.currentPage) {
        SpectrumTabs[pagerState.currentPage].key
    }

    Scaffold(
        containerColor = Spectrum.Surface,
        bottomBar = {
            SpectrumBottomNav(
                tabs = SpectrumTabs,
                selected = selectedKey,
                onSelect = { key ->
                    val idx = SpectrumTabs.indexOfFirst { it.key == key }
                    if (idx >= 0) scope.launch { pagerState.animateScrollToPage(idx) }
                },
            )
        },
    ) { padding ->
        Column(
            Modifier
                .fillMaxSize()
                .padding(padding)
                .background(Spectrum.Surface),
        ) {
            HorizontalPager(
                state = pagerState,
                beyondBoundsPageCount = 1,
                modifier = Modifier.fillMaxSize(),
            ) { page ->
                // Opaque + clipped: prevents neighboring pages (notably the osmdroid
                // MapView) from drawing into the visible page during transitions.
                Box(
                    Modifier
                        .fillMaxSize()
                        .background(Spectrum.Surface)
                        .clipToBounds(),
                ) {
                    val key = SpectrumTabs[page].key
                    when (key) {
                        "wifi" -> WifiScreen()
                        "ch" -> ChannelAnalysisScreen()
                        "bt" -> BluetoothScreen()
                        "lan" -> LanScreen()
                        "mon" -> MonitorScreen()
                        "sec" -> SecurityAuditScreen()
                        "map" -> MapScreen()
                        "inv" -> InventoryScreen()
                    }
                }
            }
        }
    }
}
