package io.horizontalsystems.tonkit.models

import io.horizontalsystems.tonkit.Address

data class Tag(
    val eventId: String,
    val type: Type? = null,
    val platform: Platform? = null,
    val jettonAddress: Address? = null,
    val addresses: List<Address>,
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

    fun conforms(tagQuery: TagQuery): Boolean {
        if (tagQuery.type != type) {
            return false
        }

        if (tagQuery.platform != platform) {
            return false
        }

        if (tagQuery.jettonAddress != jettonAddress) {
            return false
        }

        if (!addresses.contains(tagQuery.address)) {
            return false
        }

        return true
    }
}
