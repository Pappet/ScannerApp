package com.scanner.app.ui.viewmodel

import android.app.Application
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.lifecycle.AndroidViewModel
import com.scanner.app.util.ChannelAnalysis
import com.scanner.app.util.ChannelAnalyzer
import com.scanner.app.util.WifiScanner

class ChannelAnalysisViewModel(app: Application) : AndroidViewModel(app) {
    val wifiScanner = WifiScanner(app)

    var analysis by mutableStateOf<ChannelAnalysis?>(null)
    var isScanning by mutableStateOf(false)
    var selectedBand by mutableStateOf("2.4")

    fun doScan() {
        if (!wifiScanner.isWifiEnabled()) return
        isScanning = true
        wifiScanner.startScan { results ->
            try {
                analysis = ChannelAnalyzer.analyze(results)
            } catch (e: Exception) {
                android.util.Log.e("ChannelAnalysis", "Error analyzing", e)
            }
            isScanning = false
        }
    }

    override fun onCleared() {
        super.onCleared()
        wifiScanner.cleanup()
    }
}
