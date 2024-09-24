package io.horizontalsystems.tonkit.api

import io.horizontalsystems.tonkit.Address
import kotlinx.coroutines.flow.Flow

interface IApiListener {
    val transactionFlow: Flow<String>

    fun start(address: Address)
    fun stop()

}