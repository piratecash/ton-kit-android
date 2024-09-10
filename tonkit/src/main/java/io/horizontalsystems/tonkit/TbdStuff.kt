package io.horizontalsystems.tonkit

data class Address(val raw: String) {
    fun toRaw() = raw

    companion object {
        fun parse(addressStr: String): Address {
            return Address(addressStr)
        }
    }
}