package io.horizontalsystems.tonkit.models

import androidx.room.Entity
import androidx.room.PrimaryKey

@Entity
data class Event(
    @PrimaryKey
    val id: String,
    val lt: Long,
    val timestamp: Long,
    val scam: Boolean,
    val inProgress: Boolean,
    val extra: Long,
    val actions: List<Action>
)
