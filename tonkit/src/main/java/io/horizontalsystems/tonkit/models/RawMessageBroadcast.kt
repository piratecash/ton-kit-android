package io.horizontalsystems.tonkit.models

import java.math.BigInteger

class SignedRawTonTransaction(
    val raw: ByteArray,
    val bocBase64: String,
    val messageHash: String,
    val fee: BigInteger,
    val validUntil: Long,
    val senderAddress: String,
    val seqno: Int,
)

data class RawMessageBroadcastResult(
    val messageHash: String,
    val status: RawMessageBroadcastStatus,
)

enum class RawMessageBroadcastStatus {
    Submitted,
    Queued,
    AlreadyKnown,

    /**
     * The account seqno moved past this message's seqno, but the message itself
     * was not found on-chain. Ambiguous: hash indexing can lag an accepted send,
     * so the transaction may still have been executed — the message just will
     * never be accepted again with this seqno.
     */
    SeqnoConsumed,
}

data class RawMessageBroadcastMetadata(
    val validUntil: Long,
    val senderAddress: String? = null,
    val seqno: Int? = null,
)
