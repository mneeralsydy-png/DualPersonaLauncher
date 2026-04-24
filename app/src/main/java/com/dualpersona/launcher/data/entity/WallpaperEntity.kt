package com.dualpersona.launcher.data.entity

import androidx.room.Entity
import androidx.room.PrimaryKey

/**
 * Stores wallpaper configuration per environment.
 */
@Entity(tableName = "wallpapers")
data class WallpaperEntity(
    @PrimaryKey
    val environment: String, // "primary", "hidden", "emergency"

    val wallpaperUri: String,
    val blurLevel: Float = 0f,
    val brightness: Float = 1f,
    val isLiveWallpaper: Boolean = false,
    val timestamp: Long = System.currentTimeMillis()
)
