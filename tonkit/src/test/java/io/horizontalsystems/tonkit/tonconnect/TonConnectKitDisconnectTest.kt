package io.horizontalsystems.tonkit.tonconnect

import co.touchlab.kermit.Logger
import co.touchlab.kermit.StaticConfig
import com.tonapps.security.hex
import com.tonapps.wallet.api.API
import com.tonapps.wallet.data.core.entity.SendRequestEntity
import com.tonapps.wallet.data.tonconnect.entities.DAppEntity
import io.horizontalsystems.tonkit.TonV2Fixture
import io.horizontalsystems.tonkit.tonconnect.event.EventHandlerSendTransaction
import io.horizontalsystems.tonkit.tonconnect.event.TonConnectEventManager
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.currentCoroutineContext
import kotlinx.coroutines.ensureActive
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.launch
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.withTimeout
import okhttp3.OkHttpClient
import okhttp3.mockwebserver.Dispatcher
import okhttp3.mockwebserver.MockResponse
import okhttp3.mockwebserver.MockWebServer
import okhttp3.mockwebserver.RecordedRequest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import java.util.concurrent.CountDownLatch
import java.util.concurrent.TimeUnit

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

    @Test
    fun disconnect_cancelledWhileNotifyingBridge_stillRemovesSession() = runBlocking {
        val sendStarted = CountDownLatch(1)
        val releaseSend = CountDownLatch(1)
        val server = MockWebServer().apply {
            dispatcher = object : Dispatcher() {
                override fun dispatch(request: RecordedRequest): MockResponse {
                    sendStarted.countDown()
                    releaseSend.await(10, TimeUnit.SECONDS)
                    return MockResponse()
                }
            }
            start()
        }
        try {
            val dApp = TonV2Fixture.dApps().first().copy(clientId = hex(TonV2Fixture.bobKeyPair.publicKey))
            val dApps = MutableStateFlow(listOf(dApp))
            val kit = createKit(dApps, server.url("/bridge").toString())

            val job = launch(Dispatchers.IO) { kit.disconnect(dApp) }
            assertTrue("bridge POST never started", sendStarted.await(10, TimeUnit.SECONDS))
            assertTrue("disconnect finished before the bridge answered", job.isActive)
            job.cancel()
            releaseSend.countDown()
            withTimeout(10_000) { job.join() }

            assertEquals(emptyList<DAppEntity>(), dApps.value)
        } finally {
            server.shutdown()
        }
    }

    // Nothing may reach the bridge by default: an unreachable URL fails the test if it is contacted.
    private fun createKit(
        dApps: MutableStateFlow<List<DAppEntity>>,
        bridgeUrl: String = "http://127.0.0.1:9/bridge",
    ): TonConnectKit {
        val dAppManager = DAppManager(InMemoryDAppDao(dApps))
        val api = API(logger, bridgeUrl, OkHttpClient())
        val eventManager = TonConnectEventManager(dAppManager, api, LocalStorage(EmptyKeyValueDao()), logger)
        val sendTransactionHandler = EventHandlerSendTransaction(eventManager, UnusedSendRequestDao())
        return TonConnectKit(logger, dAppManager, eventManager, api, sendTransactionHandler, "test", "1")
    }

    private class InMemoryDAppDao(private val dApps: MutableStateFlow<List<DAppEntity>>) : DAppDao {
        override fun getAllFlow() = dApps
        override suspend fun save(dApp: DAppEntity) = throw UnsupportedOperationException()
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
}
