package io.horizontalsystems.tonkit.core

import com.tonapps.blockchain.ton.contract.HashSigner
import com.tonapps.blockchain.ton.extensions.base64
import com.tonapps.ledger.ton.TonPayloadFormat
import io.horizontalsystems.tonkit.Address
import io.horizontalsystems.tonkit.FriendlyAddress
import io.horizontalsystems.tonkit.models.Network
import kotlinx.coroutines.runBlocking
import kotlinx.io.bytestring.ByteString
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test
import org.ton.bitstring.BitString
import org.ton.block.AddrStd
import org.ton.block.Coins
import org.ton.block.MsgAddressInt
import org.ton.boc.BagOfCells
import org.ton.cell.Cell
import org.ton.cell.CellSlice
import org.ton.cell.buildCell
import org.ton.kotlin.crypto.PublicKeyEd25519
import org.ton.tlb.loadTlb
import java.math.BigInteger

class NftTransferTest {
    private val hashSigner = object : HashSigner {
        override fun sign(hash: BitString): BitString = BitString(ByteArray(64) { 1 })
    }
    private val publicKey = PublicKeyEd25519(ByteString(*ByteArray(32) { (it + 1).toByte() }))
    private val wallet = TonWallet.FullAccess(publicKey, hashSigner, TonWalletVersion.V4R2)
    private val walletAddress = wallet.address(Network.MainNet)
    private val nft = Address.parse("0:" + "3c".repeat(32))
    private val recipientAddress = AddrStd.parse("0:" + "2f".repeat(32))
    private val nonBounceableRecipient = FriendlyAddress(recipientAddress, isBounceable = false)
    private val api = FakeApi(currentSeqno = listOf(3))
    private val sender = TransactionSender(api, wallet, Network.MainNet)

    @Test
    fun send_nonBounceableRecipient_messageGoesBounceableToNftContractWithAttachedValue() = runBlocking {
        sender.send(nft, nonBounceableRecipient, "hi")

        val message = sentMessage()
        assertEquals(nft, message.destination)
        assertTrue(message.bounce)
        assertEquals(50_000_000L, message.attached)
    }

    @Test
    fun send_withComment_bodyTransfersNftToRecipientWithForwardComment() = runBlocking {
        sender.send(nft, nonBounceableRecipient, "hi")

        val body = sentBody().beginParse()
        assertEquals(0x5fcc3d14, body.loadUInt(32).toInt())
        body.loadUInt(64)
        assertEquals(Address(recipientAddress), body.loadAddress())
        assertEquals(walletAddress, body.loadAddress())
        assertFalse("no custom payload", body.loadBit())
        assertEquals(1L, body.loadTlb(Coins).amount.toLong())
        assertTrue("forward payload present", body.loadBit())
        assertSameCell(buildCell { storeUInt(0, 32); storeBytes("hi".toByteArray()) }, body.loadRef())
    }

    @Test
    fun send_withoutComment_bodyHasNoForwardPayload() = runBlocking {
        sender.send(nft, nonBounceableRecipient, null)

        val body = sentBody().beginParse()
        body.loadUInt(32)
        body.loadUInt(64)
        body.loadAddress()
        body.loadAddress()
        body.loadBit()
        body.loadTlb(Coins)
        assertFalse(body.loadBit())
    }

    @Test
    fun send_tonTransferNonBounceableRecipient_staysNonBounceable() = runBlocking {
        sender.send(nonBounceableRecipient, TonKit.SendAmount.Amount(BigInteger.TEN), null)

        val message = sentMessage()
        assertEquals(Address(recipientAddress), message.destination)
        assertFalse(message.bounce)
    }

    @Test
    fun nftTransfer_externalSigner_deviceTransactionDescribesTheNftTransfer() = runBlocking {
        val builder = TonRawTransactionBuilder(api, wallet.walletEntity(Network.MainNet))

        val transfer = builder.nftTransfer(nft, nonBounceableRecipient, "hi")

        val transaction = transfer.ledgerTransaction
        val hint = transaction.payload as TonPayloadFormat.NftTransfer
        assertEquals(nft, Address(transaction.destination))
        assertTrue(transaction.bounceable)
        assertEquals(50_000_000L, transaction.coins.amount.toLong())
        assertEquals(transfer.queryId, hint.queryId)
        assertEquals(Address(recipientAddress), Address(hint.newOwnerAddress))
        assertEquals(walletAddress, Address(hint.excessesAddress))
        assertSameCell(buildCell { storeUInt(0, 32); storeBytes("hi".toByteArray()) }, hint.forwardPayload)
    }

    /** ton-kotlin cannot load the addr_none source a wallet sends, so the header is read field by field. */
    private fun sentMessage(): SentMessage {
        val external = BagOfCells(base64(api.sentBocs.single())).first()
        val message = external.refs.last().refs.single()
        val header = message.beginParse()
        repeat(2) { header.loadBit() } // int_msg_info tag, ihr_disabled
        val bounce = header.loadBit()
        repeat(3) { header.loadBit() } // bounced, addr_none source
        val destination = header.loadAddress()
        val attached = header.loadTlb(Coins).amount.toLong()
        return SentMessage(bounce, destination, attached, message.refs.singleOrNull())
    }

    private fun sentBody(): Cell = requireNotNull(sentMessage().body)

    private fun CellSlice.loadAddress() = Address(loadTlb(MsgAddressInt) as AddrStd)

    private class SentMessage(val bounce: Boolean, val destination: Address, val attached: Long, val body: Cell?)
}
