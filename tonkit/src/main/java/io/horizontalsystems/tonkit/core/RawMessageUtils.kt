package io.horizontalsystems.tonkit.core

import com.tonapps.blockchain.ton.extensions.encodeBase64
import org.ton.block.AddrNone
import org.ton.block.Coins
import org.ton.block.Either
import org.ton.block.ExtInMsgInfo
import org.ton.block.Maybe
import org.ton.block.Message
import org.ton.block.MsgAddressInt
import org.ton.block.StateInit
import org.ton.boc.BagOfCells
import org.ton.cell.Cell
import org.ton.tlb.CellRef
import org.ton.tlb.constructor.AnyTlbConstructor

internal object RawMessageUtils {
    private val messageCodec = Message.tlbCodec(AnyTlbConstructor)

    fun decode(rawMessage: ByteArray): DecodedRawMessage {
        require(rawMessage.isNotEmpty()) { "Raw message is empty" }

        val root = BagOfCells(rawMessage).roots.singleOrNull()
            ?: throw IllegalArgumentException("Raw message must contain exactly one BOC root")
        val message = messageCodec.loadTlb(root)
        val info = message.info as? ExtInMsgInfo
            ?: throw IllegalArgumentException("Raw message is not an external inbound message")
        val canonicalRaw = BagOfCells(root).toByteArray()

        return DecodedRawMessage(
            raw = canonicalRaw,
            bocBase64 = canonicalRaw.encodeBase64(),
            messageHash = normalizedMessageHash(message, info),
            senderAddress = senderAddress(info),
            seqno = null,
        )
    }

    private fun normalizedMessageHash(message: Message<Cell>, info: ExtInMsgInfo): String {
        val normalizedMessage = message.copy(
            info = ExtInMsgInfo(AddrNone, info.dest, Coins.ZERO),
            init = Maybe.of<Either<StateInit, CellRef<StateInit>>>(null),
            body = bodyByRef(message.body),
        )

        return messageCodec.createCell(normalizedMessage).hash().toHexString().lowercase()
    }

    private fun bodyByRef(body: Either<Cell, CellRef<Cell>>): Either<Cell, CellRef<Cell>> {
        body.y?.let { return Either.of(null, it) }

        val inlineBody = body.x ?: throw IllegalArgumentException("Raw message body is empty")
        return Either.of(null, CellRef(cell = inlineBody, codec = AnyTlbConstructor))
    }

    private fun senderAddress(info: ExtInMsgInfo): String? {
        return try {
            MsgAddressInt.toString(info.dest, userFriendly = false).lowercase()
        } catch (error: Throwable) {
            null
        }
    }
}

internal data class DecodedRawMessage(
    val raw: ByteArray,
    val bocBase64: String,
    val messageHash: String,
    val senderAddress: String?,
    val seqno: Int?,
)
