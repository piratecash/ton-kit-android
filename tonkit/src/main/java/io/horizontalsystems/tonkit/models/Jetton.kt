package io.horizontalsystems.tonkit.models

import io.horizontalsystems.tonkit.Address
import io.swagger.client.models.JettonInfo
import io.swagger.client.models.JettonPreview

data class Jetton(
    val address: Address,
    val name: String,
    val symbol: String,
    val decimals: Int,
    val image: String?,
    val verification: JettonVerificationType,
) {

    companion object {
        fun fromPreview(jetton: JettonPreview): Jetton {
            TODO("Not yet implemented")
        }
        fun fromJettonInfo(jettonInfo: JettonInfo): Jetton {
            TODO("Not yet implemented")
        }
    }
}

enum class JettonVerificationType(val value: kotlin.String){
    WHITELIST("whitelist"),
    BLACKLIST("blacklist"),
    NONE("none");
}
