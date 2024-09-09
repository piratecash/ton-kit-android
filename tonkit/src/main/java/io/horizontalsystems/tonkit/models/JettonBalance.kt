package io.horizontalsystems.tonkit.models

import io.horizontalsystems.tonkit.Address
import java.math.BigInteger

data class JettonBalance(
    val jetton: Jetton,
    val balance: BigInteger,
    val walletAddress: Address,
)