package io.tonapi.models

import com.squareup.moshi.Json

// Hand-written, not generated: every field is nullable and currency_type is a String,
// because Moshi throws on a missing non-null field or an unknown enum value and that fails the whole events page.
data class LiquidityDepositAction(

    @Json(name = "protocol")
    val protocol: Protocol? = null,

    @Json(name = "from")
    val from: AccountAddress? = null,

    @Json(name = "tokens")
    val tokens: kotlin.collections.List<VaultDepositInfo>? = null

) {

    data class Protocol(

        @Json(name = "name")
        val name: kotlin.String? = null

    )

    data class VaultDepositInfo(

        @Json(name = "price")
        val price: Price? = null,

        @Json(name = "vault")
        val vault: kotlin.String? = null

    )
}
