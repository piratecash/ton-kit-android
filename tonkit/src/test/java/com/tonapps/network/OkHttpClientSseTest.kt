package com.tonapps.network

import co.touchlab.kermit.Logger
import co.touchlab.kermit.Severity
import co.touchlab.kermit.StaticConfig
import co.touchlab.kermit.TestLogWriter
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.retry
import kotlinx.coroutines.flow.toList
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.withTimeout
import okhttp3.OkHttpClient
import okhttp3.mockwebserver.MockResponse
import okhttp3.mockwebserver.MockWebServer
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertThrows
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.rules.Timeout
import java.io.IOException

class OkHttpClientSseTest {

    @get:Rule
    val timeout: Timeout = Timeout.seconds(30)

    private val server = MockWebServer()
    private val client = OkHttpClient()
    private val logger = Logger(StaticConfig(logWriterList = listOf(TestLogWriter(loggable = Severity.Verbose))), "test")
    private val url get() = server.url("/events").toString()

    @After
    fun tearDown() {
        server.shutdown()
    }

    @Test
    fun sse_httpError_failsWithException() {
        server.enqueue(MockResponse().setResponseCode(401))

        val error = assertThrows(IOException::class.java) {
            runBlocking { withTimeout(TIMEOUT_MS) { client.sse(url, logger).toList() } }
        }

        assertTrue(error.message.orEmpty().contains("401"))
    }

    @Test
    fun sse_serverClosesStream_failsAfterEvents() {
        server.enqueue(eventResponse())
        val events = mutableListOf<SSEvent>()

        assertThrows(IOException::class.java) {
            runBlocking { withTimeout(TIMEOUT_MS) { client.sse(url, logger).toList(events) } }
        }

        assertEquals(listOf(EVENT_DATA), events.map { it.data })
    }

    @Test
    fun sse_httpErrorThenEvent_retryReconnects() {
        server.enqueue(MockResponse().setResponseCode(401))
        server.enqueue(eventResponse())

        val event = runBlocking { withTimeout(TIMEOUT_MS) { client.sse(url, logger).retry { true }.first() } }

        assertEquals(EVENT_DATA, event.data)
        assertEquals(2, server.requestCount)
    }

    private fun eventResponse() = MockResponse()
        .setHeader("Content-Type", "text/event-stream")
        .setBody("data: $EVENT_DATA\n\n")

    private companion object {
        const val TIMEOUT_MS = 10_000L
        const val EVENT_DATA = """{"tx_hash":"abc"}"""
    }
}
