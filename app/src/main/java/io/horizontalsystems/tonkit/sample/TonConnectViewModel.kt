package io.horizontalsystems.tonkit.sample

import android.util.Log
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.tonapps.wallet.data.core.entity.SendRequestEntity
import com.tonapps.wallet.data.tonconnect.entities.DAppEntity
import io.horizontalsystems.tonkit.models.Event
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch

class TonConnectViewModel : ViewModel() {
    private var pendingSendRequest: Pair<SendRequestEntity, Event>? = null
    private var dApps: List<DAppEntity> = listOf()
    private var close = false
    private val handledRequests = mutableListOf<Int>()

    var uiState by mutableStateOf(
        UiState(
            dApps = dApps,
            close = close,
            pendingSendRequest = pendingSendRequest,
        )
    )
        private set

    private val tonConnectKit = App.tonConnectKit
    private val tonKit = App.tonKit

    init {
        viewModelScope.launch(Dispatchers.Default) {
            tonConnectKit.getDApps().collect {
                dApps = it
                emitState()
            }
        }

        viewModelScope.launch(Dispatchers.Default) {
            tonConnectKit.sendRequestFlow.collect {
                Log.e("AAA", "first send request: ${it.id}")
                it.let { it1 -> handleSendRequest(it1) }
            }
        }

        tonConnectKit.start()
    }

    private suspend fun handleSendRequest(request: SendRequestEntity) {
        if (handledRequests.contains(request.id)) return

        val event = tonKit.getDetails(request, App.walletType)

        pendingSendRequest = Pair(request, event)
        Log.e("AAA", "handleSendRequest")
        emitState()
    }

    override fun onCleared() {
        tonConnectKit.stop()
    }

    private fun emitState() {
        viewModelScope.launch {
            uiState = UiState(
                dApps = dApps,
                close = close,
                pendingSendRequest = pendingSendRequest
            )
        }
    }

    fun approve() {
        viewModelScope.launch(Dispatchers.Default) {
            val (request, _) = pendingSendRequest!!

            val boc = tonKit.sign(request, App.walletType)

            tonKit.send(boc)

            tonConnectKit.approve(request, boc)
            pendingSendRequest = null

            close = true
            emitState()
        }
    }

    fun reject() {
        viewModelScope.launch(Dispatchers.Default) {
            val (request, _) = pendingSendRequest!!

            tonConnectKit.reject(request)
            pendingSendRequest = null

            close = true
            emitState()
        }
    }

    fun onClose() {
        close = false
        emitState()
    }

    fun onRequestHandled(id: Int) {
        handledRequests.add(id)
    }

    data class UiState(
        val dApps: List<DAppEntity>,
        val close: Boolean,
        val pendingSendRequest: Pair<SendRequestEntity, Event>?
    )
}
