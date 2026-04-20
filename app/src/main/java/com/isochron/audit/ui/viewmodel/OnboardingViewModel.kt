package com.scanner.app.ui.viewmodel

import android.app.Application
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.lifecycle.AndroidViewModel

class OnboardingViewModel(app: Application) : AndroidViewModel(app) {
    var step by mutableStateOf(0)
}
