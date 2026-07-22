package io.horizontalsystems.tonkit.core

import okhttp3.Call
import okhttp3.EventListener
import okhttp3.Request
import org.junit.Assert.assertSame
import org.junit.Test

/**
 * Guards the passive network-observer seam of [TonKit.buildOkHttpClient]:
 * a supplied [EventListener.Factory] is attached to the built client, and the default (null) client
 * installs NO observer — OkHttp's default factory yields [EventListener.NONE], so behavior is unchanged.
 */
class TonKitForwardingTest {

    private val recordingFactory = object : EventListener.Factory {
        override fun create(call: Call): EventListener = EventListener.NONE
    }

    @Test
    fun buildOkHttpClient_withFactory_attachesIt() {
        val client = TonKit.buildOkHttpClient(emptyList(), recordingFactory)

        assertSame(recordingFactory, client.eventListenerFactory)
    }

    @Test
    fun buildOkHttpClient_withoutFactory_installsNoObserver() {
        val client = TonKit.buildOkHttpClient(emptyList(), null)

        val call = client.newCall(Request.Builder().url("https://tonapi.io").build())
        assertSame(EventListener.NONE, client.eventListenerFactory.create(call))
    }
}
