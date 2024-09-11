package io.horizontalsystems.tonkit.storage

import android.content.Context
import androidx.room.Database
import androidx.room.Room
import androidx.room.RoomDatabase
import androidx.room.TypeConverters
import io.horizontalsystems.tonkit.models.Account
import io.horizontalsystems.tonkit.models.JettonBalance

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
