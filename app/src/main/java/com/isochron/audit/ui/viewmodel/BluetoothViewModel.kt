package com.scanner.app.ui.viewmodel

import android.app.Application
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import com.scanner.app.data.BluetoothDevice
import com.scanner.app.data.repository.DeviceRepository
import com.scanner.app.util.BluetoothScanner
import com.scanner.app.util.GattCharacteristicInfo
import com.scanner.app.util.GattExplorer
import kotlinx.coroutines.launch

class BluetoothViewModel(app: Application) : AndroidViewModel(app) {

    val btScanner = BluetoothScanner(app)
    val gattExplorer = GattExplorer(app)
    val repository = DeviceRepository(app)

    var openSvc by mutableStateOf<String?>(null)
    var selChar by mutableStateOf<GattCharacteristicInfo?>(null)

    var devices by mutableStateOf<List<BluetoothDevice>>(emptyList())
        private set
    var isScanning by mutableStateOf(false)
        private set
    var hasScanned by mutableStateOf(false)
        private set
    var selectedAddress by mutableStateOf<String?>(null)
    var gattAddress by mutableStateOf<String?>(null)

    fun isBluetoothEnabled(): Boolean = btScanner.isBluetoothEnabled()

    fun scan() {
        if (!btScanner.isBluetoothEnabled()) return
        isScanning = true
        val startTime = System.currentTimeMillis()
        btScanner.startScan(
            onProgress = { results -> devices = results },
            onComplete = { results ->
                devices = results
                isScanning = false
                hasScanned = true
                viewModelScope.launch {
                    try {
                        repository.persistBluetoothScan(
                            devices = results,
                            durationMs = System.currentTimeMillis() - startTime,
                        )
                    } catch (e: Exception) {
                        android.util.Log.e("BluetoothViewModel", "Error persisting scan", e)
                    }
                }
            },
        )
    }

    fun openGatt(address: String) {
        gattAddress = address
        gattExplorer.connect(address)
    }

    fun closeGatt() {
        gattExplorer.disconnect()
        gattAddress = null
    }

    fun toggleFavorite(address: String) {
        viewModelScope.launch { repository.toggleFavoriteByAddress(address) }
    }

    suspend fun persistGattData(address: String, json: String) {
        repository.persistGattData(address, json)
    }

    override fun onCleared() {
        super.onCleared()
        btScanner.cleanup()
        gattExplorer.disconnect()
    }
}
