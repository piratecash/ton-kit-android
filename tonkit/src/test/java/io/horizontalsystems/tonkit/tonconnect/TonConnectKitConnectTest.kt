package io.horizontalsystems.tonkit.tonconnect

import com.tonapps.blockchain.ton.extensions.base64
import com.tonapps.security.hex
import com.tonapps.wallet.data.account.entities.ProofEntity
import com.tonapps.wallet.data.tonconnect.entities.DAppEntity
import com.tonapps.wallet.data.tonconnect.entities.DAppManifestEntity
import com.tonapps.wallet.data.tonconnect.entities.DAppRequestEntity
import com.tonapps.wallet.data.tonconnect.entities.reply.DAppAddressItemEntity
import com.tonapps.wallet.data.tonconnect.entities.reply.DAppProofItemReplySuccess
import io.horizontalsystems.tonkit.TonV2Fixture
import io.horizontalsystems.tonkit.core.TonWallet
import io.horizontalsystems.tonkit.core.TonWalletVersion
import io.horizontalsystems.tonkit.core.W5TestVectors
import io.horizontalsystems.tonkit.core.assertSameCell
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.runBlocking
import okhttp3.mockwebserver.MockResponse
import okhttp3.mockwebserver.MockWebServer
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test
import org.ton.block.AddrStd
import org.ton.block.StateInit
import org.ton.boc.BagOfCells
import org.ton.cell.buildCell
import org.ton.kotlin.crypto.PrivateKeyEd25519
import org.ton.tlb.storeTlb
import java.nio.ByteBuffer
import java.nio.ByteOrder
import java.security.MessageDigest

class TonConnectKitConnectTest {
    private val privateKey = PrivateKeyEd25519(ByteArray(32) { (it + 1).toByte() })

    @Test
    fun connect_w5Wallet_repliesW5AddressStateInitProofAndMaxMessages() = runBlocking {
        val server = MockWebServer().apply {
            enqueue(MockResponse())
            start()
        }
        try {
            val dApps = MutableStateFlow(emptyList<DAppEntity>())
            val request = DAppRequestEntity(
                id = hex(TonV2Fixture.bobKeyPair.publicKey),
                r = """{"manifestUrl":"https://dapp.example/m.json","items":[{"name":"ton_addr"},{"name":"ton_proof","payload":"nonce"}]}""",
            )
            val manifest = DAppManifestEntity("https://dapp.example", "dApp", "https://dapp.example/i.png", null, null)
            val wallet = TonWallet.FullAccess(privateKey, TonWalletVersion.W5)

            val reply = tonConnectKit(dApps, server.url("/bridge").toString())
                .connect(request, manifest, "wallet-w5", wallet)

            val addressItem = reply.payload.items.filterIsInstance<DAppAddressItemEntity>().single()
            val proof = reply.payload.items.filterIsInstance<DAppProofItemReplySuccess>().single().proof
            assertEquals(W5TestVectors.W5_ADDRESS, addressItem.address)
            assertSameCell(
                BagOfCells(base64(W5TestVectors.W5_STATE_INIT)).first(),
                buildCell { storeTlb(StateInit, addressItem.walletStateInit) },
            )
            assertEquals(255, reply.payload.device.maxMessages)
            assertTrue(proofSignedFor(AddrStd.parse(W5TestVectors.W5_ADDRESS), proof))
            assertFalse(proofSignedFor(AddrStd.parse(W5TestVectors.V4R2_ADDRESS), proof))
            assertEquals(W5TestVectors.W5_ADDRESS, dApps.value.single().accountId)
            assertEquals("wallet-w5", dApps.value.single().walletId)
        } finally {
            server.shutdown()
        }
    }

    /** Checks a ton_proof signature as a dApp does, per the TON Connect spec. */
    private fun proofSignedFor(address: AddrStd, proof: ProofEntity): Boolean {
        val message = "ton-proof-item-v2/".toByteArray() +
            ByteBuffer.allocate(4).putInt(address.workchainId).array() +
            address.address.toByteArray() +
            ByteBuffer.allocate(4).order(ByteOrder.LITTLE_ENDIAN).putInt(proof.domain.lengthBytes).array() +
            proof.domain.value.toByteArray() +
            ByteBuffer.allocate(8).order(ByteOrder.LITTLE_ENDIAN).putLong(proof.timestamp).array() +
            proof.payload.orEmpty().toByteArray()
        val signed = sha256(byteArrayOf(-1, -1) + "ton-connect".toByteArray() + sha256(message))
        return privateKey.publicKey().verifySignature(signed, base64(proof.signature))
    }

    private fun sha256(value: ByteArray): ByteArray = MessageDigest.getInstance("SHA-256").digest(value)
}
