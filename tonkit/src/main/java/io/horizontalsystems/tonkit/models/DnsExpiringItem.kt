package io.horizontalsystems.tonkit.models

import io.tonapi.models.DnsExpiringItemsInner

data class DnsExpiringItem(
    val expiringAt: Long,
    val name: String,
    val nftItem: NftItem?,
) {
    companion object {
        fun fromApi(item: DnsExpiringItemsInner) = DnsExpiringItem(
            expiringAt = item.expiringAt,
            name = item.name,
            nftItem = item.dnsItem?.let(NftItem.Companion::fromApiOrNull),
        )
    }
}
