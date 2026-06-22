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
}

data class RawMessageBroadcastMetadata(
    val validUntil: Long,
    val senderAddress: String? = null,
    val seqno: Int? = null,
)
