package io.horizontalsystems.tonkit.tonconnect

import androidx.room.Dao
import androidx.room.Delete
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import com.tonapps.wallet.data.tonconnect.entities.DAppEntity
import kotlinx.coroutines.flow.Flow

@Dao
interface DAppDao {

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    fun save(dApp: DAppEntity)

    @Query("SELECT * FROM DAppEntity")
    fun getAllFlow(): Flow<List<DAppEntity>>

    @Delete
    fun delete(dApp: DAppEntity)

}
