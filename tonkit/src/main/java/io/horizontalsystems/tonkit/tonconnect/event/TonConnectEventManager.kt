package io.horizontalsystems.tonkit.tonconnect.event

import android.util.Base64
import android.util.Log
import com.tonapps.network.SSEvent
import com.tonapps.wallet.api.API
import com.tonapps.wallet.data.tonconnect.entities.DAppEntity
import com.tonapps.wallet.data.tonconnect.entities.reply.DAppErrorEntity
import com.tonapps.wallet.data.tonconnect.entities.reply.DAppReply
import io.horizontalsystems.tonkit.tonconnect.DAppManager
import io.horizontalsystems.tonkit.tonconnect.LocalStorage
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.launch
import org.json.JSONObject
import org.ton.crypto.base64

class TonConnectEventManager(
    private val dAppManager: DAppManager,
    private val api: API,
    private val localStorage: LocalStorage,
) {
    private val coroutineScope = CoroutineScope(Dispatchers.Default)
    private var handleDAppsJob: Job? = null
    private var collectEventsJob: Job? = null

    private val handlers = mutableMapOf<String, ITonConnectEventHandler>()
    private val receivedEventIds = mutableSetOf<String>()

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
        collectEventsJob = coroutineScope.launch {
            val publicKeys = dApps.map { it.publicKeyHex }

            api.tonconnectEvents(publicKeys, localStorage.getLastSSEventId()).collect {
                Log.e("AAA", "SSE Event: $it")
                processEvent(dApps, it)
            }
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
                Log.w("AAA", "No handler registered for method $method")
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
