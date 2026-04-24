package com.dualpersona.launcher.data.dao

import androidx.room.*
import com.dualpersona.launcher.data.entity.SecurityEventEntity
import kotlinx.coroutines.flow.Flow

@Dao
interface SecurityEventDao {
    @Insert
    suspend fun insert(event: SecurityEventEntity): Long

    @Query("SELECT * FROM security_events ORDER BY timestamp DESC LIMIT :limit")
    fun getRecentEvents(limit: Int = 50): Flow<List<SecurityEventEntity>>

    @Query("SELECT * FROM security_events WHERE environment = :environment ORDER BY timestamp DESC LIMIT :limit")
    fun getEventsForEnvironment(environment: String, limit: Int = 50): Flow<List<SecurityEventEntity>>

    @Query("SELECT * FROM security_events WHERE eventType = :eventType ORDER BY timestamp DESC LIMIT :limit")
    fun getEventsByType(eventType: String, limit: Int = 50): Flow<List<SecurityEventEntity>>

    @Query("SELECT COUNT(*) FROM security_events WHERE eventType = 'failed_login' AND timestamp > :since")
    suspend fun getFailedLoginCountSince(since: Long): Int

    @Query("DELETE FROM security_events WHERE timestamp < :before")
    suspend fun deleteEventsBefore(before: Long)
}
