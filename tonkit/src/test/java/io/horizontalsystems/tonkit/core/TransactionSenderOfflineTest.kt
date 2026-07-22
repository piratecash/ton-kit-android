package io.horizontalsystems.tonkit.core

import com.tonapps.blockchain.ton.contract.HashSigner
import io.horizontalsystems.tonkit.Address
import io.horizontalsystems.tonkit.FriendlyAddress
import kotlinx.coroutines.runBlocking
import kotlinx.io.bytestring.ByteString
import org.junit.Assert.assertEquals
import org.junit.Assert.assertThrows
import org.junit.Assert.assertTrue
import org.junit.Test
import org.ton.bitstring.BitString
import org.ton.kotlin.crypto.PublicKeyEd25519
import java.math.BigInteger

/**
 * Proves the offline signing overloads make no network call: every [FakeApi]
 * method throws, so any api touch fails the test immediately.
 */
class TransactionSenderOfflineTest {
    private val api = FakeApi(throwOnAnyCall = IllegalStateException("network call on offline path"))
    private val hashSigner = object : HashSigner {
        override fun sign(hash: BitString): BitString {
            return BitString(ByteArray(64) { 1 })
        }
    }
    private val publicKey = PublicKeyEd25519(ByteString(*ByteArray(32) { (it + 1).toByte() }))
    private val sender = Address.parse(RawMessageTestHelper.senderAddress)
    private val recipient = FriendlyAddress.parse(RawMessageTestHelper.senderAddress)
    private val transactionSender = TransactionSender(api, sender, hashSigner, publicKey)

    private val seqno = 7
    private val validUntil = 1_700_000_000L
    private val fee = BigInteger.valueOf(12_345)

    @Test
    fun signedTonTransaction_offlineParams_signsWithoutNetwork() = runBlocking {
        val signed = transactionSender.signedTonTransaction(
            recipient = recipient,
            amount = TonKit.SendAmount.Amount(BigInteger.valueOf(1_000_000_000)),
            comment = "memo",
            seqno = seqno,
            validUntil = validUntil,
            fee = fee,
        )

        assertEquals(fee, signed.fee)
        assertEquals(seqno, signed.seqno)
        assertEquals(validUntil, signed.validUntil)
        assertTrue(signed.bocBase64.isNotEmpty())
    }

    @Test
    fun signedTonTransaction_maxAmount_signsWithoutNetwork() = runBlocking {
        val signed = transactionSender.signedTonTransaction(
            recipient = recipient,
            amount = TonKit.SendAmount.Max,
            comment = null,
            seqno = seqno,
            validUntil = validUntil,
            fee = fee,
        )

        assertEquals(fee, signed.fee)
        assertEquals(seqno, signed.seqno)
        assertEquals(validUntil, signed.validUntil)
        assertTrue(signed.bocBase64.isNotEmpty())
    }

    @Test
    fun signedJettonTransaction_offlineParams_signsWithoutNetwork() = runBlocking {
        val signed = transactionSender.signedJettonTransaction(
            jettonWallet = sender,
            recipient = recipient,
            amount = BigInteger.valueOf(500_000),
            comment = "memo",
            seqno = seqno,
            validUntil = validUntil,
            fee = fee,
        )

        assertEquals(fee, signed.fee)
        assertEquals(seqno, signed.seqno)
        assertEquals(validUntil, signed.validUntil)
        assertTrue(signed.bocBase64.isNotEmpty())
    }

    @Test
    fun signedTonTransaction_zeroSeqno_throwsBeforeAnyApiCall() {
        // IllegalArgumentException (not FakeApi's IllegalStateException) proves
        // the undeployed-wallet guard fires before any api touch.
        assertThrows(IllegalArgumentException::class.java) {
            runBlocking {
                transactionSender.signedTonTransaction(
                    recipient = recipient,
                    amount = TonKit.SendAmount.Amount(BigInteger.ONE),
                    comment = null,
                    seqno = 0,
                    validUntil = validUntil,
                    fee = fee,
                )
            }
        }
    }

    @Test
    fun signedJettonTransaction_zeroSeqno_throwsBeforeAnyApiCall() {
        assertThrows(IllegalArgumentException::class.java) {
            runBlocking {
                transactionSender.signedJettonTransaction(
                    jettonWallet = sender,
                    recipient = recipient,
                    amount = BigInteger.ONE,
                    comment = null,
                    seqno = 0,
                    validUntil = validUntil,
                    fee = fee,
                )
            }
        }
    }
}
