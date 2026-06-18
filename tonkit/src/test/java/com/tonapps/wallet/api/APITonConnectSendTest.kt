package com.tonapps.wallet.api

import okhttp3.OkHttpClient
import okhttp3.mockwebserver.MockResponse
import okhttp3.mockwebserver.MockWebServer
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import java.io.IOException

class APITonConnectSendTest {

    private lateinit var server: MockWebServer

    @Before
    fun setUp() {
        server = MockWebServer()
        server.start()
    }

    @After
    fun tearDown() {
        server.shutdown()
    }

    private fun createApi(client: OkHttpClient = OkHttpClient()): API =
        API(
            bridgeUrl = server.url("/bridge").toString(),
            tonAPIHttpClient = client
        )

    private fun send(api: API): Boolean =
        api.tonconnectSend(
            publicKeyHex = "wallet-key",
            clientId = "client-id",
            body = "payload"
        )

    @Test
    fun tonconnectSend_requestSucceeds_returnsTrue() {
        server.enqueue(MockResponse().setResponseCode(200))
        val api = createApi()

        val result = send(api)

        val request = server.takeRequest()
        assertTrue(result)
        assertEquals("/bridge/message?client_id=wallet-key&to=client-id&ttl=300", request.path)
        assertEquals("payload", request.body.readUtf8())
        assertTrue(request.getHeader("Content-Type").orEmpty().startsWith("text/plain"))
    }

    @Test
    fun tonconnectSend_responseIsNotSuccessful_returnsFalse() {
        server.enqueue(MockResponse().setResponseCode(500))
        val api = createApi()

        val result = send(api)

        assertFalse(result)
    }

    @Test
    fun tonconnectSend_requestThrows_returnsFalse() {
        val api = createApi(
            client = OkHttpClient.Builder()
                .addInterceptor { throw IOException("stream was reset") }
                .build()
        )

        val result = send(api)

        assertFalse(result)
    }
}
