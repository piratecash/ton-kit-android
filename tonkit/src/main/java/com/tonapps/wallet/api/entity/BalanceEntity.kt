package com.tonapps.wallet.api.entity

import android.os.Parcelable
import kotlinx.parcelize.Parcelize

@Parcelize
data class BalanceEntity(
    val isTon: Boolean,
    val walletAddress: String
): Parcelable