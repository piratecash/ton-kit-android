package io.horizontalsystems.tonkit.models

import io.horizontalsystems.tonkit.Address

data class TagToken(
    val platform: Tag.Platform,
    val jettonAddress: Address?,
)
