package com.mati.tradenotify.data.db

import androidx.room.Dao
import androidx.room.Delete
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import androidx.room.Update
import kotlinx.coroutines.flow.Flow

@Dao
interface RuleDao {

    @Query("SELECT * FROM rules ORDER BY id")
    fun observeAll(): Flow<List<RuleEntity>>

    @Query("SELECT * FROM rules ORDER BY id")
    suspend fun getAll(): List<RuleEntity>

    @Query("SELECT * FROM rules WHERE enabled = 1 ORDER BY id")
    suspend fun getEnabled(): List<RuleEntity>

    @Query("SELECT * FROM rules WHERE id = :id")
    suspend fun getById(id: Long): RuleEntity?

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insert(rule: RuleEntity): Long

    @Update
    suspend fun update(rule: RuleEntity)

    @Delete
    suspend fun delete(rule: RuleEntity)

    @Query("UPDATE rules SET lastFiredAt = :firedAt WHERE id = :id")
    suspend fun markFired(id: Long, firedAt: Long)

    @Query("UPDATE rules SET enabled = :enabled WHERE id = :id")
    suspend fun setEnabled(id: Long, enabled: Boolean)
}

/** One observed channel plus when it was last seen; drives channel discovery. */
data class ChannelSummary(
    val channel: String,
    val packageName: String,
    val lastSeen: Long,
    val messageCount: Int,
)

@Dao
interface EventDao {

    @Insert
    suspend fun insert(event: EventEntity): Long

    @Query("SELECT * FROM events ORDER BY postedAt DESC LIMIT :limit")
    fun observeRecent(limit: Int = 300): Flow<List<EventEntity>>

    @Query("SELECT * FROM events WHERE id = :id")
    suspend fun getById(id: Long): EventEntity?

    @Query(
        """
        SELECT channel, packageName, MAX(postedAt) AS lastSeen, COUNT(*) AS messageCount
        FROM events
        GROUP BY channel, packageName
        ORDER BY lastSeen DESC
        """
    )
    fun observeChannels(): Flow<List<ChannelSummary>>

    /** Most recent sighting from a channel, used by the "nothing seen lately" health warning. */
    @Query("SELECT MAX(postedAt) FROM events WHERE channel = :channel")
    suspend fun lastSeenForChannel(channel: String): Long?

    @Query("DELETE FROM events")
    suspend fun clear()

    /** Keeps the log from growing without bound; called after each insert. */
    @Query("DELETE FROM events WHERE id NOT IN (SELECT id FROM events ORDER BY postedAt DESC LIMIT :keep)")
    suspend fun trimTo(keep: Int)
}
