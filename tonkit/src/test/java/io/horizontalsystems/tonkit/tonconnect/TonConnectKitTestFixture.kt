package io.horizontalsystems.tonkit.tonconnect

import co.touchlab.kermit.Logger
import co.touchlab.kermit.StaticConfig
import com.tonapps.wallet.api.API
import com.tonapps.wallet.data.core.entity.SendRequestEntity
import com.tonapps.wallet.data.tonconnect.entities.DAppEntity
import io.horizontalsystems.tonkit.tonconnect.event.EventHandlerSendTransaction
import io.horizontalsystems.tonkit.tonconnect.event.TonConnectEventManager
import kotlinx.coroutines.currentCoroutineContext
import kotlinx.coroutines.ensureActive
import kotlinx.coroutines.flow.MutableStateFlow
import okhttp3.OkHttpClient

// Nothing may reach the bridge by default: an unreachable URL fails the test if it is contacted.
internal fun tonConnectKit(
    dApps: MutableStateFlow<List<DAppEntity>>,
    bridgeUrl: String = "http://127.0.0.1:9/bridge",
): TonConnectKit {
    val logger = Logger(StaticConfig(), "TonConnectKit:MainNet")
    val dAppManager = DAppManager(InMemoryDAppDao(dApps))
    val api = API(logger, bridgeUrl, OkHttpClient())
    val eventManager = TonConnectEventManager(dAppManager, api, LocalStorage(EmptyKeyValueDao()), logger)
    val sendTransactionHandler = EventHandlerSendTransaction(eventManager, UnusedSendRequestDao())
    return TonConnectKit(logger, dAppManager, eventManager, api, sendTransactionHandler, "test", "1")
}

private class InMemoryDAppDao(private val dApps: MutableStateFlow<List<DAppEntity>>) : DAppDao {
    override fun getAllFlow() = dApps
    override suspend fun save(dApp: DAppEntity) {
        dApps.value += dApp
    }
    override suspend fun delete(dApp: DAppEntity) {
        // Room's suspending DAO observes cancellation like this.
        currentCoroutineContext().ensureActive()
        dApps.value -= dApp
    }
    override suspend fun deleteSendRequestsOfDAppsExcept(walletIds: Collection<String>) =
        throw UnsupportedOperationException()
    override suspend fun deleteDAppsExcept(walletIds: Collection<String>) = throw UnsupportedOperationException()
}

private class EmptyKeyValueDao : KeyValueDao {
    override suspend fun save(keyValue: KeyValue) = Unit
    override suspend fun getByKey(k: String): KeyValue? = null
}

private class UnusedSendRequestDao : SendRequestDao {
    override suspend fun save(entity: SendRequestEntity) = throw UnsupportedOperationException()
    override suspend fun delete(entity: SendRequestEntity) = throw UnsupportedOperationException()
}
