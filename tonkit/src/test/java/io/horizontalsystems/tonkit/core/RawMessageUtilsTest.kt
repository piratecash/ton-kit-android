package io.horizontalsystems.tonkit.core

import com.tonapps.blockchain.ton.extensions.toByteArray
import org.junit.Assert.assertArrayEquals
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotEquals
import org.junit.Assert.assertThrows
import org.junit.Test
import org.ton.block.Either
import org.ton.block.Message
import org.ton.cell.Cell
import org.ton.cell.buildCell
import org.ton.tlb.constructor.AnyTlbConstructor
import org.ton.tlb.storeTlb

class RawMessageUtilsTest {
    @Test
    fun decode_emptyRaw_rejects() {
        assertThrows(IllegalArgumentException::class.java) {
            RawMessageUtils.decode(ByteArray(0))
        }
    }

    @Test
    fun decode_seqnoNonZero_usesRawCellHash() {
        val message = RawMessageTestHelper.message(seqno = 1)
        val decoded = RawMessageUtils.decode(message.toByteArray())

        assertEquals(message.hash().toHexString().lowercase(), decoded.messageHash)
        assertEquals(RawMessageTestHelper.senderAddress, decoded.senderAddress)
    }

    @Test
    fun decode_seqnoZero_stripsStateInitForHash() {
        val message = RawMessageTestHelper.message(seqno = 0)
        val decoded = RawMessageUtils.decode(message.toByteArray())

        assertEquals("677768ecb95f75e1e2a80f7779680f9c1860c8ce4d3357aefbb13ad656772cd2", decoded.messageHash)
        assertNotEquals(message.hash().toHexString().lowercase(), decoded.messageHash)
        assertEquals(RawMessageTestHelper.senderAddress, decoded.senderAddress)
    }

    @Test
    fun decode_inlineBody_matchesReferenceBodyHash() {
        val referenceMessage = RawMessageTestHelper.messageObject(seqno = 1)
        val inlineMessage = referenceMessage.copy(
            body = Either.of(
                checkNotNull(referenceMessage.body.y).cell,
                null,
            )
        )

        val referenceDecoded = RawMessageUtils.decode(referenceMessage.toRaw())
        val inlineDecoded = RawMessageUtils.decode(inlineMessage.toRaw())

        assertEquals(referenceDecoded.messageHash, inlineDecoded.messageHash)
    }

    @Test
    fun decode_reencodesCanonicalBoc() {
        val rawMessage = RawMessageTestHelper.rawMessage()
        val decoded = RawMessageUtils.decode(rawMessage)

        assertArrayEquals(RawMessageUtils.decode(decoded.raw).raw, decoded.raw)
    }

    private fun Message<Cell>.toRaw(): ByteArray {
        return buildCell {
            storeTlb(Message.tlbCodec(AnyTlbConstructor), this@toRaw)
        }.toByteArray()
    }
}
