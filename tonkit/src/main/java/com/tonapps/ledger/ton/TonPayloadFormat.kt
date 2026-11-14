package com.tonapps.ledger.ton

import org.ton.block.AddrStd
import org.ton.block.Coins
import org.ton.cell.Cell
import java.math.BigInteger

sealed class TonPayloadFormat {

    data class Comment(val text: String) : TonPayloadFormat()

    data class JettonTransfer(
        val queryId: BigInteger?,
        val coins: Coins,
        val receiverAddress: AddrStd,
        val excessesAddress: AddrStd,
        val customPayload: Cell?,
        val forwardAmount: Coins,
        val forwardPayload: Cell?
    ) : TonPayloadFormat()

    data class NftTransfer(
        val queryId: BigInteger?,
        val newOwnerAddress: AddrStd,
        val excessesAddress: AddrStd,
        val customPayload: Cell?,
        val forwardAmount: Coins,
        val forwardPayload: Cell?
    ) : TonPayloadFormat()
}
