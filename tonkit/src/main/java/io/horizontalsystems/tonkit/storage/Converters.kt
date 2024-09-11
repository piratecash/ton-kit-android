package io.horizontalsystems.tonkit.storage

import androidx.room.TypeConverter
import io.horizontalsystems.tonkit.Address
import java.math.BigInteger

class Converters {
    @TypeConverter
    fun stringToAddress(value: String?): Address? {
        return value?.let { Address.parse(it) }
    }

    @TypeConverter
    fun addressToString(value: Address?): String? {
        return value?.toRaw()
    }

    @TypeConverter
    fun stringToBigInteger(value: String?): BigInteger? {
        return value?.let { BigInteger(it) }
    }

    @TypeConverter
    fun bigIntegerToString(value: BigInteger?): String? {
        return value?.toString()
    }
}