package io.horizontalsystems.tonkit.tonconnect

import com.tonapps.wallet.data.tonconnect.entities.DAppEntity
import com.tonapps.wallet.data.tonconnect.entities.reply.DAppSuccessEntity
import io.horizontalsystems.tonkit.tonconnect.event.ITonConnectEventHandler
import io.horizontalsystems.tonkit.tonconnect.event.TonConnectEventManager
import org.json.JSONArray

class EventHandlerDisconnect(
    private val dAppManager: DAppManager,
    private val tonConnectEventManager: TonConnectEventManager
) : ITonConnectEventHandler {
    override val method = "disconnect"

    override suspend fun handle(requestId: String, params: JSONArray, dApp: DAppEntity) {
        dAppManager.remove(dApp)
        tonConnectEventManager.responseToDApp(dApp, DAppSuccessEntity(requestId, "{}"))
    }

}
