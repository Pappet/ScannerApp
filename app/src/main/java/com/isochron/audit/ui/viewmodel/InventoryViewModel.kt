package com.scanner.app.ui.viewmodel

import android.app.Application
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.lifecycle.AndroidViewModel
import com.scanner.app.data.db.DiscoveredDeviceEntity
import com.scanner.app.data.repository.DeviceRepository

class InventoryViewModel(app: Application) : AndroidViewModel(app) {
    val repository = DeviceRepository(app)

    var kind by mutableStateOf("all")
    var favOnly by mutableStateOf(false)
    var searchQuery by mutableStateOf("")

    var editDialogDevice by mutableStateOf<DiscoveredDeviceEntity?>(null)
    var showExportDialog by mutableStateOf(false)
}
