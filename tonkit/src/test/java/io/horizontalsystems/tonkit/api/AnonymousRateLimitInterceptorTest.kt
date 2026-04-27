package io.horizontalsystems.tonkit.api

import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.mockwebserver.MockResponse
import okhttp3.mockwebserver.MockWebServer
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import timber.log.Timber

class AnonymousRateLimitInterceptorTest {

    private lateinit var server: MockWebServer
    private val sleepCalls = mutableListOf<Long>()
    private val logMessages = mutableListOf<String>()

    private val fakeSleeper = RateLimitInterceptor.Sleeper { ms ->
        sleepCalls.add(ms)
    }

    private val testTree = object : Timber.Tree() {
        override fun log(priority: Int, tag: String?, message: String, t: Throwable?) {
            logMessages.add(message)
        }
    }

    @Before
    fun setUp() {
        server = MockWebServer()
        server.start()
        sleepCalls.clear()
        logMessages.clear()
        Timber.plant(testTree)
    }

    @After
    fun tearDown() {
        server.shutdown()
        Timber.uproot(testTree)
    }

    private fun createClient(): OkHttpClient =
        OkHttpClient.Builder()
            .addInterceptor(AnonymousRateLimitInterceptor(sleeper = fakeSleeper))
            .build()

    private fun request(): Request =
        Request.Builder().url(server.url("/v2/accounts/test")).build()

    @Test
    fun intercept_no429_returnsImmediately() {
        val client = createClient()
        server.enqueue(MockResponse().setResponseCode(200).setBody("ok"))

        val response = client.newCall(request()).execute()

        assertEquals(200, response.code)
        assertEquals(1, server.requestCount)
        assertEquals(emptyList<Long>(), sleepCalls)
        assertEquals(emptyList<String>(), logMessages)
    }

    @Test
    fun intercept_429WithoutRetryAfter_waitsDefaultAndRetriesOnce() {
        val client = createClient()
        server.enqueue(MockResponse().setResponseCode(429))
        server.enqueue(MockResponse().setResponseCode(200).setBody("ok"))

        val response = client.newCall(request()).execute()

        assertEquals(200, response.code)
        assertEquals(2, server.requestCount)
        assertEquals(listOf(5_000L), sleepCalls)
        assertTrue(logMessages.single().contains("retry in 5000ms"))
    }

    @Test
    fun intercept_429WithRetryAfter_waitsHeaderValueAndRetriesOnce() {
        val client = createClient()
        server.enqueue(MockResponse().setResponseCode(429).setHeader("Retry-After", "2"))
        server.enqueue(MockResponse().setResponseCode(200).setBody("ok"))

        val response = client.newCall(request()).execute()

        assertEquals(200, response.code)
        assertEquals(2, server.requestCount)
        assertEquals(listOf(2_000L), sleepCalls)
    }

    @Test
    fun intercept_429ThenSuccessOnLastRetry_returnsSuccess() {
        val client = createClient()
        repeat(6) {
            server.enqueue(MockResponse().setResponseCode(429))
        }
        server.enqueue(MockResponse().setResponseCode(200).setBody("ok"))

        val response = client.newCall(request()).execute()

        assertEquals(200, response.code)
        assertEquals(7, server.requestCount)
        assertEquals(List(6) { 5_000L }, sleepCalls)
        assertEquals(6, logMessages.size)
    }

    @Test
    fun intercept_429OnAllAttempts_returnsFinalResponseBodyReadable() {
        val client = createClient()
        repeat(6) {
            server.enqueue(MockResponse().setResponseCode(429))
        }
        server.enqueue(MockResponse().setResponseCode(429).setBody("rate limit body"))

        val response = client.newCall(request()).execute()

        assertEquals(429, response.code)
        assertEquals(7, server.requestCount)
        assertEquals("rate limit body", response.body?.string())
        assertEquals(List(6) { 5_000L }, sleepCalls)
        assertEquals(7, logMessages.size)
        assertTrue(logMessages.last().contains("return 429"))
    }

    @Test
    fun intercept_429_logsHeaders() {
        val client = createClient()
        server.enqueue(MockResponse().setResponseCode(429).setHeader("CF-RAY", "ray-id"))
        server.enqueue(MockResponse().setResponseCode(200).setBody("ok"))

        client.newCall(request()).execute()

        assertTrue(logMessages.single().contains("CF-RAY=ray-id"))
    }

    @Test
    fun intercept_429_doesNotLogSensitiveHeaders() {
        val client = createClient()
        server.enqueue(
            MockResponse().setResponseCode(429)
                .setHeader("CF-RAY", "ray-id")
                .setHeader("Set-Cookie", "session=secret-token")
                .setHeader("Authorization", "Bearer secret")
                .setHeader("Proxy-Authenticate", "Basic realm=secret")
        )
        server.enqueue(MockResponse().setResponseCode(200).setBody("ok"))

        client.newCall(request()).execute()

        val log = logMessages.single()
        assertTrue("expected CF-RAY in log: $log", log.contains("CF-RAY=ray-id"))
        assertFalse("Set-Cookie must be filtered out: $log", log.contains("secret-token"))
        assertFalse("Authorization must be filtered out: $log", log.contains("Bearer secret"))
        assertFalse("Proxy-Authenticate must be filtered out: $log", log.contains("Proxy-Authenticate"))
    }

    @Test
    fun intercept_429_logsRateLimitFamilyHeaders() {
        val client = createClient()
        server.enqueue(
            MockResponse().setResponseCode(429)
                .setHeader("X-RateLimit-Remaining", "0")
                .setHeader("X-RateLimit-Reset", "1234")
        )
        server.enqueue(MockResponse().setResponseCode(200).setBody("ok"))

        client.newCall(request()).execute()

        val log = logMessages.single()
        assertTrue(log.contains("X-RateLimit-Remaining=0"))
        assertTrue(log.contains("X-RateLimit-Reset=1234"))
    }
}
