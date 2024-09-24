package io.horizontalsystems.tonkit.models

import io.horizontalsystems.tonkit.Address
import io.tonapi.models.JettonInfo
import io.tonapi.models.JettonPreview

data class Jetton(
    val address: Address,
    val name: String,
    val symbol: String,
    val decimals: Int,
    val image: String?,
    val verification: JettonVerificationType,
) {

    companion object {
        fun fromPreview(jetton: JettonPreview) = Jetton(
            address = Address.parse(jetton.address),
            name = jetton.name,
            symbol = jetton.symbol,
            decimals = jetton.decimals,
            image = jetton.image,
            verification = JettonVerificationType.fromApi(jetton.verification)
        )

        fun fromJettonInfo(jettonInfo: JettonInfo): Jetton {
            val metadata = jettonInfo.metadata

            return Jetton(
                address = Address.parse(metadata.address),
                name = metadata.name,
                symbol = metadata.symbol,
                decimals = metadata.decimals.toInt(),
                image = metadata.image,
                verification = JettonVerificationType.fromApi(jettonInfo.verification)
            )
        }
    }
}

enum class JettonVerificationType(val value: String) {
    WHITELIST("whitelist"),
    BLACKLIST("blacklist"),
    NONE("none"),
    UNKNOWN("unknown");

    companion object {
        fun fromApi(type: io.tonapi.models.JettonVerificationType) = when (type) {
            io.tonapi.models.JettonVerificationType.whitelist -> WHITELIST
            io.tonapi.models.JettonVerificationType.blacklist -> BLACKLIST
            io.tonapi.models.JettonVerificationType.none -> NONE
            io.tonapi.models.JettonVerificationType.unknown_default_open_api -> UNKNOWN
        }
    }
}
