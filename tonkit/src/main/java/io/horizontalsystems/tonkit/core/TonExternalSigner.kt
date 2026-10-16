package io.horizontalsystems.tonkit.core

import com.tonapps.blockchain.ton.contract.BaseWalletContract
import com.tonapps.blockchain.ton.contract.HashSigner
import com.tonapps.ledger.ton.Transaction
import com.tonapps.ledger.ton.deviceTimeout
import com.tonapps.ledger.ton.recognizePayload
import com.tonapps.ledger.ton.toWalletTransfer
import com.tonapps.wallet.data.core.entity.RawMessageEntity
import org.ton.bitstring.BitString
import org.ton.block.Either
import org.ton.block.Maybe
import org.ton.block.Message
import org.ton.block.StateInit
import org.ton.cell.Cell
import org.ton.cell.buildCell
import org.ton.tlb.CellRef
import org.ton.tlb.constructor.AnyTlbConstructor
import org.ton.tlb.storeTlb

/** Signs a v4R2 transfer on a hardware device that rebuilds the transfer cell from [Transaction] itself. */
interface TonTransactionDeviceSigner {
    suspend fun sign(transaction: Transaction): TonDeviceSignature
}

/** [signedHash] is the hash of the transfer cell the device built and signed. */
class TonDeviceSignature(val signature: ByteArray, val signedHash: ByteArray)

sealed class TonExternalSignerException(message: String) : Exception(message) {
    /** The device signs a transfer of exactly one message. */
    class UnsupportedMessageCount(val count: Int) :
        TonExternalSignerException("The device signs exactly one message, the request has $count")

    /** The device signed another transfer than the one that would be sent. */
    class SignedHashMismatch : TonExternalSignerException("The device signed a different transfer")
}

/** Wallet contracts need a [HashSigner]; an external signer wallet never signs a hash on the host. */
internal object DeviceOnlyHashSigner : HashSigner {
    override fun sign(hash: BitString): BitString =
        throw UnsupportedOperationException("An external signer wallet signs on the device")
}

/** Signs [transaction] on the device and returns the external message, refusing a signature over any other cell. */
internal suspend fun TonTransactionDeviceSigner.signedMessage(
    contract: BaseWalletContract,
    transaction: Transaction,
): Cell {
    val unsignedBody = contract.createTransferUnsignedBody(
        validUntil = transaction.timeout.toUInt().toLong(),
        seqno = transaction.seqno,
        gifts = arrayOf(transaction.toWalletTransfer()),
    )
    val signature = sign(transaction)
    if (!signature.signedHash.contentEquals(unsignedBody.hash().toByteArray())) {
        throw TonExternalSignerException.SignedHashMismatch()
    }
    val signedBody = contract.signedBody(BitString(signature.signature), unsignedBody)
    val message = contract.createTransferMessage(contract.address, transaction.seqno, signedBody)
    return buildCell { storeTlb(Message.tlbCodec(AnyTlbConstructor), message.withInlineStateInit()) }
}

// The internal message of a deploy transfer refers to the same state init cell; ton-kotlin's BoC writer orders such a
// shared cell wrongly, so the unsigned external message carries the state init inline.
private fun Message<Cell>.withInlineStateInit(): Message<Cell> {
    val stateInit = init.value?.let { it.x ?: it.y?.value } ?: return this
    return copy(init = Maybe.of(Either.of<StateInit, CellRef<StateInit>>(stateInit, null)))
}

internal fun RawMessageEntity.toDeviceTransaction(seqno: Int, validUntil: Long): Transaction {
    val body = payload.takeUnless { it.isEmpty() }
    return Transaction(
        destination = address,
        sendMode = walletTransfer.sendMode,
        seqno = seqno,
        timeout = deviceTimeout(seqno, validUntil),
        bounceable = walletTransfer.bounceable,
        coins = coins,
        stateInit = stateInit,
        payload = body?.let(::recognizePayload),
        body = body,
    )
}
