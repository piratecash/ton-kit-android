package io.horizontalsystems.tonkit.storage

import androidx.room.Entity
import androidx.room.PrimaryKey

@Entity(tableName = "RawMessageBroadcastRecord")
data class RawMessageBroadcastRecord(
    @PrimaryKey val messageHash: String,
    val bocBase64: String,
    val validUntil: Long,
    val senderAddress: String?,
    val seqno: Int?,
    val firstSendTime: Long,
    val lastSendTime: Long,
    val retriesCount: Int,
)
