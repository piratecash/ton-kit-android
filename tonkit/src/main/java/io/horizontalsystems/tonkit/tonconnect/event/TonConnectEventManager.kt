package io.horizontalsystems.tonkit.tonconnect.event

import android.util.Base64
import android.util.Log
import com.tonapps.blockchain.ton.extensions.base64
import com.tonapps.network.SSEvent
import com.tonapps.wallet.api.API
import com.tonapps.wallet.data.tonconnect.entities.DAppEntity
import com.tonapps.wallet.data.tonconnect.entities.reply.DAppErrorEntity
import com.tonapps.wallet.data.tonconnect.entities.reply.DAppReply
import io.horizontalsystems.tonkit.tonconnect.DAppManager
import io.horizontalsystems.tonkit.tonconnect.LocalStorage
import kotlinx.coroutines.CoroutineExceptionHandler
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.retry
import kotlinx.coroutines.launch
import kotlinx.coroutines.withTimeoutOrNull
import org.json.JSONObject
import timber.log.Timber
import java.util.Collections
import java.util.concurrent.ConcurrentHashMap

class TonConnectEventManager(
    private val dAppManager: DAppManager,
    private val api: API,
    private val localStorage: LocalStorage,
) {
    private val coroutineExceptionHandler = CoroutineExceptionHandler { _, throwable ->
        Timber.w(throwable, "TonConnect event processing failed")
    }
    private val coroutineScope = CoroutineScope(
        SupervisorJob() + Dispatchers.Default + coroutineExceptionHandler
    )
    private var handleDAppsJob: Job? = null
    private var collectEventsJob: Job? = null

    private val handlers = ConcurrentHashMap<String, ITonConnectEventHandler>()
    private val receivedEventIds: MutableSet<String> = Collections.synchronizedSet(mutableSetOf())

    private val _sseConnectedFlow = MutableStateFlow(false)

    fun registerHandler(handler: ITonConnectEventHandler) {
        handlers[handler.method] = handler
    }

    fun start() {
        handleDAppsJob = coroutineScope.launch {
            dAppManager.getAllFlow().collect {
                handleDApps(it)
            }
        }
    }

    fun stop() {
        handleDAppsJob?.cancel()
        collectEventsJob?.cancel()
    }

    private fun handleDApps(dApps: List<DAppEntity>) {
        collectEventsJob?.cancel()
        _sseConnectedFlow.value = false
        // Clear received event IDs on SSE restart to prevent unbounded memory growth
        // The lastEventId from localStorage ensures we don't reprocess old events
        receivedEventIds.clear()
        collectEventsJob = coroutineScope.launch {
            val publicKeys = dApps.map { it.publicKeyHex }

            api.tonconnectEvents(
                publicKeys = publicKeys,
                lastEventId = localStorage.getLastSSEventId(),
                onConnected = { _sseConnectedFlow.value = true }
            )
                .retry {
                    _sseConnectedFlow.value = false
                    delay(3000)
                    true
                }
                .collect {
                    processEventSafely(dApps, it)
                }
        }
    }

    /**
     * Waits for SSE connection to be established.
     * Returns true if connected within timeout, false otherwise.
     */
    suspend fun awaitSseReady(timeoutMs: Long = 5000): Boolean {
        // Fast path: already connected
        if (_sseConnectedFlow.value) return true
        return withTimeoutOrNull(timeoutMs) {
            _sseConnectedFlow.first { it }
        } != null
    }

    private fun processEventSafely(dApps: List<DAppEntity>, ssEvent: SSEvent) {
        try {
            processEvent(dApps, ssEvent)
        } catch (e: Exception) {
            Timber.w(e, "Failed processing TonConnect event")
        }
    }

    private fun processEvent(dApps: List<DAppEntity>, ssEvent: SSEvent) {
        val ssEventId = ssEvent.id ?: return
        if (receivedEventIds.contains(ssEventId)) return

        receivedEventIds.add(ssEventId)
        localStorage.setLastSSEventId(ssEventId)

        val from = ssEvent.json.getString("from")
        val dApp = dApps.find { it.clientId == from } ?: return

        val message = ssEvent.json.getString("message")
        val body = Base64.decode(message, Base64.DEFAULT)
        val jsonObject = JSONObject(dApp.decrypt(body).toString(Charsets.UTF_8))

        val method = jsonObject.getString("method")
        val params = jsonObject.getJSONArray("params")
        val requestId = jsonObject.getString("id")

        val handler = handlers[method]

        coroutineScope.launch {
            if (handler != null) {
                handler.handle(requestId, params, dApp)
            } else {
                Log.w("TonConnectEventManager", "No handler registered for method $method")
                responseToDApp(dApp, DAppErrorEntity.methodNotSupported(requestId))
            }

        }
    }

    fun responseToDApp(dApp: DAppEntity, response: DAppReply) {
        val responseBody = response.toJSON().toString()
        val encrypted = dApp.encrypt(responseBody)
        api.tonconnectSend(dApp.publicKeyHex, dApp.clientId, base64(encrypted))
    }

    suspend fun responseToDApp(dAppId: String, response: DAppReply) {
        val dApps = dAppManager.getAllFlow().first()
        val dApp = dApps.find { it.uniqueId == dAppId }
        if (dApp != null) {
            responseToDApp(dApp, response)
        }
    }
}
