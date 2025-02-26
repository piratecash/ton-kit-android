package io.horizontalsystems.tonkit.models

import com.tonapps.wallet.data.core.entity.SendRequestEntity
import com.tonapps.wallet.data.tonconnect.entities.DAppEntity

data class SignTransaction(
    val request: SendRequestEntity,
    val dApp: DAppEntity
)