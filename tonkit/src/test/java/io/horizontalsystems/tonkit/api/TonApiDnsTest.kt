package io.horizontalsystems.tonkit.api

import io.horizontalsystems.tonkit.Address
import io.horizontalsystems.tonkit.core.fakeHttpClient
import io.horizontalsystems.tonkit.models.Network
import io.tonapi.infrastructure.ClientException
import io.tonapi.infrastructure.ServerException
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertThrows
import org.junit.Test

class TonApiDnsTest {
    private val walletRaw = "0:" + "83".repeat(32)
    private val requestedPaths = mutableListOf<String>()

    @Test
    fun resolveDomain_recordWithWallet_returnsWalletAddress() {
        val resolved = api(200, recordWithWallet()).resolveDomain("foundation.ton")

        assertEquals(Address.parse(walletRaw), Address(requireNotNull(resolved).addrStd))
        assertEquals("/v2/dns/foundation.ton/resolve", requestedPaths.single())
    }

    @Test
    fun resolveDomain_recordWithoutWallet_returnsNull() {
        assertNull(api(200, """{"sites":[]}""").resolveDomain("site.ton"))
    }

    @Test
    fun resolveDomain_notFound_returnsNull() {
        assertNull(api(404, """{"error":"entity not found"}""").resolveDomain("unknown.ton"))
    }

    @Test
    fun resolveDomain_otherClientError_throws() {
        assertThrows(ClientException::class.java) {
            api(400, """{"error":"bad request"}""").resolveDomain("bad.ton")
        }
    }

    @Test
    fun resolveDomain_serverError_throws() {
        assertThrows(ServerException::class.java) {
            api(500, """{"error":"internal"}""").resolveDomain("any.ton")
        }
    }

    private fun api(code: Int, json: String) = TonApi(
        Network.MainNet,
        fakeHttpClient { request ->
            requestedPaths += request.url.encodedPath
            code to json
        },
    )

    private fun recordWithWallet(): String {
        val account = """{"address":"$walletRaw","is_scam":false,"is_wallet":true}"""
        return """{"wallet":{"address":"$walletRaw","account":$account,"is_wallet":false,""" +
            """"has_method_pubkey":false,"has_method_seqno":false,"names":[]},"sites":[]}"""
    }
}
