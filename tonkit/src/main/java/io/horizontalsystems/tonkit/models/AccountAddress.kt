package io.horizontalsystems.tonkit.models

import io.horizontalsystems.tonkit.Address

data class AccountAddress(
    val address: Address,
    val name: String?,
    val isScam: Boolean,
    val isWallet: Boolean,
) {
    companion object {
        fun fromApi(accountAddress: io.swagger.client.models.AccountAddress): AccountAddress {
            return AccountAddress(
                Address.parse(accountAddress.address),
                accountAddress.name,
                accountAddress.isScam,
                accountAddress.isWallet,
            )
        }
    }
}