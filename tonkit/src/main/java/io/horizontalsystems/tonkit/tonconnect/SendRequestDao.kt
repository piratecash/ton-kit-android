package io.horizontalsystems.tonkit.tonconnect

import androidx.room.Dao
import androidx.room.Delete
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import com.tonapps.wallet.data.core.entity.SendRequestEntity
import kotlinx.coroutines.flow.Flow

@Dao
interface SendRequestDao {
    @Insert(onConflict = OnConflictStrategy.REPLACE)
    fun save(entity: SendRequestEntity)

    @Delete
    fun delete(entity: SendRequestEntity)

    @Query("SELECT * FROM SendRequestEntity ORDER BY id LIMIT 0, 1")
    fun getFirst(): Flow<SendRequestEntity?>

}