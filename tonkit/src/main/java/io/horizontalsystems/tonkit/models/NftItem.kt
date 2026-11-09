package io.horizontalsystems.tonkit.models

import io.horizontalsystems.tonkit.Address
import io.tonapi.models.TrustType

data class NftItem(
    val address: String,
    val ownerAddress: String?,
    val name: String?,
    val description: String?,
    val renderType: String?,
    val trust: NftTrust,
    val collection: NftCollection?,
    val previews: List<NftPreview>,
    val isOnSale: Boolean,
    val dns: String?,
    val buttons: List<NftButton>,
    val lottie: String?,
) {
    companion object {
        /** Skips a single item with a malformed address instead of failing the whole list. */
        fun fromApiOrNull(item: io.tonapi.models.NftItem): NftItem? = try {
            fromApi(item)
        } catch (e: IllegalArgumentException) {
            null
        }

        fun fromApi(item: io.tonapi.models.NftItem): NftItem {
            // Everything in metadata is chosen by the NFT creator.
            val metadata = item.metadata
            val owner = item.sale?.owner ?: item.owner

            return NftItem(
                address = Address.parse(item.address).toUserFriendly(bounceable = true),
                ownerAddress = owner?.let { Address.parse(it.address).toUserFriendly(bounceable = false) },
                name = metadata["name"] as? String,
                description = metadata["description"] as? String,
                renderType = metadata["render_type"] as? String,
                trust = NftTrust.fromApi(item.trust),
                collection = item.collection?.let {
                    NftCollection(
                        Address.parse(it.address).toUserFriendly(bounceable = true),
                        it.name,
                        it.description,
                    )
                },
                previews = item.previews.orEmpty().map { NftPreview(it.resolution, it.url) },
                isOnSale = item.sale != null,
                dns = item.dns,
                buttons = NftButton.listFromMetadata(metadata["buttons"]),
                lottie = metadata["lottie"] as? String,
            )
        }
    }
}

data class NftCollection(
    val address: String,
    val name: String,
    val description: String,
)

data class NftPreview(
    val resolution: String,
    val url: String,
)

data class NftButton(
    val label: String,
    val uri: String,
) {
    companion object {
        fun listFromMetadata(raw: Any?): List<NftButton> = (raw as? List<*>).orEmpty().mapNotNull { entry ->
            val map = entry as? Map<*, *>
            val label = map?.get("label") as? String
            val uri = map?.get("uri") as? String
            if (label.isNullOrBlank() || uri.isNullOrBlank()) null else NftButton(label, uri)
        }
    }
}

enum class NftTrust {
    WHITELIST,
    GRAYLIST,
    BLACKLIST,
    NONE;

    companion object {
        fun fromApi(type: TrustType) = when (type) {
            TrustType.whitelist -> WHITELIST
            TrustType.graylist -> GRAYLIST
            TrustType.blacklist -> BLACKLIST
            TrustType.none, TrustType.unknown_default_open_api -> NONE
        }
    }
}
