package io.horizontalsystems.tonkit.storage

import android.util.Log
import androidx.room.Dao
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import androidx.room.RawQuery
import androidx.sqlite.db.SimpleSQLiteQuery
import androidx.sqlite.db.SupportSQLiteQuery
import io.horizontalsystems.tonkit.models.Event
import io.horizontalsystems.tonkit.models.EventSyncState
import io.horizontalsystems.tonkit.models.Tag
import io.horizontalsystems.tonkit.models.TagQuery
import io.horizontalsystems.tonkit.models.TagToken

@Dao
interface EventDao {

    @Query("SELECT * FROM EventSyncState LIMIT 0, 1")
    fun eventSyncState(): EventSyncState?

    fun events(tagQuery: TagQuery, beforeLt: Long?, limit: Int): List<Event> {
        val arguments = mutableListOf<String>()
        val whereConditions = mutableListOf<String>()
        var joinClause = ""

        if (!tagQuery.isEmpty) {
            tagQuery.type?.let { type ->
                whereConditions.add("Tag.type = ?")
                arguments.add(type.name)
            }
            tagQuery.platform?.let { platform ->
                whereConditions.add("Tag.platform = ?")
                arguments.add(platform.name)
            }
            tagQuery.jettonAddress?.let { jettonAddress ->
                whereConditions.add("Tag.jettonAddress = ?")
                arguments.add(jettonAddress.toRaw())
            }
            tagQuery.address?.let { address ->
                whereConditions.add("Tag.addresses LIKE ?")
                arguments.add("%${address.toRaw()}%")
            }

            joinClause = "INNER JOIN tag ON event.id = tag.eventId"
        }

        beforeLt?.let {
            whereConditions.add("event.lt < ?")
            arguments.add(it.toString())
        }

        val limitClause = "LIMIT $limit"
        val orderClause = "ORDER BY event.lt DESC"
        val whereClause = if (whereConditions.size > 0) {
            "WHERE ${whereConditions.joinToString(" AND ")}"
        } else {
            ""
        }

        val sql = """
            SELECT DISTINCT Event.*
            FROM Event
            $joinClause
            $whereClause
            $orderClause
            $limitClause
            """

        Log.e("AAA", "sql: $sql")

        val query = SimpleSQLiteQuery(sql, arguments.toTypedArray())

        return events(query)
    }

    @RawQuery
    fun events(query: SupportSQLiteQuery): List<Event>

    @Query("SELECT * FROM Event WHERE id IN (:ids)")
    fun events(ids: List<String>): List<Event>

    @Query("SELECT * FROM Event ORDER BY lt DESC LIMIT 0, 1")
    fun latestEvent(): Event?

    @Query("SELECT * FROM Event ORDER BY lt ASC LIMIT 0, 1")
    fun oldestEvent(): Event?

    @Query("SELECT platform, jettonAddress FROM Tag WHERE platform IS NOT NULL")
    fun tagTokens(): List<TagToken>

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    fun save(eventSyncState: EventSyncState)

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    fun save(events: List<Event>)

    fun resave(tags: List<Tag>, eventIds: List<String>) {
        deleteTags(eventIds)
        insertTags(tags)
    }

    @Query("DELETE FROM Tag WHERE eventId IN (:eventIds)")
    fun deleteTags(eventIds: List<String>)

    @Insert
    fun insertTags(tags: List<Tag>)
}
