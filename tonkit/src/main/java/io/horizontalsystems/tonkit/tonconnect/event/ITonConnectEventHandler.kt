package io.horizontalsystems.tonkit.tonconnect.event

import com.tonapps.wallet.data.tonconnect.entities.DAppEntity
import com.tonapps.wallet.data.tonconnect.entities.reply.DAppReply
import org.json.JSONArray

interface ITonConnectEventHandler {
    val method: String
    suspend fun handle(id: String, params: JSONArray, dApp: DAppEntity): DAppReply
}
