package io.horizontalsystems.tonkit.tonconnect

import android.content.Context
import androidx.room.Database
import androidx.room.Room
import androidx.room.RoomDatabase
import com.tonapps.wallet.data.tonconnect.entities.DAppEntity

@Database(
    entities = [
        DAppEntity::class
    ],
    version = 1,
)
abstract class TonConnectKitDatabase : RoomDatabase() {
    abstract fun dAppDao(): DAppDao

    companion object {
        fun getInstance(context: Context, name: String): TonConnectKitDatabase {
            return Room.databaseBuilder(context, TonConnectKitDatabase::class.java, name)
                .build()
        }
    }
}
