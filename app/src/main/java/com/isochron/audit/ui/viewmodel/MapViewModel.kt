package com.scanner.app.ui.viewmodel

import android.app.Application
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.lifecycle.AndroidViewModel
import com.scanner.app.data.repository.DeviceRepository

class MapViewModel(app: Application) : AndroidViewModel(app) {
    val repository = DeviceRepository(app)
    var selectedBssid by mutableStateOf<String?>(null)
}
