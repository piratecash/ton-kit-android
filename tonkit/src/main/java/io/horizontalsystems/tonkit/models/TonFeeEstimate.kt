package io.horizontalsystems.tonkit.models

import java.math.BigInteger

/** [totalDebit] is all TON the wallet transaction takes: the value it attaches plus every fee. */
data class TonFeeEstimate(
    val fee: BigInteger,
    val totalDebit: BigInteger,
)

/** [grossFee] adds the forwarding fees charged on top of [totalFees]; neither includes the attached value. */
data class EmulatedFee(
    val totalFees: BigInteger,
    val grossFee: BigInteger,
)
