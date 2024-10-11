package io.horizontalsystems.tonkit.sample

import android.util.Log
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.tonapps.wallet.data.tonconnect.entities.DAppRequestEntity
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch

class TonConnectNewConnectionViewModel : ViewModel() {
    private var connected = false
    private var dAppRequestEntity: DAppRequestEntity? = null
    private var error: Throwable? = null

    var uiState by mutableStateOf(
        TonConnectNewConnectionUiState(
            dAppRequestEntity = dAppRequestEntity,
            error = error,
            connected = connected,
        )
    )
        private set

    private val tonConnectKit = App.tonConnectKit

    fun resolveUrl(url: String) {
//        val url =
//            "tc://?v=2&id=5e15bbe3bc218c3d7c152fe8cbd90896d8870efb8fd2879e7ce19b7e40b5aa22&r=%7B%22manifestUrl%22%3A%22https%3A%2F%2Fapp.hipo.finance%2Ftonconnect-manifest.json%22%2C%22items%22%3A%5B%7B%22name%22%3A%22ton_addr%22%7D%5D%7D&ret=none"

        try {
            dAppRequestEntity = tonConnectKit.readData(url)
        } catch (e: Throwable) {
            error = e
        }
        emitState()
    }

    fun connect() {
        viewModelScope.launch(Dispatchers.Default) {
            error = null
            emitState()

            try {
                val dAppRequestEntity = dAppRequestEntity

                if (dAppRequestEntity != null) {
                    val manifest = tonConnectKit.getManifest(dAppRequestEntity.payload.manifestUrl)
                    val connect = tonConnectKit.connect(
                        dAppRequestEntity = dAppRequestEntity,
                        manifest = manifest,
                        walletId = "walletId",
                        walletType = App.walletType,
                        testnet = false,
                    )
                    Log.e("AAA", "connect: $connect")
                }
                connected = true
            } catch (e: Throwable) {
                error = e
            }
            emitState()
        }
    }

    private fun emitState() {
        viewModelScope.launch {
            uiState = TonConnectNewConnectionUiState(dAppRequestEntity, error, connected)
        }
    }
}

data class TonConnectNewConnectionUiState(
    val dAppRequestEntity: DAppRequestEntity?,
    val error: Throwable?,
    val connected: Boolean,
)
