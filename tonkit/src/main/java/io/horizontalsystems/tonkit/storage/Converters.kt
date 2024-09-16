package io.horizontalsystems.tonkit.storage

import androidx.room.TypeConverter
import com.squareup.moshi.FromJson
import com.squareup.moshi.Moshi
import com.squareup.moshi.ToJson
import com.squareup.moshi.adapter
import com.squareup.moshi.kotlin.reflect.KotlinJsonAdapterFactory
import io.horizontalsystems.tonkit.Address
import io.horizontalsystems.tonkit.models.Action
import io.tonapi.infrastructure.BigDecimalAdapter
import io.tonapi.infrastructure.BigIntegerAdapter
import io.tonapi.infrastructure.ByteArrayAdapter
import io.tonapi.infrastructure.LocalDateAdapter
import io.tonapi.infrastructure.LocalDateTimeAdapter
import io.tonapi.infrastructure.OffsetDateTimeAdapter
import io.tonapi.infrastructure.URIAdapter
import io.tonapi.infrastructure.UUIDAdapter
import java.math.BigInteger

class Converters {
    private val moshi = Moshi.Builder()
        .add(AddressAdapter())
        .add(OffsetDateTimeAdapter())
        .add(LocalDateTimeAdapter())
        .add(LocalDateAdapter())
        .add(UUIDAdapter())
        .add(ByteArrayAdapter())
        .add(URIAdapter())
        .add(BigDecimalAdapter())
        .add(BigIntegerAdapter())
        .add(KotlinJsonAdapterFactory())
        .build()

    @TypeConverter
    fun stringToAddress(value: String?): Address? {
        return value?.let { Address.parse(it) }
    }

    @TypeConverter
    fun addressToString(value: Address?): String? {
        return value?.toRaw()
    }

    @TypeConverter
    fun stringToAddressList(value: String?): List<Address>? {
        return value?.split("|")?.mapNotNull { stringToAddress(it) }
    }

    @TypeConverter
    fun addressListToString(value: List<Address>?): String? {
        return value?.map { addressToString(it) }?.joinToString("|")
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
        val adapter = moshi.adapter<List<Action>>()

        return adapter.fromJson(value)
    }

    @OptIn(ExperimentalStdlibApi::class)
    @TypeConverter
    fun actionListToString(value: List<Action>?): String? {
        if (value == null) return null
        val adapter = moshi.adapter<List<Action>>()

        return adapter.toJson(value)
    }
}

class AddressAdapter {
    @ToJson
    fun toJson(value: Address) = value.toRaw()

    @FromJson
    fun fromJson(value: String) = Address.parse(value)
}
