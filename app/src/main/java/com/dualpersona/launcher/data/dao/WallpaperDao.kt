package com.dualpersona.launcher.data.dao

import androidx.room.*
import com.dualpersona.launcher.data.entity.WallpaperEntity
import kotlinx.coroutines.flow.Flow

@Dao
interface WallpaperDao {
    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insert(wallpaper: WallpaperEntity)

    @Query("SELECT * FROM wallpapers WHERE environment = :environment")
    fun getWallpaper(environment: String): Flow<WallpaperEntity?>

    @Query("DELETE FROM wallpapers WHERE environment = :environment")
    suspend fun delete(environment: String)
}
