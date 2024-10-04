package io.horizontalsystems.tonkit.tonconnect

import com.tonapps.wallet.data.tonconnect.entities.DAppEntity
import com.tonapps.wallet.data.tonconnect.entities.reply.DAppReply
import com.tonapps.wallet.data.tonconnect.entities.reply.DAppSuccessEntity
import io.horizontalsystems.tonkit.tonconnect.event.ITonConnectEventHandler
import org.json.JSONArray

class EventHandlerDisconnect(private val dAppManager: DAppManager) : ITonConnectEventHandler {
    override val method = "disconnect"

    override suspend fun handle(id: String, params: JSONArray, dApp: DAppEntity): DAppReply {
        dAppManager.remove(dApp)
        return DAppSuccessEntity(id, "{}")
    }

}
