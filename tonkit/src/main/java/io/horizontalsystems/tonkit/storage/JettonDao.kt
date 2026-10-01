package io.horizontalsystems.tonkit.storage

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import androidx.room.Transaction
import io.horizontalsystems.tonkit.models.JettonBalance

@Dao
interface JettonDao {
    @Query("SELECT * FROM JettonBalance")
    suspend fun getJettonBalances(): List<JettonBalance>

    @Transaction
    suspend fun replaceAll(jettonBalances: List<JettonBalance>) {
        deleteAll()
        insertAll(jettonBalances)
    }

    @Query("DELETE FROM JETTONBALANCE")
    suspend fun deleteAll()

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertAll(jettonBalances: List<JettonBalance>)

}
