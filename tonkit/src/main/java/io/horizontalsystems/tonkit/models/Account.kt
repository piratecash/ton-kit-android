package io.horizontalsystems.tonkit.models

import androidx.room.Entity
import androidx.room.PrimaryKey
import io.horizontalsystems.tonkit.Address

@Entity
data class Account(
    @PrimaryKey
    val address: Address,
    val balance: Long,
    val status: AccountStatus,
)
