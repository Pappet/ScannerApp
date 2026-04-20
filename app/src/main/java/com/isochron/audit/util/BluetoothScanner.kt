package com.scanner.app.util

import android.annotation.SuppressLint
import android.bluetooth.BluetoothAdapter
import android.bluetooth.BluetoothManager
import android.bluetooth.le.BluetoothLeScanner
import android.bluetooth.le.ScanCallback
import android.bluetooth.le.ScanResult
import android.bluetooth.le.ScanSettings
import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.content.IntentFilter
import android.os.Build
import android.os.Handler
import android.os.Looper
import android.util.Log
import com.scanner.app.data.BluetoothDevice
import com.scanner.app.data.BondState
import com.scanner.app.data.DeviceType

/**
 * Utility class for scanning Bluetooth devices (both Classic and Low Energy).
 * Consolidates results from [BluetoothAdapter.startDiscovery] and [BluetoothLeScanner.startScan].
 *
 * @property context The application context used for OS service access and receiver registration.
 */
@SuppressLint("MissingPermission")
class BluetoothScanner(private val context: Context) {

    companion object {
        private const val TAG = "BluetoothScanner"
    }

    private val bluetoothManager: BluetoothManager? =
        try {
            context.getSystemService(Context.BLUETOOTH_SERVICE) as? BluetoothManager
        } catch (e: Exception) {
            Log.e(TAG, "Failed to get BluetoothManager", e)
            null
        }

    private val bluetoothAdapter: BluetoothAdapter? = bluetoothManager?.adapter

    private val discoveredDevices = mutableMapOf<String, BluetoothDevice>()
    private var classicReceiver: BroadcastReceiver? = null
    private var bleCallback: ScanCallback? = null
    private val handler = Handler(Looper.getMainLooper())
    private var stopRunnable: Runnable? = null

    /**
     * Starts a combined Classic and BLE scan for a specified duration.
     * Bonded devices are added immediately.
     *
     * @param durationMs Length of the scan in milliseconds.
     * @param onProgress Callback invoked periodically with updated device list during the scan.
     * @param onComplete Callback invoked when the scan duration expires, returning the final sorted list.
     */
    fun startScan(
        durationMs: Long = 12_000L,
        onProgress: (List<BluetoothDevice>) -> Unit,
        onComplete: (List<BluetoothDevice>) -> Unit
    ) {
        discoveredDevices.clear()

        if (bluetoothAdapter == null || !isBluetoothEnabled()) {
            Log.w(TAG, "Bluetooth not available or disabled")
            onComplete(emptyList())
            return
        }

        // Add bonded (paired) devices first
        try {
            addBondedDevices()
            onProgress(discoveredDevices.values.toList())
        } catch (e: Exception) {
            Log.e(TAG, "Error reading bonded devices", e)
        }

        // Start Classic Bluetooth discovery
        try {
            startClassicDiscovery(onProgress)
        } catch (e: Exception) {
            Log.e(TAG, "Error starting classic discovery", e)
        }

        // Start BLE scan
        try {
            startBleScan(onProgress)
        } catch (e: Exception) {
            Log.e(TAG, "Error starting BLE scan", e)
        }

        // Stop after duration
        stopRunnable = Runnable {
            stopScan()
            onComplete(getSortedDevices())
        }
        handler.postDelayed(stopRunnable!!, durationMs)
    }

    /**
     * Forcefully stops any ongoing Classic discovery or BLE scan.
     * Unregisters internal receivers and clears callbacks.
     */
    fun stopScan() {
        stopRunnable?.let { handler.removeCallbacks(it) }
        stopRunnable = null

        // Stop Classic discovery
        try {
            bluetoothAdapter?.cancelDiscovery()
        } catch (e: Exception) {
            Log.w(TAG, "Error canceling discovery", e)
        }

        classicReceiver?.let { receiver ->
            try {
                context.unregisterReceiver(receiver)
            } catch (_: IllegalArgumentException) {
                // Already unregistered
            } catch (e: Exception) {
                Log.w(TAG, "Error unregistering classic receiver", e)
            }
        }
        classicReceiver = null

        // Stop BLE scan
        bleCallback?.let { callback ->
            try {
                val leScanner = bluetoothAdapter?.bluetoothLeScanner
                leScanner?.stopScan(callback)
            } catch (e: Exception) {
                Log.w(TAG, "Error stopping BLE scan", e)
            }
        }
        bleCallback = null
    }

    /**
     * Checks if the Bluetooth adapter is currently enabled.
     */
    fun isBluetoothEnabled(): Boolean {
        return try {
            bluetoothAdapter?.isEnabled == true
        } catch (e: Exception) {
            false
        }
    }

    /**
     * Checks if the device has a Bluetooth adapter available.
     */
    fun hasBluetoothSupport(): Boolean = bluetoothAdapter != null

    /**
     * Retrieves a list of currently bonded (paired) devices.
     */
    fun getBondedDevices(): List<BluetoothDevice> {
        return try {
            bluetoothAdapter?.bondedDevices?.mapNotNull { device ->
                try {
                    val address = device.address ?: return@mapNotNull null
                    BluetoothDevice(
                        name = device.getAliasOrName(TAG) ?: "(Unbekannt)",
                        address = address,
                        rssi = null,
                        type = mapDeviceType(device.type),
                        bondState = BondState.BONDED,
                        isConnected = isDeviceConnected(device),
                        deviceClass = getDeviceClassName(device.bluetoothClass?.majorDeviceClass),
                        vendor = MacVendorLookup.shortName(address),
                        minorClass = getMinorClassName(device.bluetoothClass?.deviceClass)
                    )
                } catch (e: Exception) {
                    Log.w(TAG, "Error reading bonded device", e)
                    null
                }
            } ?: emptyList()
        } catch (e: SecurityException) {
            Log.e(TAG, "Permission denied reading bonded devices", e)
            emptyList()
        } catch (e: Exception) {
            Log.e(TAG, "Error reading bonded devices", e)
            emptyList()
        }
    }

    /**
     * Stops any active scans and clears the main thread handler.
     */
    fun cleanup() {
        stopScan()
        handler.removeCallbacksAndMessages(null)
    }



    private fun addBondedDevices() {
        getBondedDevices().forEach { device ->
            discoveredDevices[device.address] = device
        }
    }

    /**
     * Upsert a device into discoveredDevices — merges info from multiple sources.
     */
    private fun upsertDevice(
        address: String,
        name: String?,
        rssi: Int?,
        type: DeviceType,
        bondState: BondState,
        isConnected: Boolean,
        majorClass: Int?,
        minorClass: Int?,
        serviceUuids: List<String> = emptyList(),
        txPower: Int? = null
    ) {
        val existing = discoveredDevices[address]
        val vendor = MacVendorLookup.shortName(address)

        val bestName = when {
            name != null && name.isNotBlank() -> name
            existing?.name != null && existing.name != "(Unbekannt)" -> existing.name
            else -> "(Unbekannt)"
        }

        discoveredDevices[address] = BluetoothDevice(
            name = bestName,
            address = address,
            rssi = rssi ?: existing?.rssi,
            type = if (type != DeviceType.UNKNOWN) type else existing?.type ?: DeviceType.UNKNOWN,
            bondState = if (bondState == BondState.BONDED) bondState else existing?.bondState ?: bondState,
            isConnected = isConnected || existing?.isConnected == true,
            deviceClass = getDeviceClassName(majorClass) ?: existing?.deviceClass,
            vendor = vendor ?: existing?.vendor,
            minorClass = getMinorClassName(minorClass) ?: existing?.minorClass,
            serviceUuids = serviceUuids.ifEmpty { existing?.serviceUuids ?: emptyList() },
            txPower = txPower ?: existing?.txPower
        )
    }

    private fun startClassicDiscovery(onProgress: (List<BluetoothDevice>) -> Unit) {
        classicReceiver = object : BroadcastReceiver() {
            override fun onReceive(ctx: Context?, intent: Intent?) {
                try {
                    when (intent?.action) {
                        android.bluetooth.BluetoothDevice.ACTION_FOUND -> {
                            @Suppress("DEPRECATION")
                            val device: android.bluetooth.BluetoothDevice =
                                intent.getParcelableExtra(android.bluetooth.BluetoothDevice.EXTRA_DEVICE)
                                    ?: return

                            val address = device.address ?: return
                            val rssi = intent.getShortExtra(
                                android.bluetooth.BluetoothDevice.EXTRA_RSSI, Short.MIN_VALUE
                            ).toInt()

                            upsertDevice(
                                address = address,
                                name = device.getAliasOrName(TAG),
                                rssi = rssi.takeIf { it != Short.MIN_VALUE.toInt() },
                                type = try { mapDeviceType(device.type) } catch (_: Exception) { DeviceType.UNKNOWN },
                                bondState = try { mapBondState(device.bondState) } catch (_: Exception) { BondState.NOT_BONDED },
                                isConnected = try { isDeviceConnected(device) } catch (_: Exception) { false },
                                majorClass = try { device.bluetoothClass?.majorDeviceClass } catch (_: Exception) { null },
                                minorClass = try { device.bluetoothClass?.deviceClass } catch (_: Exception) { null }
                            )
                            onProgress(discoveredDevices.values.toList())
                        }

                        android.bluetooth.BluetoothDevice.ACTION_NAME_CHANGED -> {
                            @Suppress("DEPRECATION")
                            val device: android.bluetooth.BluetoothDevice =
                                intent.getParcelableExtra(android.bluetooth.BluetoothDevice.EXTRA_DEVICE)
                                    ?: return

                            val address = device.address ?: return
                            val newName = device.getAliasOrName(TAG)
                            if (newName != null) {
                                val existing = discoveredDevices[address] ?: return
                                discoveredDevices[address] = existing.copy(name = newName)
                                onProgress(discoveredDevices.values.toList())
                            }
                        }
                    }
                } catch (e: SecurityException) {
                    Log.e(TAG, "Permission denied in classic discovery", e)
                } catch (e: Exception) {
                    Log.e(TAG, "Error processing discovered device", e)
                }
            }
        }

        val filter = IntentFilter().apply {
            addAction(android.bluetooth.BluetoothDevice.ACTION_FOUND)
            addAction(android.bluetooth.BluetoothDevice.ACTION_NAME_CHANGED)
            addAction(BluetoothAdapter.ACTION_DISCOVERY_FINISHED)
        }

        try {
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
                context.registerReceiver(classicReceiver, filter, Context.RECEIVER_NOT_EXPORTED)
            } else {
                context.registerReceiver(classicReceiver, filter)
            }
            bluetoothAdapter?.startDiscovery()
        } catch (e: SecurityException) {
            Log.e(TAG, "Permission denied starting classic discovery", e)
        } catch (e: Exception) {
            Log.e(TAG, "Error starting classic discovery", e)
        }
    }

    private fun startBleScan(onProgress: (List<BluetoothDevice>) -> Unit) {
        val leScanner: BluetoothLeScanner? = try {
            bluetoothAdapter?.bluetoothLeScanner
        } catch (e: Exception) {
            Log.e(TAG, "Error getting BLE scanner", e)
            null
        }

        if (leScanner == null) {
            Log.w(TAG, "BLE scanner not available")
            return
        }

        bleCallback = object : ScanCallback() {
            override fun onScanResult(callbackType: Int, result: ScanResult) {
                try {
                    val device = result.device ?: return
                    val address = device.address ?: return
                    val scanRecord = result.scanRecord

                    // Extract service UUIDs from scan record
                    val serviceUuids = try {
                        scanRecord?.serviceUuids?.map { it.toString() } ?: emptyList()
                    } catch (_: Exception) { emptyList() }

                    // Extract TX Power
                    val txPower = try {
                        scanRecord?.txPowerLevel?.takeIf { it != Int.MIN_VALUE }
                    } catch (_: Exception) { null }

                    val resolvedName = try { scanRecord?.deviceName } catch (_: Exception) { null }
                        ?: device.getAliasOrName(TAG)

                    upsertDevice(
                        address = address,
                        name = resolvedName,
                        rssi = result.rssi,
                        type = DeviceType.BLE,
                        bondState = try { mapBondState(device.bondState) } catch (_: Exception) { BondState.NOT_BONDED },
                        isConnected = false,
                        majorClass = null,
                        minorClass = null,
                        serviceUuids = serviceUuids,
                        txPower = txPower
                    )
                    onProgress(discoveredDevices.values.toList())
                } catch (e: SecurityException) {
                    Log.e(TAG, "Permission denied in BLE callback", e)
                } catch (e: Exception) {
                    Log.e(TAG, "Error processing BLE result", e)
                }
            }

            override fun onScanFailed(errorCode: Int) {
                Log.e(TAG, "BLE scan failed with error code: $errorCode")
            }
        }

        try {
            val settings = ScanSettings.Builder()
                .setScanMode(ScanSettings.SCAN_MODE_LOW_LATENCY)
                .build()
            leScanner.startScan(null, settings, bleCallback)
        } catch (e: SecurityException) {
            Log.e(TAG, "Permission denied starting BLE scan", e)
            bleCallback = null
        } catch (e: Exception) {
            Log.e(TAG, "Error starting BLE scan", e)
            bleCallback = null
        }
    }

    private fun getSortedDevices(): List<BluetoothDevice> {
        return discoveredDevices.values.toList().sortedWith(
            compareByDescending<BluetoothDevice> { it.isConnected }
                .thenByDescending { it.bondState == BondState.BONDED }
                .thenByDescending { it.rssi ?: Int.MIN_VALUE }
        )
    }

    private fun isDeviceConnected(device: android.bluetooth.BluetoothDevice): Boolean {
        return try {
            val method = device.javaClass.getMethod("isConnected")
            method.invoke(device) as? Boolean ?: false
        } catch (_: Exception) {
            false
        }
    }

    private fun mapDeviceType(type: Int): DeviceType = when (type) {
        android.bluetooth.BluetoothDevice.DEVICE_TYPE_CLASSIC -> DeviceType.CLASSIC
        android.bluetooth.BluetoothDevice.DEVICE_TYPE_LE -> DeviceType.BLE
        android.bluetooth.BluetoothDevice.DEVICE_TYPE_DUAL -> DeviceType.DUAL
        else -> DeviceType.UNKNOWN
    }

    private fun mapBondState(state: Int): BondState = when (state) {
        android.bluetooth.BluetoothDevice.BOND_BONDED -> BondState.BONDED
        android.bluetooth.BluetoothDevice.BOND_BONDING -> BondState.BONDING
        else -> BondState.NOT_BONDED
    }

    private fun getDeviceClassName(majorClass: Int?): String? = when (majorClass) {
        0x0100 -> "Computer"
        0x0200 -> "Telefon"
        0x0300 -> "Netzwerk"
        0x0400 -> "Audio/Video"
        0x0500 -> "Peripherie"
        0x0600 -> "Bildgebung"
        0x0700 -> "Wearable"
        0x0800 -> "Spielzeug"
        0x0900 -> "Gesundheit"
        else -> null
    }

    @SuppressLint("NewApi")
    private fun android.bluetooth.BluetoothDevice.getAliasOrName(tag: String): String? {
        return try {
            var n: String? = null
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.R) {
                n = this.alias
            }
            if (n.isNullOrBlank()) {
                n = this.name
            }
            n
        } catch (e: SecurityException) {
            Log.e(tag, "SecurityException: Permission denied reading device name/alias", e)
            null
        } catch (e: Exception) {
            Log.w(tag, "Error reading device name/alias", e)
            null
        }
    }

    /**
     * Resolve the full device class (major + minor) to a specific device type name.
     * See https://www.bluetooth.com/specifications/assigned-numbers/baseband/
     */
    private fun getMinorClassName(deviceClass: Int?): String? {
        if (deviceClass == null) return null
        return when (deviceClass) {
            // Computer
            0x0104 -> "Desktop"
            0x0108 -> "Server"
            0x010C -> "Laptop"
            0x0110 -> "Handheld"
            0x0114 -> "Palm"
            0x0118 -> "Wearable Computer"
            0x011C -> "Tablet"
            // Phone
            0x0204 -> "Mobiltelefon"
            0x0208 -> "Schnurlostelefon"
            0x020C -> "Smartphone"
            0x0210 -> "Modem/Gateway"
            0x0214 -> "ISDN"
            // Audio/Video
            0x0404 -> "Headset"
            0x0408 -> "Freisprecher"
            0x0410 -> "Mikrofon"
            0x0414 -> "Lautsprecher"
            0x0418 -> "Kopfhörer"
            0x041C -> "Portable Audio"
            0x0420 -> "Auto-Audio"
            0x0424 -> "Set-Top-Box"
            0x0428 -> "HiFi-Audio"
            0x042C -> "Videorekorder"
            0x0430 -> "Videokamera"
            0x0434 -> "Camcorder"
            0x0438 -> "Video-Monitor"
            0x043C -> "Video-Display & Lautsprecher"
            0x0444 -> "Gaming/Spielzeug"
            // Peripherals
            0x0502 -> "Gamepad"
            0x0504 -> "Joystick"
            0x0508 -> "Gamepad"
            0x0540 -> "Tastatur"
            0x0580 -> "Maus"
            0x05C0 -> "Tastatur & Maus"
            // Imaging
            0x0604 -> "Display"
            0x0608 -> "Kamera"
            0x0610 -> "Scanner"
            0x0620 -> "Drucker"
            // Wearable
            0x0704 -> "Armbanduhr"
            0x0708 -> "Pager"
            0x070C -> "Jacke"
            0x0710 -> "Helm"
            0x0714 -> "Brille"
            // Health
            0x0904 -> "Blutdruckmessgerät"
            0x0908 -> "Thermometer"
            0x090C -> "Waage"
            0x0910 -> "Glukosemessgerät"
            0x0914 -> "Pulsoximeter"
            0x0918 -> "Herzfrequenzmesser"
            0x091C -> "Gesundheitsdaten-Display"
            0x0920 -> "Schrittzähler"
            0x0924 -> "Medikamentenmonitor"
            0x0928 -> "Knie-Prothese"
            0x092C -> "Knöchel-Prothese"
            0x0938 -> "Gesundheitsmanager"
            else -> null
        }
    }
}
