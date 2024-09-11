package io.horizontalsystems.tonkit.storage

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import io.horizontalsystems.tonkit.Address
import io.horizontalsystems.tonkit.models.Account

@Dao
interface AccountDao {

    @Query("SELECT * FROM Account WHERE address = :address")
    fun getAccount(address: Address) : Account?

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    fun save(account: Account)
}