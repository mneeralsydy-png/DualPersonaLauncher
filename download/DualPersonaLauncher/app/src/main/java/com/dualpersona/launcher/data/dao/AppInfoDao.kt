package com.dualpersona.launcher.data.dao

import androidx.room.*
import com.dualpersona.launcher.data.entity.AppInfoEntity
import kotlinx.coroutines.flow.Flow

/**
 * Data access object for app information.
 */
@Dao
interface AppInfoDao {

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insert(app: AppInfoEntity): Long

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertAll(apps: List<AppInfoEntity>)

    @Update
    suspend fun update(app: AppInfoEntity)

    @Delete
    suspend fun delete(app: AppInfoEntity)

    @Query("DELETE FROM apps WHERE packageName = :packageName AND environment = :environment")
    suspend fun deleteByPackageAndEnvironment(packageName: String, environment: String)

    @Query("DELETE FROM apps WHERE environment = :environment")
    suspend fun deleteAllForEnvironment(environment: String)

    @Query("SELECT * FROM apps WHERE environment = :environment AND isHidden = 0 ORDER BY position ASC")
    fun getAppsForEnvironment(environment: String): Flow<List<AppInfoEntity>>

    @Query("SELECT * FROM apps WHERE environment = :environment AND isOnHomeScreen = 1 AND isHidden = 0 ORDER BY position ASC")
    fun getHomeScreenApps(environment: String): Flow<List<AppInfoEntity>>

    @Query("SELECT * FROM apps WHERE environment = :environment AND isHidden = 1 ORDER BY position ASC")
    fun getHiddenApps(environment: String): Flow<List<AppInfoEntity>>

    @Query("SELECT * FROM apps WHERE packageName = :packageName AND environment = :environment")
    suspend fun getApp(packageName: String, environment: String): AppInfoEntity?

    @Query("SELECT * FROM apps WHERE environment = :environment ORDER BY lastUsedTime DESC LIMIT :limit")
    fun getRecentApps(environment: String, limit: Int = 10): Flow<List<AppInfoEntity>>

    @Query("SELECT * FROM apps WHERE environment = :environment ORDER BY usageCount DESC LIMIT :limit")
    fun getMostUsedApps(environment: String, limit: Int = 10): Flow<List<AppInfoEntity>>

    @Query("SELECT COUNT(*) FROM apps WHERE environment = :environment AND isHidden = 0")
    fun getAppCount(environment: String): Flow<Int>

    @Query("UPDATE apps SET lastUsedTime = :timestamp, usageCount = usageCount + 1 WHERE packageName = :packageName AND environment = :environment")
    suspend fun recordAppUsage(packageName: String, environment: String, timestamp: Long = System.currentTimeMillis())

    @Query("UPDATE apps SET position = :position, page = :page WHERE packageName = :packageName AND environment = :environment")
    suspend fun updatePosition(packageName: String, environment: String, position: Int, page: Int)

    @Query("UPDATE apps SET isHidden = :hidden WHERE packageName = :packageName AND environment = :environment")
    suspend fun setHidden(packageName: String, environment: String, hidden: Boolean)

    @Query("UPDATE apps SET isOnHomeScreen = :onHomeScreen WHERE packageName = :packageName AND environment = :environment")
    suspend fun setOnHomeScreen(packageName: String, environment: String, onHomeScreen: Boolean)

    @Query("UPDATE apps SET customLabel = :label WHERE packageName = :packageName AND environment = :environment")
    suspend fun setCustomLabel(packageName: String, environment: String, label: String?)

    @Query("SELECT * FROM apps WHERE environment = :environment AND (appName LIKE '%' || :query || '%' OR packageName LIKE '%' || :query || '%' OR customLabel LIKE '%' || :query || '%') ORDER BY appName ASC")
    fun searchApps(environment: String, query: String): Flow<List<AppInfoEntity>>
}
