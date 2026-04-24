package com.dualpersona.launcher.data.entity

import androidx.room.Entity
import androidx.room.PrimaryKey

/**
 * Represents an application in the user's app drawer for a specific environment.
 * Each environment can have different sets of apps.
 */
@Entity(
    tableName = "apps",
    indices = [
        androidx.room.Index(value = ["packageName", "environment"], unique = true)
    ]
)
data class AppInfoEntity(
    @PrimaryKey(autoGenerate = true)
    val id: Long = 0,

    val packageName: String,
    val appName: String,
    val environment: String, // "primary", "hidden", "emergency"

    // Position on home screen / drawer
    val position: Int = 0,
    val page: Int = 0, // Which page (0 = home screen)

    // Display settings
    val isHidden: Boolean = false,
    val isOnHomeScreen: Boolean = false,

    // Custom label (if renamed by user)
    val customLabel: String? = null,

    // Icon data (cached)
    val iconData: ByteArray? = null,

    // Metadata
    val installTime: Long = System.currentTimeMillis(),
    val lastUsedTime: Long = 0L,
    val usageCount: Int = 0
) {
    override fun equals(other: Any?): Boolean {
        if (this === other) return true
        if (other !is AppInfoEntity) return false
        return id == other.id
    }

    override fun hashCode(): Int = id.toInt()
}
