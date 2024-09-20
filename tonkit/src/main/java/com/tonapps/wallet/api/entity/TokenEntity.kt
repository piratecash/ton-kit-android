package com.tonapps.wallet.api.entity

import android.os.Parcelable
import io.tonapi.models.JettonPreview
import io.tonapi.models.JettonVerificationType
import kotlinx.parcelize.Parcelize

@Parcelize
data class TokenEntity(
    val address: String,
    val name: String,
    val symbol: String,
    val decimals: Int,
    val verification: Verification
): Parcelable {

    enum class Verification {
        whitelist, blacklist, none
    }

    companion object {
        val TON = TokenEntity(
            address = "TON",
            name = "Toncoin",
            symbol = "TON",
            decimals = 9,
            verification = Verification.whitelist
        )

        val USDT = TokenEntity(
            address = "0:b113a994b5024a16719f69139328eb759596c38a25f59028b146fecdc3621dfe",
            name = "Tether USD",
            symbol = "USD₮",
            decimals = 6,
            verification = Verification.whitelist
        )

        private fun convertVerification(verification: JettonVerificationType): Verification {
            return when (verification) {
                JettonVerificationType.whitelist -> Verification.whitelist
                JettonVerificationType.blacklist -> Verification.blacklist
                else -> Verification.none
            }
        }
    }

    val isTon: Boolean
        get() = address == TON.address

    val isUsdt: Boolean
        get() = address == USDT.address

    constructor(jetton: JettonPreview) : this(
        address = jetton.address,
        name = jetton.name,
        symbol = jetton.symbol,
        decimals = jetton.decimals,
        verification = convertVerification(jetton.verification)
    )
}