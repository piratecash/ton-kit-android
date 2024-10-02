package io.horizontalsystems.tonkit.sample

import android.util.Log
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.tonapps.wallet.data.tonconnect.entities.DAppRequestEntity
import io.horizontalsystems.tonkit.tonconnect.TonConnectKit
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch

class TonConnectViewModel : ViewModel() {
    private var dAppRequestEntity: DAppRequestEntity? = null

    //    private var url: String? = null
    private var url: String? =
        "tc://?v=2&id=5e15bbe3bc218c3d7c152fe8cbd90896d8870efb8fd2879e7ce19b7e40b5aa22&r=%7B%22manifestUrl%22%3A%22https%3A%2F%2Fapp.hipo.finance%2Ftonconnect-manifest.json%22%2C%22items%22%3A%5B%7B%22name%22%3A%22ton_addr%22%7D%5D%7D&ret=none"

    var uiState by mutableStateOf(
        TonConnectUiState(
            dAppRequestEntity = dAppRequestEntity
        )
    )
        private set

    private val tonConnectKit = TonConnectKit()

    fun setUrl(v: String) {
        url = v
    }

    fun readData() {
        val url = url

        if (url != null) {
            dAppRequestEntity = tonConnectKit.readData(url)
            emitState()
        }
    }

    fun connect() {
        viewModelScope.launch(Dispatchers.Default) {
            val dAppRequestEntity = dAppRequestEntity

            if (dAppRequestEntity != null) {
                val connect = tonConnectKit.connect(dAppRequestEntity, App.walletType, false)
                Log.e("AAA", "connect: $connect")
            }
        }
    }

    private fun emitState() {
        viewModelScope.launch {
            uiState = TonConnectUiState(dAppRequestEntity)
        }
    }

}

data class TonConnectUiState(val dAppRequestEntity: DAppRequestEntity?)
