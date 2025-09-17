package io.horizontalsystems.tonkit.sample

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch

class QRCodeScannerViewModel : ViewModel() {
    private val _scannedUrl = MutableStateFlow<String?>(null)
    val scannedUrl: StateFlow<String?> = _scannedUrl.asStateFlow()
    
    private val _isScanning = MutableStateFlow(true)
    val isScanning: StateFlow<Boolean> = _isScanning.asStateFlow()
    
    private val _error = MutableStateFlow<String?>(null)
    val error: StateFlow<String?> = _error.asStateFlow()
    
    fun onQRCodeDetected(qrCode: String) {
        viewModelScope.launch {
            _scannedUrl.value = qrCode
            _isScanning.value = false
            _error.value = null // Clear any previous errors
        }
    }
    
    fun clearScannedUrl() {
        viewModelScope.launch {
            _scannedUrl.value = null
            _isScanning.value = true
            _error.value = null
        }
    }
    
    fun setError(errorMessage: String) {
        viewModelScope.launch {
            _error.value = errorMessage
        }
    }
}
