package io.horizontalsystems.tonkit.tonconnect.event

import com.tonapps.wallet.data.core.entity.SendRequestEntity
import com.tonapps.wallet.data.tonconnect.entities.DAppEntity
import com.tonapps.wallet.data.tonconnect.entities.DAppEventEntity
import com.tonapps.wallet.data.tonconnect.entities.reply.DAppErrorEntity
import com.tonapps.wallet.data.tonconnect.entities.reply.DAppSuccessEntity
import io.horizontalsystems.tonkit.tonconnect.SendRequestDao
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.asSharedFlow
import org.json.JSONArray

class EventHandlerSendTransaction(
    private val tonConnectEventManager: TonConnectEventManager,
    private val sendRequestDao: SendRequestDao
) : ITonConnectEventHandler {
    override val method = "sendTransaction"

    private val _sendRequestFlow = MutableSharedFlow<SendRequestEntity>()
    val sendRequestFlow = _sendRequestFlow.asSharedFlow()

    override suspend fun handle(requestId: String, params: JSONArray, dApp: DAppEntity) {
        for (i in 0 until params.length()) {
            val param = DAppEventEntity.parseParam(params.get(i))
            val request = SendRequestEntity(param, requestId, dApp.uniqueId)

            addPendingRequest(request)
        }
    }

    private suspend fun addPendingRequest(request: SendRequestEntity) {
        sendRequestDao.save(request)
        _sendRequestFlow.emit(request)
    }

    private fun removePendingRequest(request: SendRequestEntity) {
        sendRequestDao.delete(request)
    }

    suspend fun reject(request: SendRequestEntity) {
        removePendingRequest(request)

        tonConnectEventManager.responseToDApp(
            request.dAppId,
            DAppErrorEntity(
                id = request.tonConnectRequestId,
                errorCode = 300,
                errorMessage = "User declined the transaction"
            )
        )
    }

    suspend fun approve(request: SendRequestEntity, boc: String) {
        removePendingRequest(request)

        tonConnectEventManager.responseToDApp(
            request.dAppId,
            DAppSuccessEntity(
                id = request.tonConnectRequestId,
                result = boc
            )
        )
    }

}