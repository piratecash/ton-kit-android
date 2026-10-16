package io.horizontalsystems.tonkit.tonconnect.event

import co.touchlab.kermit.Logger
import com.tonapps.blockchain.ton.extensions.base64
import com.tonapps.network.SSEvent
import com.tonapps.wallet.api.API
import com.tonapps.wallet.data.tonconnect.entities.DAppEntity
import com.tonapps.wallet.data.tonconnect.entities.reply.DAppErrorEntity
import com.tonapps.wallet.data.tonconnect.entities.reply.DAppReply
import io.horizontalsystems.tonkit.tonconnect.DAppManager
import io.horizontalsystems.tonkit.tonconnect.LocalStorage
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.CoroutineExceptionHandler
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.NonCancellable
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.retry
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import kotlinx.coroutines.withTimeoutOrNull
import org.json.JSONObject
import java.util.Collections
import java.util.concurrent.ConcurrentHashMap
import kotlin.io.encoding.Base64

class TonConnectEventManager(
    private val dAppManager: DAppManager,
    private val api: API,
    private val localStorage: LocalStorage,
    private val logger: Logger,
) {
    private val coroutineExceptionHandler = CoroutineExceptionHandler { _, throwable ->
        // Parser messages embed the decrypted request, so only the exception class is logged.
        logger.w { "TonConnect event processing failed: ${throwable::class.simpleName}" }
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

    private suspend fun processEventSafely(dApps: List<DAppEntity>, ssEvent: SSEvent) {
        try {
            processEvent(dApps, ssEvent)
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            logger.w { "Failed processing TonConnect event ${ssEvent.id}: ${e::class.simpleName}" }
        }
    }

    private suspend fun processEvent(dApps: List<DAppEntity>, ssEvent: SSEvent) {
        val ssEventId = ssEvent.id ?: return
        if (receivedEventIds.contains(ssEventId)) return

        receivedEventIds.add(ssEventId)
        // A restarted subscription resumes after the stored cursor, so a committed event must still be dispatched.
        withContext(NonCancellable) {
            localStorage.setLastSSEventId(ssEventId)
            dispatchEvent(dApps, ssEvent)
        }
    }

    private fun dispatchEvent(dApps: List<DAppEntity>, ssEvent: SSEvent) {
        val from = ssEvent.json.getString("from")
        val dApp = dApps.find { it.clientId == from } ?: return

        val message = ssEvent.json.getString("message")
        val body = bridgeBase64.decode(message)
        val jsonObject = JSONObject(dApp.decrypt(body).toString(Charsets.UTF_8))

        val method = jsonObject.getString("method")
        val params = jsonObject.getJSONArray("params")
        val requestId = jsonObject.getString("id")

        val handler = handlers[method]

        coroutineScope.launch {
            if (handler != null) {
                handler.handle(requestId, params, dApp)
            } else {
                logger.w { "No handler registered for the requested method" }
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

// Lenient like android.util.Base64.DEFAULT: skips line breaks and non-alphabet chars, padding optional.
private val bridgeBase64 = Base64.Mime.withPadding(Base64.PaddingOption.PRESENT_OPTIONAL)
