package io.horizontalsystems.tonkit.tonconnect

import androidx.room.Entity
import androidx.room.PrimaryKey

@Entity
data class KeyValue(
    @PrimaryKey
    val key: String,
    val value: String,
)