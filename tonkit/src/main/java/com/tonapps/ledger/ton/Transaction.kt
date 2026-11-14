package com.tonapps.ledger.ton

import org.ton.block.AddrStd
import org.ton.block.Coins
import org.ton.block.StateInit
import org.ton.cell.Cell

/**
 * A v4R2 transfer as a Ledger device rebuilds it. [body] is the exact message body sent; [payload], when set, is the
 * hint the device rebuilds [body] from to show it, otherwise the device shows only its hash (blind signing).
 */
data class Transaction(
    val destination: AddrStd,
    val sendMode: Int,
    val seqno: Int,
    val timeout: Int,
    val bounceable: Boolean,
    val coins: Coins,
    val stateInit: StateInit? = null,
    val payload: TonPayloadFormat? = null,
    val body: Cell? = null,
)
