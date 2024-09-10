package io.horizontalsystems.tonkit.models

import androidx.room.Embedded
import androidx.room.Entity
import androidx.room.PrimaryKey
import io.horizontalsystems.tonkit.Address
import java.math.BigInteger

@Entity
data class JettonBalance(
    @Embedded(prefix = "jetton_")
    val jetton: Jetton,
    val balance: BigInteger,
    val walletAddress: Address,
    @PrimaryKey
    val jettonAddress: Address,
) {
    constructor(
        jetton: Jetton,
        balance: BigInteger,
        walletAddress: Address,
    ) : this(
        jetton,
        balance,
        walletAddress,
        jetton.address
    )
}
