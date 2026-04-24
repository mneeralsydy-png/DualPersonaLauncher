package com.dualpersona.launcher.utils

/**
 * Application-wide constants for Dual Persona Launcher
 */

// Environment Types
object EnvironmentType {
    const val PRIMARY = "primary"
    const val HIDDEN = "hidden"
    const val EMERGENCY = "emergency"
    const val UNKNOWN = "unknown"
}

// Lock Screen Auth Types
object AuthType {
    const val PIN = "pin"
    const val PATTERN = "pattern"
    const val FINGERPRINT = "fingerprint"
}

// Preferences Keys
object PrefKeys {
    const val IS_SETUP_COMPLETE = "is_setup_complete"
    const val CURRENT_ENVIRONMENT = "current_environment"

    // PINs (encrypted)
    const val PRIMARY_PIN_HASH = "primary_pin_hash"
    const val HIDDEN_PIN_HASH = "hidden_pin_hash"
    const val EMERGENCY_PIN_HASH = "emergency_pin_hash"
    const val PRIMARY_PIN_SALT = "primary_pin_salt"
    const val HIDDEN_PIN_SALT = "hidden_pin_salt"
    const val EMERGENCY_PIN_SALT = "emergency_pin_salt"

    // Pattern hashes
    const val PRIMARY_PATTERN_HASH = "primary_pattern_hash"
    const val HIDDEN_PATTERN_HASH = "hidden_pattern_hash"

    // Security settings
    const val ENABLE_FINGERPRINT = "enable_fingerprint"
    const val ENABLE_STEALTH_MODE = "enable_stealth_mode"
    const val AUTO_LOCK_ENABLED = "auto_lock_enabled"
    const val AUTO_LOCK_DELAY_SECONDS = "auto_lock_delay_seconds"
    const val MAX_FAILED_ATTEMPTS = "max_failed_attempts"
    const val ENABLE_INTRUSION_DETECTION = "enable_intrusion_detection"
    const val ENABLE_SELF_DESTRUCT = "enable_self_destruct"

    // Theme
    const val THEME_PRIMARY_COLOR = "theme_primary_color"
    const val THEME_ACCENT_COLOR = "theme_accent_color"
    const val THEME_DARK_MODE = "theme_dark_mode"
    const val THEME_GRID_COLUMNS = "theme_grid_columns"
    const val THEME_ICON_SIZE = "theme_icon_size"

    // Backup
    const val LAST_BACKUP_TIME = "last_backup_time"
    const val BACKUP_ENCRYPTED = "backup_encrypted"
}

// Security
object SecurityConstants {
    const val AES_KEY_SIZE = 256
    const val AES_TRANSFORMATION = "AES/GCM/NoPadding"
    const val PBKDF2_ITERATIONS = 100000
    const val PBKDF2_KEY_LENGTH = 256
    const val SALT_LENGTH = 32
    const val IV_LENGTH = 12
    const val GCM_TAG_LENGTH = 128

    const val MAX_PIN_LENGTH = 16
    const val MIN_PIN_LENGTH = 4
    const val MAX_PATTERN_LENGTH = 9

    const val DEFAULT_MAX_ATTEMPTS = 5
    const val DEFAULT_AUTO_LOCK_DELAY = 30 // seconds
}

// Intent Extras
object IntentExtras {
    const val ENVIRONMENT_TYPE = "environment_type"
    const val IS_SWITCHING = "is_switching"
    const val REQUEST_CODE = "request_code"
    const val PACKAGE_NAME = "package_name"
    const val APP_INFO = "app_info"
}

// Request Codes
object RequestCodes {
    const val ENABLE_DEVICE_ADMIN = 1001
    const val ENABLE_OVERLAY = 1002
    const val PICK_APP = 1003
    const val PICK_WALLPAPER = 1004
    const val PICK_BACKUP_FILE = 1005
    const val BIOMETRIC_AUTH = 1006
    const val STORAGE_PERMISSION = 1007
    const val NOTIFICATION_PERMISSION = 1008
}

// Notification IDs
object NotificationIds {
    const val LOCK_SCREEN_SERVICE = 2001
    const val SECURITY_ALERT = 2002
    const val FAILED_ATTEMPT = 2003
    const val BACKUP_PROGRESS = 2004
    const val SELF_DESTRUCT = 2005
}

// Database
object DatabaseConstants {
    const val DATABASE_NAME = "dual_persona_db"
    const val DATABASE_VERSION = 1
}
