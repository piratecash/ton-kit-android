package com.tonapps.ledger.ton

import com.tonapps.blockchain.ton.TonTransferHelper
import org.ton.block.AddrStd
import org.ton.block.Coins
import org.ton.block.MsgAddressInt
import org.ton.block.StateInit
import org.ton.cell.Cell
import org.ton.contract.wallet.MessageData
import org.ton.contract.wallet.WalletTransfer
import org.ton.contract.wallet.WalletTransferBuilder
import org.ton.kotlin.message.MessageLayout
import org.ton.tlb.CellRef
import org.ton.tlb.loadTlb

private const val MAX_COMMENT_LENGTH = 120
private const val COMMENT_OP = 0
private const val JETTON_TRANSFER_OP = 0x0f8a7ea5
private const val OP_BITS = 32

/** The device shows only printable ASCII comments of up to 120 characters; others go without a hint. */
internal fun commentHint(text: String): TonPayloadFormat.Comment? =
    TonPayloadFormat.Comment(text).takeIf { text.length <= MAX_COMMENT_LENGTH && text.all { it in ' '..'~' } }

/** v4R2 writes all ones instead of the timeout into the deploy transfer (seqno 0), so the device must get them too. */
internal fun deviceTimeout(seqno: Int, validUntil: Long): Int = if (seqno == 0) -1 else validUntil.toInt()

/** A hint only for a body the device rebuilds bit for bit; anything else is signed blind. */
internal fun recognizePayload(body: Cell): TonPayloadFormat? = try {
    body.commentHint() ?: body.jettonTransferHint()
} catch (error: Exception) {
    null
}

/**
 * The internal message as the device builds it: state init and a non-empty body always in child cells, unlike the
 * layout ton-kotlin picks by size.
 */
internal fun Transaction.toWalletTransfer(): WalletTransfer {
    val transaction = this
    return WalletTransferBuilder().apply {
        destination = transaction.destination
        bounceable = transaction.bounceable
        coins = transaction.coins
        sendMode = transaction.sendMode
        messageData = MessageData.Raw(
            body = transaction.body ?: Cell.empty(),
            stateInit = transaction.stateInit?.let { CellRef(it, StateInit) },
            layout = MessageLayout(initToCell = true, bodyToCell = transaction.body != null),
        )
    }.build()
}

private fun Cell.commentHint(): TonPayloadFormat.Comment? {
    if (refs.isNotEmpty() || bits.size < OP_BITS || bits.size % Byte.SIZE_BITS != 0) return null
    val slice = beginParse()
    if (slice.loadUInt32().toInt() != COMMENT_OP) return null
    return commentHint(slice.loadByteArray((bits.size - OP_BITS) / Byte.SIZE_BITS).decodeToString())
}

private fun Cell.jettonTransferHint(): TonPayloadFormat.JettonTransfer? {
    val slice = beginParse()
    if (slice.loadUInt32().toInt() != JETTON_TRANSFER_OP) return null
    val queryId = slice.loadULong().toString().toBigInteger()
    val amount = slice.loadTlb(Coins)
    val receiver = slice.loadTlb(MsgAddressInt) as? AddrStd ?: return null
    val response = slice.loadTlb(MsgAddressInt) as? AddrStd ?: return null
    // A custom payload cannot be rebuilt by the helper below, so such a transfer is signed blind.
    if (slice.loadBit()) return null
    val forwardAmount = slice.loadTlb(Coins)
    val forwardPayload = if (slice.loadBit()) slice.loadRef() else null
    val canonical = TonTransferHelper.jetton(
        coins = amount,
        toAddress = receiver,
        responseAddress = response,
        queryId = queryId,
        forwardAmount = forwardAmount.amount.toLong(),
        body = forwardPayload,
    )
    if (canonical.hash() != hash()) return null
    return TonPayloadFormat.JettonTransfer(
        queryId = queryId,
        coins = amount,
        receiverAddress = receiver,
        excessesAddress = response,
        customPayload = null,
        forwardAmount = forwardAmount,
        forwardPayload = forwardPayload,
    )
}
