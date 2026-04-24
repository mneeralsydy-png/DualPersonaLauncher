package com.dualpersona.launcher.data.entity

import androidx.room.Entity
import androidx.room.PrimaryKey

/**
 * Records security events such as failed login attempts,
 * intrusion detection triggers, and environment switches.
 */
@Entity(tableName = "security_events")
data class SecurityEventEntity(
    @PrimaryKey(autoGenerate = true)
    val id: Long = 0,

    val eventType: String, // "failed_login", "intrusion_detected", "environment_switch", "self_destruct"
    val environment: String,
    val timestamp: Long = System.currentTimeMillis(),
    val details: String? = null,

    // For intrusion detection — photo file path
    val photoPath: String? = null
)
