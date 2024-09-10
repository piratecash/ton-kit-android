package io.horizontalsystems.tonkit.storage

import android.content.Context
import androidx.room.Database
import androidx.room.Room
import androidx.room.RoomDatabase
import androidx.room.TypeConverter
import androidx.room.TypeConverters
import io.horizontalsystems.tonkit.Address
import io.horizontalsystems.tonkit.models.Account
import io.horizontalsystems.tonkit.models.JettonBalance
import java.math.BigInteger

@Database(
    entities = [Account::class, JettonBalance::class],
    version = 1,
)
@TypeConverters(Converters::class)
abstract class KitDatabase : RoomDatabase() {
    abstract fun accountDao(): AccountDao
    abstract fun jettonDao(): JettonDao

    companion object {
        fun getInstance(context: Context, name: String): KitDatabase {
            return Room
                .databaseBuilder(context, KitDatabase::class.java, name)
                .allowMainThreadQueries()
                .build()
        }
    }
}

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