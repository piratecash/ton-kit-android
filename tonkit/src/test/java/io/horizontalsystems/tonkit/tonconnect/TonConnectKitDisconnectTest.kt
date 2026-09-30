package io.horizontalsystems.tonkit.tonconnect

import co.touchlab.kermit.Logger
import co.touchlab.kermit.StaticConfig
import com.tonapps.wallet.api.API
import com.tonapps.wallet.data.core.entity.SendRequestEntity
import com.tonapps.wallet.data.tonconnect.entities.DAppEntity
import io.horizontalsystems.tonkit.TonV2Fixture
import io.horizontalsystems.tonkit.tonconnect.event.EventHandlerSendTransaction
import io.horizontalsystems.tonkit.tonconnect.event.TonConnectEventManager
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.runBlocking
import okhttp3.OkHttpClient
import org.junit.Assert.assertEquals
import org.junit.Test

class TonConnectKitDisconnectTest {

    private val logger = Logger(StaticConfig(), "TonConnectKit:MainNet")

    @Test
    fun disconnect_storedSessionWithInvalidClientKey_removesItLocally() = runBlocking {
        // Rows stored by older versions were never checked against the client key rules.
        listOf("0".repeat(64), "abcd").forEach { clientId ->
            val dApp = TonV2Fixture.dApps().first().copy(clientId = clientId)
            val dApps = MutableStateFlow(listOf(dApp))

            createKit(dApps).disconnect(dApp)

            assertEquals(clientId, emptyList<DAppEntity>(), dApps.value)
        }
    }

    private fun createKit(dApps: MutableStateFlow<List<DAppEntity>>): TonConnectKit {
        val dAppManager = DAppManager(InMemoryDAppDao(dApps))
        // Nothing may reach the bridge: an unreachable URL fails the test if it is contacted.
        val api = API(logger, "http://127.0.0.1:9/bridge", OkHttpClient())
        val eventManager = TonConnectEventManager(dAppManager, api, LocalStorage(EmptyKeyValueDao()), logger)
        val sendTransactionHandler = EventHandlerSendTransaction(eventManager, UnusedSendRequestDao())
        return TonConnectKit(logger, dAppManager, eventManager, api, sendTransactionHandler, "test", "1")
    }

    private class InMemoryDAppDao(private val dApps: MutableStateFlow<List<DAppEntity>>) : DAppDao {
        override fun getAllFlow() = dApps
        override suspend fun save(dApp: DAppEntity) = throw UnsupportedOperationException()
        override suspend fun delete(dApp: DAppEntity) {
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
}
