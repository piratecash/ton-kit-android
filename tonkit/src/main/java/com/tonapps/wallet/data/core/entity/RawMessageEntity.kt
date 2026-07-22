package com.tonapps.wallet.data.core.entity

import android.os.Parcelable
import com.tonapps.blockchain.ton.extensions.isBounceable
import com.tonapps.blockchain.ton.extensions.safeParseCell
import com.tonapps.blockchain.ton.extensions.toTlb
import kotlinx.parcelize.Parcelize
import org.json.JSONObject
import org.ton.block.AddrStd
import org.ton.block.Coins
import org.ton.block.StateInit
import org.ton.cell.Cell
import org.ton.contract.wallet.MessageData
import org.ton.contract.wallet.WalletTransfer
import org.ton.contract.wallet.WalletTransferBuilder
import org.ton.tlb.CellRef

@Parcelize
data class RawMessageEntity(
    val addressValue: String,
    val amount: Long,
    val stateInitValue: String?,
    val payloadValue: String
): Parcelable {

    val address: AddrStd
        get() = AddrStd.parse(addressValue)

    val coins: Coins
        get() = Coins.ofNano(amount)

    val stateInit: StateInit?
        get() = stateInitValue?.toTlb()

    val payload: Cell
        get() = payloadValue.safeParseCell() ?: Cell()

    val walletTransfer: WalletTransfer by lazy {
        val builder = WalletTransferBuilder()
        builder.destination = address
        builder.bounceable = addressValue.isBounceable()
        builder.coins = coins
        val stateInitRef = stateInit?.let { CellRef(it, StateInit) }
        builder.messageData = MessageData.raw(payload, stateInitRef)
        builder.build()
    }

    constructor(json: JSONObject) : this(
        json.getString("address"),
        parseAmount(json.get("amount")),
        json.optString("stateInit"),
        json.optString("payload")
    )

    private companion object {

        private fun parseAmount(value: Any): Long {
            if (value is String) {
                return value.toLong()
            }
            throw IllegalArgumentException("Invalid amount value")
        }
    }

}
