package com.tonapps.ledger.ton

import org.ton.block.AddrStd
import org.ton.block.Coins
import org.ton.block.StateInit

data class Transaction(
    val destination: AddrStd,
    val sendMode: Int,
    val seqno: Int,
    val timeout: Int,
    val bounceable: Boolean,
    val coins: Coins,
    val stateInit: StateInit? = null,
    val payload: TonPayloadFormat? = null
)
