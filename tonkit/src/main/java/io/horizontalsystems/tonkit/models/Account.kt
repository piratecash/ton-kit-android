package io.horizontalsystems.tonkit.models

import io.horizontalsystems.tonkit.Address

data class Account(
    val address: Address,
    val balance: Long,
    val status: AccountStatus,
)
