package io.horizontalsystems.tonkit.storage

import androidx.room.TypeConverter
import com.squareup.moshi.adapter
import io.horizontalsystems.tonkit.Address
import io.horizontalsystems.tonkit.models.Action
import io.tonapi.infrastructure.Serializer
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

    @OptIn(ExperimentalStdlibApi::class)
    @TypeConverter
    fun stringToActionList(value: String?): List<Action>? {
        if (value == null) return null
        val adapter = Serializer.moshi.adapter<List<Action>>()

        return adapter.fromJson(value)
    }

    @OptIn(ExperimentalStdlibApi::class)
    @TypeConverter
    fun actionListToString(value: List<Action>?): String? {
        if (value == null) return null
        val adapter = Serializer.moshi.adapter<List<Action>>()

        return adapter.toJson(value)
    }
}