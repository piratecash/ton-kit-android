package io.horizontalsystems.tonkit.tonconnect

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query

@Dao
interface KeyValueDao {
    fun set(k: String, v: String) {
        save(KeyValue(k, v))
    }

    fun get(k: String) = getByKey(k)?.value

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    fun save(keyValue: KeyValue)


    @Query("SELECT * FROM KeyValue WHERE `key` = :k")
    fun getByKey(k: String): KeyValue?

}
