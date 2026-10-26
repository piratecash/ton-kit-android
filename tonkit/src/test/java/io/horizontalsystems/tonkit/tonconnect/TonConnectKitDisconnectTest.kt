package io.horizontalsystems.tonkit.tonconnect

import com.tonapps.security.hex
import com.tonapps.wallet.data.tonconnect.entities.DAppEntity
import io.horizontalsystems.tonkit.TonV2Fixture
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.launch
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.withTimeout
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

    @Test
    fun disconnect_storedSessionWithInvalidClientKey_removesItLocally() = runBlocking {
        // Rows stored by older versions were never checked against the client key rules.
        listOf("0".repeat(64), "abcd").forEach { clientId ->
            val dApp = TonV2Fixture.dApps().first().copy(clientId = clientId)
            val dApps = MutableStateFlow(listOf(dApp))

            tonConnectKit(dApps).disconnect(dApp)

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
            val kit = tonConnectKit(dApps, server.url("/bridge").toString())

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
}
