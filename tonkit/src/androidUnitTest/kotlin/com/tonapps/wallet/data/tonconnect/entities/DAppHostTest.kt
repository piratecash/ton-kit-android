package com.tonapps.wallet.data.tonconnect.entities

import android.net.Uri
import io.horizontalsystems.tonkit.TonV2Fixture
import org.junit.Assert.assertEquals
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

// Stored rows and ton_proof domains were produced by android.net.Uri.getHost; the kit must keep that result.
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [35])
class DAppHostTest {

    @Test
    fun domain_urlsAcceptedByUri_equalsUriHost() {
        for (url in URLS_WITH_HOST) {
            assertEquals(url, Uri.parse(url).host, dApp(url).domain.value)
        }
    }

    @Test
    fun host_manifestUrls_equalsUriHostOrName() {
        for (url in URLS_WITH_HOST + URLS_WITHOUT_HOST) {
            assertEquals(url, Uri.parse(url).host ?: NAME, manifest(url).host)
        }
    }

    @Test
    fun domain_malformedPercentEscapeInHost_constructsEntity() {
        dApp("https://ex%zzample.com")
    }

    private fun dApp(url: String) = TonV2Fixture.dApps().first().copy(url = url)

    private fun manifest(url: String) = DAppManifestEntity(url, NAME, "", null, null)

    private companion object {
        const val NAME = "Manifest name"

        val URLS_WITH_HOST = listOf(
            "https://dapp.example.com",
            "https://Mixed.Example.COM/path",
            "ftp://example.com",
            "tonapp://My.DApp:1234/x",
            "//example.com/x",
            "https://example.com:99999",
            "https://user:pw@example.com:8080/?q=1#f",
            "https://[::1]:8080/",
            "https://пример.рф",
            "https://ex%41mple.com",
            "https://example.com\\path",
            "https://ex_ample.com",
            "https:///no-host",
            "https://example.com:",
            "https://a+b.example.com?x#y",
        )
        val URLS_WITHOUT_HOST = listOf("example.com", "mailto:dapp@example.com", "tonapp:x", "")
    }
}
