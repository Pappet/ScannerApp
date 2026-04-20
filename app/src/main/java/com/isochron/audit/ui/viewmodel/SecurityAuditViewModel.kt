package com.scanner.app.ui.viewmodel

import android.app.Application
import android.util.Log
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import com.scanner.app.R
import com.scanner.app.data.BluetoothDevice
import com.scanner.app.data.WifiNetwork
import com.scanner.app.util.BluetoothScanner
import com.scanner.app.util.PingUtil
import com.scanner.app.util.PortScanProgress
import com.scanner.app.util.PortScanResult
import com.scanner.app.util.PortScanner
import com.scanner.app.util.SecurityAuditReport
import com.scanner.app.util.SecurityAuditor
import com.scanner.app.util.WellKnownPorts
import com.scanner.app.util.WifiScanner
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

class SecurityAuditViewModel(app: Application) : AndroidViewModel(app) {
    private val wifiScanner = WifiScanner(app)
    private val btScanner = BluetoothScanner(app)
    private val portScanner = PortScanner()
    private val pingUtil = PingUtil(app)

    var wifiNetworks by mutableStateOf<List<WifiNetwork>>(emptyList())
    var btDevices by mutableStateOf<List<BluetoothDevice>>(emptyList())
    var openPorts by mutableStateOf<List<PortScanResult>>(emptyList())
    var report by mutableStateOf<SecurityAuditReport?>(null)
    var isAuditing by mutableStateOf(false)
    var auditPhase by mutableStateOf("")
    var portScanProgress by mutableStateOf<PortScanProgress?>(null)

    fun runAudit() {
        isAuditing = true
        val context = getApplication<Application>()
        openPorts = emptyList()
        viewModelScope.launch {
            try {
                auditPhase = context.getString(R.string.audit_phase_wifi)
                wifiNetworks = try {
                    val deferred = kotlinx.coroutines.CompletableDeferred<List<WifiNetwork>>()
                    wifiScanner.startScan { results -> deferred.complete(results) }
                    kotlinx.coroutines.withTimeoutOrNull(10_000L) { deferred.await() } ?: emptyList()
                } catch (e: Exception) { Log.e("SecurityAudit", "WiFi scan error", e); emptyList() }

                auditPhase = context.getString(R.string.audit_phase_bt)
                btDevices = try {
                    val deferred = kotlinx.coroutines.CompletableDeferred<List<BluetoothDevice>>()
                    btScanner.startScan(
                        durationMs = 6000L,
                        onProgress = { devices ->
                            viewModelScope.launch(Dispatchers.Main.immediate) {
                                try { btDevices = devices } catch (_: Exception) {}
                            }
                        },
                        onComplete = { results -> deferred.complete(results) }
                    )
                    kotlinx.coroutines.withTimeoutOrNull(15_000L) { deferred.await() } ?: emptyList()
                } catch (e: Exception) { Log.e("SecurityAudit", "BT scan error", e); emptyList() }

                try {
                    val gateway = pingUtil.getNetworkInfo().gatewayIp
                    if (gateway != null) {
                        auditPhase = context.getString(R.string.audit_phase_ports, gateway)
                        val scanResults = portScanner.scan(
                            ip = gateway,
                            ports = WellKnownPorts.QUICK_20,
                            grabBanners = true,
                            onProgress = { p ->
                                viewModelScope.launch(Dispatchers.Main.immediate) { portScanProgress = p }
                            }
                        )
                        withContext(Dispatchers.Main) { openPorts = scanResults }
                    }
                } catch (e: Exception) { Log.e("SecurityAudit", "Port scan error", e) }

                auditPhase = context.getString(R.string.audit_phase_report)
                report = try {
                    SecurityAuditor.audit(
                        wifiNetworks = wifiNetworks,
                        btDevices = btDevices,
                        openPorts = openPorts,
                        connectedSsid = wifiScanner.getConnectedSsid()
                    )
                } catch (e: Exception) { Log.e("SecurityAudit", "Report error", e); null }
            } catch (e: Exception) {
                Log.e("SecurityAudit", "Audit error", e)
            } finally {
                isAuditing = false
                auditPhase = ""
                portScanProgress = null
            }
        }
    }

    override fun onCleared() {
        super.onCleared()
        wifiScanner.cleanup()
        btScanner.cleanup()
    }
}
