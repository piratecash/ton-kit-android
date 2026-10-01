package io.horizontalsystems.tonkit.tonconnect

import androidx.room.Dao
import androidx.room.Delete
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import androidx.room.Transaction
import com.tonapps.wallet.data.tonconnect.entities.DAppEntity
import kotlinx.coroutines.flow.Flow

@Dao
interface DAppDao {

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun save(dApp: DAppEntity)

    @Query("SELECT * FROM DAppEntity")
    fun getAllFlow(): Flow<List<DAppEntity>>

    @Delete
    suspend fun delete(dApp: DAppEntity)

    @Transaction
    suspend fun deleteAllExcept(walletIds: Collection<String>) {
        deleteSendRequestsOfDAppsExcept(walletIds)
        deleteDAppsExcept(walletIds)
    }

    // SendRequestEntity.dAppId is DAppEntity.uniqueId.
    @Query("DELETE FROM SendRequestEntity WHERE dAppId IN (SELECT walletId || ':' || url FROM DAppEntity WHERE walletId NOT IN (:walletIds))")
    suspend fun deleteSendRequestsOfDAppsExcept(walletIds: Collection<String>)

    @Query("DELETE FROM DAppEntity WHERE walletId NOT IN (:walletIds)")
    suspend fun deleteDAppsExcept(walletIds: Collection<String>)

}
