package com.scanner.app.service

import android.annotation.SuppressLint
import android.app.Notification
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.app.Service
import android.content.Context
import android.content.Intent
import android.os.Binder
import android.os.IBinder
import androidx.core.app.NotificationCompat
import com.scanner.app.MainActivity
import com.scanner.app.util.PingUtil
import kotlinx.coroutines.*
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import java.time.Instant

/**
 * Immutable state representation for the background monitoring process.
 */
data class MonitoringState(
    val isRunning: Boolean = false,
    val intervalSeconds: Int = 10,
    val wifiSignalHistory: List<Pair<Int, Instant>> = emptyList(),    // dBm, time
    val gatewayLatency: List<Pair<Float, Instant>> = emptyList(),     // ms, time
    val internetLatency: List<Pair<Float, Instant>> = emptyList(),    // ms, time
    val gatewayReachable: Boolean = true,
    val internetReachable: Boolean = true,
    val currentSsid: String? = null,
    val currentSignal: Int? = null,
    val scanCount: Int = 0,
    val newDeviceCount: Int = 0
)

/**
 * Foreground service that performs periodic network monitoring.
 * Tracks WiFi signal strength, gateway latency, and internet reachability.
 * Exposes real-time status through a [StateFlow] and updates a persistent notification.
 */
class ScanService : Service() {

    companion object {
        const val CHANNEL_ID = "scan_monitor_channel"
        const val NOTIFICATION_ID = 1
        const val MAX_HISTORY_POINTS = 120  // ~20 min at 10s interval

        const val ACTION_START = "com.scanner.app.START_MONITORING"
        const val ACTION_STOP = "com.scanner.app.STOP_MONITORING"
        const val EXTRA_INTERVAL = "interval_seconds"
    }

    private val binder = LocalBinder()
    private val scope = CoroutineScope(Dispatchers.Default + SupervisorJob())

    private lateinit var pingUtil: PingUtil

    private var monitorJob: Job? = null

    private val _state = MutableStateFlow(MonitoringState())
    val state: StateFlow<MonitoringState> = _state.asStateFlow()

    inner class LocalBinder : Binder() {
        fun getService(): ScanService = this@ScanService
    }

    override fun onBind(intent: Intent?): IBinder = binder

    override fun onCreate() {
        super.onCreate()
        pingUtil = PingUtil(this)
        createNotificationChannel()
    }

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        android.util.Log.d("ScanService", "onStartCommand: action=${intent?.action}")
        when (intent?.action) {
            ACTION_START -> {
                val interval = intent.getIntExtra(EXTRA_INTERVAL, 10)
                startMonitoring(interval)
            }
            ACTION_STOP -> stopMonitoring()
        }
        return START_STICKY
    }

    override fun onDestroy() {
        stopMonitoring()
        scope.cancel()
        super.onDestroy()
    }



    /**
     * Starts the monitoring cycle as a foreground service.
     * @param intervalSeconds The delay between monitoring cycles.
     */
    @SuppressLint("ForegroundServiceType")
    fun startMonitoring(intervalSeconds: Int = 10) {
        if (monitorJob?.isActive == true) return

        startForeground(NOTIFICATION_ID, buildNotification("Monitoring aktiv..."))

        _state.value = _state.value.copy(
            isRunning = true,
            intervalSeconds = intervalSeconds,
            scanCount = 0
        )

        monitorJob = scope.launch {
            while (isActive) {
                performMonitoringCycle()
                delay(intervalSeconds * 1000L)
            }
        }
    }

    /**
     * Stops the monitoring cycle and removes the foreground notification.
     */
    fun stopMonitoring() {
        android.util.Log.d("ScanService", "stopMonitoring called")
        monitorJob?.cancel()
        monitorJob = null
        _state.value = _state.value.copy(isRunning = false)
        stopForeground(STOP_FOREGROUND_REMOVE)
        stopSelf()
    }

    /**
     * Updates the frequency of the monitoring cycles.
     */
    fun updateInterval(seconds: Int) {
        val wasRunning = _state.value.isRunning
        if (wasRunning) {
            monitorJob?.cancel()
            _state.value = _state.value.copy(intervalSeconds = seconds)
            monitorJob = scope.launch {
                while (isActive) {
                    performMonitoringCycle()
                    delay(seconds * 1000L)
                }
            }
        } else {
            _state.value = _state.value.copy(intervalSeconds = seconds)
        }
    }



    private suspend fun performMonitoringCycle() {
        try {
            val now = Instant.now()
            val currentState = _state.value

            // 1. WiFi signal check
            val networkInfo = pingUtil.getNetworkInfo()
            val wifiSignal = networkInfo.signalStrength

            val newWifiHistory = (currentState.wifiSignalHistory +
                    (wifiSignal?.let { listOf(Pair(it, now)) } ?: emptyList()))
                .takeLast(MAX_HISTORY_POINTS)

            // 2. Gateway ping
            val gatewayResult = networkInfo.gatewayIp?.let { gateway ->
                try {
                    pingUtil.ping(gateway, count = 1, timeoutSec = 3)
                } catch (e: Exception) { null }
            }

            val newGatewayLatency = (currentState.gatewayLatency +
                    (gatewayResult?.latencyMs?.let { listOf(Pair(it, now)) } ?: emptyList()))
                .takeLast(MAX_HISTORY_POINTS)

            // 3. Internet ping
            val internetResult = try {
                pingUtil.ping("8.8.8.8", count = 1, timeoutSec = 3)
            } catch (e: Exception) { null }

            val newInternetLatency = (currentState.internetLatency +
                    (internetResult?.latencyMs?.let { listOf(Pair(it, now)) } ?: emptyList()))
                .takeLast(MAX_HISTORY_POINTS)

            // Update state
            _state.value = currentState.copy(
                wifiSignalHistory = newWifiHistory,
                gatewayLatency = newGatewayLatency,
                internetLatency = newInternetLatency,
                gatewayReachable = gatewayResult?.isReachable ?: false,
                internetReachable = internetResult?.isReachable ?: false,
                currentSsid = networkInfo.ssid,
                currentSignal = wifiSignal,
                scanCount = currentState.scanCount + 1
            )

            // Update notification
            val notifText = buildString {
                append(networkInfo.ssid ?: "Kein WLAN")
                wifiSignal?.let { append(" · $it dBm") }
                gatewayResult?.latencyMs?.let { append(" · ${"%.0f".format(it)}ms") }
            }
            updateNotification(notifText)
        } catch (e: Exception) {
            android.util.Log.e("ScanService", "Error in monitoring cycle", e)
        }
    }



    private fun createNotificationChannel() {
        val channel = NotificationChannel(
            CHANNEL_ID,
            "Netzwerk-Monitoring",
            NotificationManager.IMPORTANCE_LOW
        ).apply {
            description = "Zeigt den Status des Netzwerk-Monitorings"
            setShowBadge(false)
        }
        val manager = getSystemService(NotificationManager::class.java)
        manager.createNotificationChannel(channel)
    }

    private fun buildNotification(text: String): Notification {
        val pendingIntent = PendingIntent.getActivity(
            this, 0,
            Intent(this, MainActivity::class.java),
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        )

        val stopIntent = PendingIntent.getService(
            this, 1,
            Intent(this, ScanService::class.java).apply { action = ACTION_STOP },
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        )

        return NotificationCompat.Builder(this, CHANNEL_ID)
            .setContentTitle("Netzwerk-Scanner")
            .setContentText(text)
            .setSmallIcon(android.R.drawable.ic_menu_manage)
            .setContentIntent(pendingIntent)
            .addAction(android.R.drawable.ic_media_pause, "Stopp", stopIntent)
            .setOngoing(true)
            .setSilent(true)
            .build()
    }

    private fun updateNotification(text: String) {
        val manager = getSystemService(NotificationManager::class.java)
        manager.notify(NOTIFICATION_ID, buildNotification(text))
    }
}
