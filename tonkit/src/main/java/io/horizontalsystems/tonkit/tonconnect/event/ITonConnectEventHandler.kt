package io.horizontalsystems.tonkit.tonconnect.event

import com.tonapps.wallet.data.tonconnect.entities.DAppEntity
import org.json.JSONArray

interface ITonConnectEventHandler {
    val method: String
    suspend fun handle(requestId: String, params: JSONArray, dApp: DAppEntity)
}
