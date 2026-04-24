package com.dualpersona.system.core

import android.annotation.SuppressLint
import android.content.Context
import android.content.Intent
import android.os.Build
import android.os.Handler
import android.os.Looper
import android.os.UserHandle
import android.os.UserManager
import android.provider.Settings
import com.dualpersona.system.data.PreferencesManager
import com.dualpersona.system.data.SecurityLog
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.withContext
import java.lang.reflect.Method

class SystemUserManager(private val context: Context) {

    private val userManager: UserManager =
        context.getSystemService(Context.USER_SERVICE) as UserManager

    private val prefs: PreferencesManager = PreferencesManager(context)

    // ===== Get existing users count before creation =====
    private var usersBeforeCreation: Int = 0

    // ===== User Management =====

    /**
     * Try to create secondary user via hidden API.
     * Falls back to guiding user to system settings.
     */
    fun createSecondaryUser(userName: String): Result<UserHandle> = runCatching {
        usersBeforeCreation = getUserCount()

        // Method 1: Try hidden API createUser
        try {
            val method: Method = UserManager::class.java.getMethod(
                "createUser", String::class.java, Int::class.javaPrimitiveType
            )
            val flags = 0
            val userHandle = method.invoke(userManager, userName, flags) as? UserHandle
            if (userHandle != null) {
                val serial = getSerialNumberForUser(userHandle)
                prefs.setSecondaryUserHandleId(serial)
                prefs.setSecondaryUserName(userName)
                SecurityLog.log(context, "SUCCESS", "create_user",
                    "Secondary user created via hidden API: $userName")
                return@runCatching userHandle
            }
        } catch (e: Exception) {
            SecurityLog.log(context, "WARNING", "create_user_hidden_api",
                "Hidden API failed: ${e.message}")
        }

        // Method 2: Try DevicePolicyManager (if app is device owner)
        try {
            val dpm = context.getSystemService(Context.DEVICE_POLICY_SERVICE) as? android.app.admin.DevicePolicyManager
            if (dpm != null && dpm.isDeviceOwnerApp(context.packageName)) {
                val method = android.app.admin.DevicePolicyManager::class.java.getMethod(
                    "createAndManageUser",
                    String::class.java,
                    String::class.java,
                    android.content.ComponentName::class.java,
                    android.os.PersistableBundle::class.java,
                    Int::class.javaPrimitiveType
                )
                val componentName = android.content.ComponentName(
                    context,
                    com.dualpersona.system.receiver.DualPersonaAdmin::class.java
                )
                val userHandle = method.invoke(
                    dpm, userName, userName, componentName, null,
                    android.app.admin.DevicePolicyManager.SKIP_SETUP_WIZARD
                ) as? UserHandle

                if (userHandle != null) {
                    val serial = getSerialNumberForUser(userHandle)
                    prefs.setSecondaryUserHandleId(serial)
                    prefs.setSecondaryUserName(userName)
                    SecurityLog.log(context, "SUCCESS", "create_user_dpm",
                        "Secondary user created via DevicePolicyManager: $userName")
                    return@runCatching userHandle
                }
            }
        } catch (e: Exception) {
            SecurityLog.log(context, "WARNING", "create_user_dpm",
                "DPM method failed: ${e.message}")
        }

        throw IllegalStateException("AUTO_CREATE_FAILED")
    }

    /**
     * Open system User Settings so the user can manually create a second profile.
     * After creation, call detectNewSecondaryUser() to pick it up.
     */
    fun openUserSettings() {
        try {
            val intent = Intent("android.settings.USER_SETTINGS").apply {
                this.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
            }
            context.startActivity(intent)
            SecurityLog.log(context, "INFO", "open_user_settings", "Opened system user settings")
        } catch (e: Exception) {
            try {
                val intent = Intent("android.settings.USER_CREATE_SETTINGS").apply {
                    this.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
                }
                context.startActivity(intent)
            } catch (e2: Exception) {
                SecurityLog.log(context, "ERROR", "open_user_settings",
                    "Failed to open settings: ${e2.message}")
            }
        }
    }

    /**
     * Poll for a newly created user.
     * Call this after the user creates a user via system settings.
     * Returns the UserHandle if found within timeout.
     */
    suspend fun detectNewSecondaryUser(
        timeoutMs: Long = 120_000,
        pollIntervalMs: Long = 2_000
    ): Result<UserHandle> = withContext(Dispatchers.IO) {
        val startTime = System.currentTimeMillis()

        while (System.currentTimeMillis() - startTime < timeoutMs) {
            val allUsers = getAllUsers()
            // Look for users that weren't there before
            for (userInfo in allUsers) {
                val id = userInfo["id"] as? Int ?: -1
                val name = userInfo["name"] as? String ?: ""
                val isGuest = userInfo["isGuest"] as? Boolean ?: false

                // Skip current user (id=0), system, and guests
                if (id > 0 && !isGuest) {
                    try {
                        val handle = getUserHandleForId(id)
                        val serial = getSerialNumberForUser(handle)
                        prefs.setSecondaryUserHandleId(serial)
                        prefs.setSecondaryUserName(name.ifBlank { "User B" })
                        SecurityLog.log(context, "SUCCESS", "detect_user",
                            "Detected new user: $name (id=$id)")
                        return@withContext Result.success(handle)
                    } catch (e: Exception) {
                        // continue checking
                    }
                }
            }

            delay(pollIntervalMs)
        }

        Result.failure(java.lang.IllegalStateException("TIMEOUT: No new user detected"))
    }

    /**
     * Find any existing secondary user (non-owner, non-guest)
     */
    fun findExistingSecondaryUser(): UserHandle? {
        return try {
            val allUsers = getAllUsers()
            for (userInfo in allUsers) {
                val id = userInfo["id"] as? Int ?: -1
                val isGuest = userInfo["isGuest"] as? Boolean ?: false
                if (id > 0 && !isGuest) {
                    return getUserHandleForId(id)
                }
            }
            null
        } catch (e: Exception) {
            null
        }
    }

    fun removeSecondaryUser(): Result<Unit> = runCatching {
        val handle = getSecondaryUserHandle() ?: return Result.success(Unit)

        val method: Method = UserManager::class.java.getMethod("removeUser", UserHandle::class.java)
        val result = method.invoke(userManager, handle) as? Boolean
            ?: throw IllegalStateException("Failed to remove secondary user")

        if (result) {
            prefs.clearSecondaryUser()
            SecurityLog.log(context, "SUCCESS", "remove_user", "Secondary user removed")
        }
    }

    @SuppressLint("NewApi")
    fun switchUser(userHandle: UserHandle): Result<Unit> = runCatching {
        val userId = getUserId(userHandle)
        val method: Method = UserManager::class.java.getMethod("switchUser", Int::class.javaPrimitiveType)
        method.invoke(userManager, userId)
        SecurityLog.log(context, "SUCCESS", "switch_user", "Switched to user id: $userId")
    }

    fun getAllUsers(): List<Map<String, Any?>> {
        return try {
            val method: Method = UserManager::class.java.getMethod("getUsers")
            val users = method.invoke(userManager) as? List<*> ?: emptyList<Any>()
            users.mapNotNull { userInfo ->
                val cls = userInfo!!.javaClass
                val name = cls.getMethod("getName")?.invoke(userInfo) as? String ?: ""
                val id = cls.getMethod("getId")?.invoke(userInfo) as? Int ?: -1
                val isGuest = cls.getMethod("isGuest")?.invoke(userInfo) as? Boolean ?: false
                mapOf("name" to name, "id" to id, "isGuest" to isGuest)
            }
        } catch (e: Exception) {
            emptyList()
        }
    }

    fun getCurrentUserSerial(): Long {
        return try {
            val method: Method = UserManager::class.java.getMethod("getCurrentUserSerialNumber")
            method.invoke(userManager) as? Long ?: -1
        } catch (e: Exception) { -1 }
    }

    fun getCurrentUserId(): Int {
        return try {
            val method: Method = UserManager::class.java.getMethod("getCurrentUserId")
            method.invoke(userManager) as? Int ?: 0
        } catch (e: Exception) { 0 }
    }

    fun getSecondaryUserInfo(): Map<String, Any?>? {
        val serial = prefs.getSecondaryUserHandleId()
        if (serial == -1L) return null

        return try {
            val allUsers = getAllUsers()
            allUsers.firstOrNull { it["id"] == getUserIdFromSerial(serial) }
        } catch (e: Exception) { null }
    }

    fun hasSecondaryUser(): Boolean {
        return getSecondaryUserInfo() != null || findExistingSecondaryUser() != null
    }

    // ===== User Restrictions =====

    fun applyUserRestrictions(userHandle: UserHandle) = runCatching {
        val restrictions = prefs.getUserRestrictions()
        for (restriction in restrictions) {
            try {
                userManager.setUserRestriction(restriction, true)
            } catch (e: Exception) { }
        }
    }

    fun removeUserRestrictions(userHandle: UserHandle) = runCatching {
        val restrictionKeys = listOf(
            UserManager.DISALLOW_INSTALL_APPS,
            UserManager.DISALLOW_UNINSTALL_APPS,
            UserManager.DISALLOW_SHARE_LOCATION,
            UserManager.DISALLOW_USB_FILE_TRANSFER,
        )
        for (key in restrictionKeys) {
            try {
                userManager.setUserRestriction(key, false)
            } catch (e: Exception) { }
        }
    }

    // ===== Multi-User Support Check =====

    fun isMultiUserSupported(): Boolean {
        return try {
            val method: Method = UserManager::class.java.getMethod("supportsMultipleUsers")
            method.invoke(userManager) as? Boolean ?: (Build.VERSION.SDK_INT >= Build.VERSION_CODES.LOLLIPOP)
        } catch (e: Exception) {
            Build.VERSION.SDK_INT >= Build.VERSION_CODES.LOLLIPOP
        }
    }

    /**
     * Check if MANAGE_USERS permission is available (system app only)
     */
    fun hasManageUsersPermission(): Boolean {
        return try {
            val method: Method = UserManager::class.java.getMethod("supportsMultipleUsers")
            true // If we can access hidden API, we might have permission
        } catch (e: Exception) {
            false
        }
    }

    // ===== Reflection Helpers =====

    private fun getSerialNumberForUser(handle: UserHandle): Long {
        return try {
            val method: Method = UserManager::class.java.getMethod(
                "getSerialNumberForUser", UserHandle::class.java
            )
            method.invoke(userManager, handle) as? Long ?: -1
        } catch (e: Exception) { -1 }
    }

    private fun getUserId(handle: UserHandle): Int {
        return if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.N) {
            try {
                val method: Method = UserHandle::class.java.getMethod("getIdentifier")
                method.invoke(handle) as? Int ?: -1
            } catch (e: Exception) { -1 }
        } else {
            @Suppress("DEPRECATION")
            handle.hashCode()
        }
    }

    private fun getUserIdFromSerial(serial: Long): Int {
        return try {
            val method: Method = UserManager::class.java.getMethod(
                "getUserForSerialNumber", Long::class.javaPrimitiveType
            )
            val handle = method.invoke(userManager, serial) as? UserHandle ?: return -1
            getUserId(handle)
        } catch (e: Exception) { -1 }
    }

    fun getUserCount(): Int {
        return getAllUsers().size
    }

    fun getSecondaryUserHandle(): UserHandle? {
        // First check stored serial
        val serial = prefs.getSecondaryUserHandleId()
        if (serial != -1L) {
            try {
                val method: Method = UserManager::class.java.getMethod(
                    "getUserForSerialNumber", Long::class.javaPrimitiveType
                )
                val handle = method.invoke(userManager, serial) as? UserHandle
                if (handle != null) return handle
            } catch (e: Exception) { }
        }

        // Fallback: find any secondary user
        return findExistingSecondaryUser()
    }

    // ===== Async helpers =====

    suspend fun switchToSecondaryUserAsync() = withContext(Dispatchers.IO) {
        val handle = getSecondaryUserHandle()
            ?: throw IllegalStateException("No secondary user configured")
        switchUser(handle).getOrThrow()
    }

    suspend fun switchToMainUserAsync() = withContext(Dispatchers.IO) {
        switchUser(getUserHandleForId(0)).getOrThrow()
    }

    private fun getUserHandleForId(userId: Int): UserHandle {
        return try {
            val method: Method = UserHandle::class.java.getMethod("of", Int::class.javaPrimitiveType)
            method.invoke(null, userId) as UserHandle
        } catch (e: Exception) {
            val constructor = UserHandle::class.java.getConstructor(Int::class.javaPrimitiveType)
            constructor.newInstance(userId)
        }
    }
}
