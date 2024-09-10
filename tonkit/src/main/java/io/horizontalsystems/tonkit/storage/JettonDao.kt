package io.horizontalsystems.tonkit.storage

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import io.horizontalsystems.tonkit.models.JettonBalance

@Dao
interface JettonDao {
    @Query("SELECT * FROM JettonBalance")
    fun getJettonBalances(): List<JettonBalance>

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    fun insertAll(jettonBalances: List<JettonBalance>)

}
