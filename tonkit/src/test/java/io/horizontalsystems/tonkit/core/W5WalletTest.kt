package io.horizontalsystems.tonkit.core

import com.tonapps.blockchain.ton.contract.HashSigner
import com.tonapps.blockchain.ton.contract.PrivateKeyHashSigner
import com.tonapps.blockchain.ton.extensions.base64
import com.tonapps.wallet.data.core.entity.SendRequestEntity
import io.horizontalsystems.tonkit.Address
import io.horizontalsystems.tonkit.FriendlyAddress
import io.horizontalsystems.tonkit.core.W5TestVectors.V4R2_ADDRESS
import io.horizontalsystems.tonkit.core.W5TestVectors.W5_ADDRESS
import io.horizontalsystems.tonkit.core.W5TestVectors.W5_JETTON
import io.horizontalsystems.tonkit.core.W5TestVectors.W5_STATE_INIT
import io.horizontalsystems.tonkit.core.W5TestVectors.W5_TESTNET_ADDRESS
import io.horizontalsystems.tonkit.core.W5TestVectors.W5_THREE_MESSAGES
import io.horizontalsystems.tonkit.core.W5TestVectors.W5_TON_MAX
import io.horizontalsystems.tonkit.core.W5TestVectors.W5_TON_SEQNO_0
import io.horizontalsystems.tonkit.core.W5TestVectors.W5_TON_SEQNO_5
import io.horizontalsystems.tonkit.models.Network
import kotlinx.coroutines.runBlocking
import org.json.JSONArray
import org.json.JSONObject
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test
import org.ton.bitstring.BitString
import org.ton.block.AddrStd
import org.ton.block.Message
import org.ton.block.StateInit
import org.ton.boc.BagOfCells
import org.ton.cell.Cell
import org.ton.cell.buildCell
import org.ton.kotlin.crypto.PrivateKeyEd25519
import org.ton.tlb.constructor.AnyTlbConstructor
import org.ton.tlb.loadTlb
import org.ton.tlb.storeTlb
import java.math.BigInteger

/** Vectors: tools/w5-vectors, built with ton-org's WalletContractV5R1 from the same key and inputs. */
class W5WalletTest {
    private val privateKey = PrivateKeyEd25519(ByteArray(32) { (it + 1).toByte() })
    private val hashSigner = RecordingHashSigner(PrivateKeyHashSigner(privateKey))
    private val wallet = TonWallet.FullAccess(privateKey.publicKey(), hashSigner, TonWalletVersion.W5)
    private val recipient = FriendlyAddress(AddrStd.parse("0:" + "2f".repeat(32)), isBounceable = false)
    private val offlineApi = FakeApi(throwOnAnyCall = IllegalStateException("network call on offline path"))

    @Test
    fun address_eachVersionAndNetwork_matchesReference() {
        assertEquals(Address.parse(V4R2_ADDRESS), TonWallet.FullAccess(privateKey, TonWalletVersion.V4R2).address(Network.MainNet))
        assertEquals(Address.parse(W5_ADDRESS), wallet.address(Network.MainNet))
        assertEquals(Address.parse(W5_TESTNET_ADDRESS), wallet.address(Network.TestNet))
    }

    @Test
    fun send_undeployedW5_broadcastsReferenceDeployWithStateInit() = runBlocking {
        val api = FakeApi(currentSeqno = listOf(0))

        TransactionSender(api, wallet, Network.MainNet)
            .send(recipient, TonKit.SendAmount.Amount(BigInteger.valueOf(1_000_000_000)), "deploy")

        val message = externalMessage(base64(api.sentBocs.single()))
        assertSameCell(cell(W5_STATE_INIT), message.init.value?.let { stateInitCell(it.x ?: it.y?.value) })
        assertSignedReferenceBody(W5_TON_SEQNO_0, message)
    }

    @Test
    fun signedTonTransaction_w5Seqno5_signsReferenceBody() = runBlocking {
        val signed = TransactionSender(offlineApi, wallet, Network.MainNet).signedTonTransaction(
            recipient, TonKit.SendAmount.Amount(BigInteger.valueOf(500_000_000)), "memo", 5, VALID_UNTIL, BigInteger.ONE,
        )

        val message = externalMessage(signed.raw)
        assertNull(message.init.value)
        assertSignedReferenceBody(W5_TON_SEQNO_5, message)
    }

    @Test
    fun signedTonTransaction_w5Max_addsIgnoreErrorsToSendMode() = runBlocking {
        val signed = TransactionSender(offlineApi, wallet, Network.MainNet)
            .signedTonTransaction(recipient, TonKit.SendAmount.Max, null, 5, VALID_UNTIL, BigInteger.ONE)

        assertSignedReferenceBody(W5_TON_MAX, externalMessage(signed.raw))
    }

    @Test
    fun jettonTransfer_w5_bodyMatchesReference() = runBlocking {
        val transfer = TonRawTransactionBuilder(offlineApi, wallet.walletEntity(Network.MainNet)).jettonTransfer(
            Address.parse("0:" + "3c".repeat(32)),
            FriendlyAddress(recipient.addrStd, isBounceable = true),
            BigInteger.valueOf(1_000_000),
            "hi",
            seqno = 5,
            validUntil = VALID_UNTIL,
        ).copy(queryId = BigInteger.valueOf(12345))

        assertSameCell(cell(W5_JETTON), transfer.unsignedBody)
    }

    @Test
    fun sign_w5ThreeMessages_signsReferenceBodyInRequestOrder() = runBlocking {
        val boc = TransactionSigner(fakeTonApi(seqno = 5, rawTime = RAW_TIME)).sign(threeMessagesRequest(), wallet)

        assertSignedReferenceBody(W5_THREE_MESSAGES, externalMessage(base64(boc)))
    }

    @Test
    fun sign_explicitSeqno_signsWithItWithoutReadingSeqno() = runBlocking {
        // The api cannot answer a seqno request, so reading one fails the test.
        val signer = TransactionSigner(fakeTonApi(seqno = null, rawTime = RAW_TIME))

        val boc = signer.sign(threeMessagesRequest(), wallet, seqno = 5)

        assertSignedReferenceBody(W5_THREE_MESSAGES, externalMessage(base64(boc)))
    }

    private fun threeMessagesRequest() = sendRequest(
        message("a1", bounceable = true, amount = 100_000_000),
        message("b2", bounceable = false, amount = 200_000_000)
            .put("payload", buildCell { storeUInt(0, 32); storeBytes("second".toByteArray()) }.base64()),
        message("c3", bounceable = true, amount = 300_000_000),
    )

    /** The body is the reference one, signed at the tail through the wallet's [HashSigner]. */
    private fun assertSignedReferenceBody(vector: String, message: Message<Cell>) {
        val expected = cell(vector)
        val body = requireNotNull(message.body.x ?: message.body.y?.value)
        val bits = body.beginParse()
        // Bit by bit: ton-kotlin 0.5.0 bulk loadBits after skipBits overruns its buffer.
        val unsigned = buildCell {
            repeat(bits.remainingBits - SIGNATURE_BITS) { storeBit(bits.loadBit()) }
            storeRefs(body.refs)
        }
        val signature = buildCell { repeat(SIGNATURE_BITS) { storeBit(bits.loadBit()) } }.bits.toByteArray()

        assertSameCell(expected, unsigned)
        assertEquals(listOf(expected.hash()), hashSigner.hashes)
        assertTrue(privateKey.publicKey().verifySignature(expected.hash().toByteArray(), signature))
    }

    private fun externalMessage(boc: ByteArray): Message<Cell> =
        Message.tlbCodec(AnyTlbConstructor).loadTlb(BagOfCells(boc).first())

    private fun stateInitCell(stateInit: StateInit?): Cell? = stateInit?.let { buildCell { storeTlb(StateInit, it) } }

    private fun cell(vector: String): Cell = BagOfCells(base64(vector)).first()

    private fun message(hexByte: String, bounceable: Boolean, amount: Long) = JSONObject()
        .put("address", AddrStd.parse("0:" + hexByte.repeat(32)).toString(userFriendly = true, bounceable = bounceable))
        .put("amount", amount.toString())

    private fun sendRequest(vararg messages: JSONObject) =
        SendRequestEntity(JSONObject().put("messages", JSONArray(messages.toList())), "id", "dapp")

    private class RecordingHashSigner(private val signer: HashSigner) : HashSigner {
        val hashes = mutableListOf<BitString>()

        override fun sign(hash: BitString): BitString {
            hashes += hash
            return signer.sign(hash)
        }
    }

    private companion object {
        const val VALID_UNTIL = 1_893_456_000L
        const val RAW_TIME = 1_893_455_000
        const val SIGNATURE_BITS = 512
    }
}
