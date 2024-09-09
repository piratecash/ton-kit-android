package io.horizontalsystems.tonkit.models

data class Event(
    val id: String,
    val lt: Long,
    val timestamp: Long,
    val scam: Boolean,
    val inProgress: Boolean,
    val extra: Long,
    val actions: List<Action>
)
