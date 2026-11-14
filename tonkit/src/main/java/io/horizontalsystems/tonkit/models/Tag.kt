package io.horizontalsystems.tonkit.models

import androidx.room.Entity
import androidx.room.PrimaryKey
import io.horizontalsystems.tonkit.Address

@Entity
data class Tag(
    val eventId: String,
    val type: Type? = null,
    val platform: Platform? = null,
    val jettonAddress: Address? = null,
    val addresses: List<Address> = listOf(),
    @PrimaryKey(autoGenerate = true)
    val id: Int = 0
) {
    enum class Platform {
        Native, Jetton;
    }

    enum class Type {
        Incoming,
        Outgoing,
        Swap,
        Unsupported;
    }

    // Null fields in `tagQuery` act as wildcards, matching the SQL filtering
    // performed by `EventDao.events` (it skips WHERE clauses for null fields).
    fun conforms(tagQuery: TagQuery): Boolean {
        if (tagQuery.type != null && tagQuery.type != type) {
            return false
        }

        if (tagQuery.platform != null && tagQuery.platform != platform) {
            return false
        }

        if (tagQuery.jettonAddress != null && tagQuery.jettonAddress != jettonAddress) {
            return false
        }

        if (tagQuery.address != null && !addresses.contains(tagQuery.address)) {
            return false
        }

        return true
    }
}
