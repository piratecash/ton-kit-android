package io.horizontalsystems.tonkit.sample

import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.tonapps.wallet.data.tonconnect.entities.DAppEntity
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch

class TonConnectViewModel : ViewModel() {
    private var dApps: List<DAppEntity> = listOf()

    var uiState by mutableStateOf(
        UiState(
            dApps = dApps
        )
    )
        private set

    private val tonConnectKit = App.tonConnectKit

    init {
        viewModelScope.launch(Dispatchers.Default) {
            tonConnectKit.getDApps().collect {
                dApps = it
                emitState()
            }
        }

        tonConnectKit.start()
    }

    override fun onCleared() {
        tonConnectKit.stop()
    }

    private fun emitState() {
        viewModelScope.launch {
            uiState = UiState(
                dApps = dApps
            )
        }
    }

    data class UiState(val dApps: List<DAppEntity>)
}
