package com.tonapps.extensions

import org.ton.block.AddrStd

fun String.toRawAddress(): String {
    if (this.contains(":")) {
        return this
    }
    return try {
        AddrStd(this).toString(userFriendly = false).lowercase()
    } catch (e: Exception) {
        this
    }
}

fun String.equalsAddress(other: String): Boolean {
    return try {
        toRawAddress().equals(other.toRawAddress(), ignoreCase = true)
    } catch (e: Throwable) {
        false
    }
}