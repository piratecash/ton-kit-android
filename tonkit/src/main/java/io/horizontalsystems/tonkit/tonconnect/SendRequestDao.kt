package io.horizontalsystems.tonkit.tonconnect

import androidx.room.Dao
import androidx.room.Delete
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import com.tonapps.wallet.data.core.entity.SendRequestEntity

@Dao
interface SendRequestDao {
    @Insert(onConflict = OnConflictStrategy.REPLACE)
    fun save(entity: SendRequestEntity)

    @Delete
    fun delete(entity: SendRequestEntity)

}