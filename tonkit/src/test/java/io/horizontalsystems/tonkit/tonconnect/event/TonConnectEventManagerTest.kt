package io.horizontalsystems.tonkit.tonconnect.event

import co.touchlab.kermit.LogWriter
import co.touchlab.kermit.Logger
import co.touchlab.kermit.Severity
import co.touchlab.kermit.StaticConfig
import co.touchlab.kermit.TestLogWriter
import com.tonapps.blockchain.ton.extensions.base64
import com.tonapps.security.CryptoBox
import com.tonapps.security.hex
import com.tonapps.wallet.api.API
import com.tonapps.wallet.data.core.entity.SendRequestEntity
import com.tonapps.wallet.data.tonconnect.entities.DAppEntity
import io.horizontalsystems.tonkit.TonV2Fixture
import io.horizontalsystems.tonkit.tonconnect.DAppDao
import io.horizontalsystems.tonkit.tonconnect.DAppManager
import io.horizontalsystems.tonkit.tonconnect.KeyValue
import io.horizontalsystems.tonkit.tonconnect.KeyValueDao
import io.horizontalsystems.tonkit.tonconnect.LocalStorage
import io.horizontalsystems.tonkit.tonconnect.SendRequestDao
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.withContext
import kotlinx.coroutines.withTimeout
import okhttp3.OkHttpClient
import okhttp3.mockwebserver.Dispatcher
import okhttp3.mockwebserver.MockResponse
import okhttp3.mockwebserver.MockWebServer
import okhttp3.mockwebserver.RecordedRequest
import org.json.JSONArray
import org.json.JSONObject
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Before
import org.junit.Rule
import org.junit.Test
import org.junit.rules.Timeout
import java.util.concurrent.ConcurrentHashMap
import java.util.concurrent.atomic.AtomicInteger

class TonConnectEventManagerTest {

    @get:Rule
    val timeout: Timeout = Timeout.seconds(60)

    private val server = MockWebServer()
    private val logs = TestLogWriter(loggable = Severity.Verbose)
    private val warned = CompletableDeferred<Unit>()
    private val logger = Logger(StaticConfig(logWriterList = listOf(logs, WarningSignal())), "TonConnectKit:MainNet")

    private val dApp: DAppEntity = TonV2Fixture.dApps().first().copy(clientId = hex(dAppKeyPair.publicKey))
    private val dApps = MutableStateFlow<List<DAppEntity>>(listOf(dApp))
    private val keyValues = FakeKeyValueDao()
    private val subscriptions = AtomicInteger()
    private val secondSubscription = CompletableDeferred<Unit>()

    @Volatile
    private var firstSubscriptionData = ""
    private lateinit var manager: TonConnectEventManager

    @Before
    fun setUp() {
        server.dispatcher = BridgeDispatcher()
        server.start()
        manager = TonConnectEventManager(
            DAppManager(FakeDAppDao(dApps)),
            API(logger, server.url("/bridge").toString(), OkHttpClient()),
            LocalStorage(keyValues),
            logger,
        )
    }

    @After
    fun tearDown() {
        manager.stop()
        server.shutdown()
    }

    @Test
    fun processEvent_malformedDecryptedJson_logsNeitherPlaintextNorSecret() = runBlocking {
        firstSubscriptionData = bridgeEvent("""{"$SECRET":1,"$SECRET":2""")

        manager.start()

        assertWarnedWithoutSecret()
    }

    @Test
    fun processEvent_malformedSendTransactionParam_logsNeitherPlaintextNorSecret() = runBlocking {
        firstSubscriptionData = bridgeEvent(
            JSONObject()
                .put("method", "sendTransaction")
                .put("id", "request-1")
                .put("params", JSONArray().put("""{"$SECRET":1,"$SECRET":2"""))
                .toString()
        )
        manager.registerHandler(EventHandlerSendTransaction(manager, UnusedSendRequestDao()))

        manager.start()

        assertWarnedWithoutSecret()
    }

    @Test
    fun processEvent_unknownMethod_logsWithoutMethodName() = runBlocking {
        firstSubscriptionData = bridgeEvent("""{"method":"$SECRET","id":"request-1","params":[]}""")

        manager.start()

        assertWarnedWithoutSecret()
    }

    @Test
    fun processEvent_malformedBridgeEnvelope_logsWithoutEnvelope() = runBlocking {
        firstSubscriptionData = """{"$SECRET":1,"$SECRET":2"""

        manager.start()

        assertWarnedWithoutSecret()
    }

    @Test
    fun processEvent_collectorCancelledRightAfterCursorCommit_stillHandlesEvent() = runBlocking {
        val handled = CompletableDeferred<String>()
        manager.registerHandler(RecordingHandler(handled))
        firstSubscriptionData = bridgeEvent("""{"method":"sendTransaction","id":"request-1","params":[]}""")
        // Like Room: the write commits, then a dApp-list change cancels the collector before it resumes.
        keyValues.afterFirstCommit = {
            dApps.value = listOf(dApp, otherDApp)
            withContext(Dispatchers.IO) { secondSubscription.await() }
        }

        manager.start()

        assertEquals("request-1", withTimeout(10_000) { handled.await() })
        assertEquals("1", keyValues.values[LAST_EVENT_ID_KEY])
    }

    private suspend fun assertWarnedWithoutSecret() {
        withTimeout(10_000) { warned.await() }
        val leaking = logs.logs.filter { entry ->
            SECRET in entry.message || entry.throwable?.stackTraceToString()?.contains(SECRET) == true
        }
        assertEquals(emptyList<String>(), leaking.map { it.message })
    }

    private fun bridgeEvent(plaintext: String): String {
        val message = CryptoBox.encrypt(plaintext.toByteArray(), dApp.keyPair.publicKey, dAppKeyPair.privateKey)
        return JSONObject().put("from", dApp.clientId).put("message", base64(message)).toString()
    }

    private inner class WarningSignal : LogWriter() {
        override fun log(severity: Severity, message: String, tag: String, throwable: Throwable?) {
            if (severity == Severity.Warn) warned.complete(Unit)
        }
    }

    private inner class BridgeDispatcher : Dispatcher() {
        override fun dispatch(request: RecordedRequest): MockResponse {
            if (request.path?.startsWith("/bridge/events") != true) return MockResponse()
            val subscription = subscriptions.incrementAndGet()
            if (subscription > 1) secondSubscription.complete(Unit)
            val body = if (subscription == 1) "id: 1\ndata: $firstSubscriptionData\n\n" else ""
            return MockResponse().setHeader("Content-Type", "text/event-stream").setBody(body)
        }
    }

    private class RecordingHandler(private val handled: CompletableDeferred<String>) : ITonConnectEventHandler {
        override val method = "sendTransaction"

        override suspend fun handle(requestId: String, params: JSONArray, dApp: DAppEntity) {
            handled.complete(requestId)
        }
    }

    private class FakeKeyValueDao : KeyValueDao {
        val values = ConcurrentHashMap<String, String>()

        @Volatile
        var afterFirstCommit: (suspend () -> Unit)? = null

        override suspend fun save(keyValue: KeyValue) {
            values[keyValue.key] = keyValue.value
            afterFirstCommit?.also { afterFirstCommit = null }?.invoke()
        }

        override suspend fun getByKey(k: String) = values[k]?.let { KeyValue(k, it) }
    }

    private class FakeDAppDao(private val dApps: MutableStateFlow<List<DAppEntity>>) : DAppDao {
        override fun getAllFlow() = dApps
        override suspend fun save(dApp: DAppEntity) = throw UnsupportedOperationException()
        override suspend fun delete(dApp: DAppEntity) = throw UnsupportedOperationException()
        override suspend fun deleteSendRequestsOfDAppsExcept(walletIds: Collection<String>) =
            throw UnsupportedOperationException()
        override suspend fun deleteDAppsExcept(walletIds: Collection<String>) = throw UnsupportedOperationException()
    }

    private class UnusedSendRequestDao : SendRequestDao {
        override suspend fun save(entity: SendRequestEntity) = throw UnsupportedOperationException()
        override suspend fun delete(entity: SendRequestEntity) = throw UnsupportedOperationException()
    }

    private companion object {
        const val SECRET = "demo-secret"
        const val LAST_EVENT_ID_KEY = "LastSSEventId"

        val dAppKeyPair = TonV2Fixture.bobKeyPair
        val otherDApp = TonV2Fixture.dApps().last()
    }
}
