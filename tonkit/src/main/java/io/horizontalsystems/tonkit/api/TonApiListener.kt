package io.horizontalsystems.tonkit.api

import co.touchlab.kermit.Logger
import com.tonapps.network.SSEvent
import com.tonapps.network.sse
import io.horizontalsystems.tonkit.Address
import io.horizontalsystems.tonkit.models.Network
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.asSharedFlow
import kotlinx.coroutines.flow.retry
import kotlinx.coroutines.launch
import okhttp3.OkHttpClient

class TonApiListener(
    val network: Network,
    okHttpClient: OkHttpClient,
    private val logger: Logger,
) : IApiListener {
    enum class State {
        Connecting,
        Connected,
        Disconnected
    }

    private var state = State.Disconnected
    private var address: Address? = null
    private var streamingAPI: StreamingAPI

    private val _transactionFlow = MutableSharedFlow<String>()
    override val transactionFlow: Flow<String>
        get() = _transactionFlow.asSharedFlow()

    private val coroutineScope = CoroutineScope(Dispatchers.Default)
    private var job: Job? = null

    init {
        val serverUrl = when (network) {
            Network.MainNet -> "https://tonapi.io"
            Network.TestNet -> "https://testnet.tonapi.io"
        }

        streamingAPI = StreamingAPI(serverUrl, okHttpClient, logger)
    }

    override fun start(address: Address) {
        this.address = address
        connect()
    }

    private fun connect() {
        val tmpAddress = address

        if (tmpAddress == null) {
            state = State.Disconnected
            return
        }

        if (state == State.Connecting || state == State.Connected) {
            return
        }

        state = State.Connecting

        job?.cancel()
        job = coroutineScope.launch {
            state = State.Connected

            streamingAPI.accountTransactionsFlow(tmpAddress.toRaw())
                .retry {
                    state = State.Disconnected
                    delay(3000)
                    true
                }
                .collect {
                    handleEvent(it)
                }

            state = State.Disconnected
        }
    }

    private suspend fun handleEvent(ssEvent: SSEvent) {
        val json = ssEvent.json
        try {
            if (json.has("tx_hash")) {
                _transactionFlow.emit(json.getString("tx_hash"))
            } else {
                val keys = Iterable {
                    json.keys()
                }.joinToString(",")
                logger.i { "no tx_hash, keys: $keys" }
            }

        } catch (e: Throwable) {
            logger.e(e) { "Failed handling SSE event" }
        }
    }

    override fun stop() {
        address = null
        state = State.Disconnected
        job?.cancel()
    }

}

class StreamingAPI(
    private val serverUrl: String,
    private val okHttpClient: OkHttpClient,
    private val logger: Logger,
) {

    fun accountTransactionsFlow(accountId: String): Flow<SSEvent> {
        return okHttpClient.sse("$serverUrl/v2/sse/accounts/transactions?accounts=${accountId}", logger)
    }

}