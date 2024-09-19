package io.horizontalsystems.tonkit

import org.ton.block.AddrStd

class Address(private val addrStd: AddrStd) {
    fun toRaw() = addrStd.toString(userFriendly = false).lowercase()
    fun toUserFriendly(bounceable: Boolean) = addrStd.toString(userFriendly = true, bounceable = bounceable)

    override fun equals(other: Any?): Boolean {
        if (other !is Address) return false
        return toRaw() == other.toRaw()
    }

    override fun hashCode() = addrStd.hashCode()

    companion object {
        fun parse(addressStr: String): Address {
            return Address(AddrStd(addressStr))
        }
    }
}

class FriendlyAddress(val addrStd: AddrStd, val isBounceable: Boolean) {

    companion object {
        fun parse(addressStr: String, bounceable: Boolean = false): FriendlyAddress {
            return FriendlyAddress(AddrStd(addressStr), bounceable)
        }
    }
}
