package com.dualpersona.system.core

import android.annotation.SuppressLint
import android.app.admin.DevicePolicyManager
import android.content.Context
import android.content.pm.UserInfo
import android.os.Build
import android.os.UserHandle
import android.os.UserManager
import com.dualpersona.system.data.PreferencesManager
import com.dualpersona.system.data.SecurityLog
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.lang.reflect.Method

/**
 * SystemUserManager - Manages Android OS multi-user functionality
 *
 * This class interacts directly with Android's UserManager to:
 * - Create and manage a secondary user profile
 * - Switch between User A and User B
 * - Monitor user state changes
 * - Configure user restrictions and policies
 *
 * Requires: MANAGE_USERS permission (system app or Device Owner)
 */
class SystemUserManager(private val context: Context) {

    private val userManager: UserManager =
        context.getSystemService(Context.USER_SERVICE) as UserManager

    private val devicePolicyManager: DevicePolicyManager =
        context.getSystemService(Context.DEVICE_POLICY_SERVICE) as DevicePolicyManager

    private val prefs: PreferencesManager = PreferencesManager(context)

    // ===== User Management =====

    /**
     * Create a secondary user (User B / Space B)
     * This creates a completely separate Android user with:
     * - Independent app installations
     * - Separate storage partition
     * - Own lock screen credential
     * - Isolated contacts, messages, photos
     */
    @SuppressLint("NewApi")
    fun createSecondaryUser(userName: String): Result<UserHandle> = runCatching {
        val userHandle = userManager.createUser(userName, 0)
        if (userHandle == null) {
            SecurityLog.log(context, "FAILED", "create_user", "User creation returned null")
            throw IllegalStateException("Failed to create secondary user")
        }

        prefs.setSecondaryUserHandle(userHandle)
        prefs.setSecondaryUserName(userName)

        SecurityLog.log(context, "SUCCESS", "create_user", "Secondary user created: $userName")
        userHandle
    }

    /**
     * Remove secondary user and all associated data
     */
    @SuppressLint("NewApi")
    fun removeSecondaryUser(): Result<Unit> = runCatching {
        val handle = prefs.getSecondaryUserHandle() ?: return Result.success(Unit)
        val serialNumber = userManager.getSerialNumberForUser(handle)

        if (userManager.removeUser(handle)) {
            prefs.clearSecondaryUser()
            SecurityLog.log(context, "SUCCESS", "remove_user", "Secondary user removed")
        } else {
            throw IllegalStateException("Failed to remove secondary user")
        }
    }

    /**
     * Switch to a specific user profile
     * This is the core mechanism: switching User A ↔ User B
     */
    @SuppressLint("NewApi")
    fun switchUser(userHandle: UserHandle): Result<Unit> = runCatching {
        // Use reflection for switchUser as it's a @SystemApi
        val method: Method = try {
            UserManager::class.java.getMethod(
                "switchUser",
                UserHandle::class.java
            )
        } catch (e: NoSuchMethodException) {
            // Fallback: try with int userId
            val userIdMethod = UserManager::class.java.getMethod(
                "switchUser",
                Int::class.javaPrimitiveType
            )
            userIdMethod.invoke(userManager, getAndroidId(userHandle))
            SecurityLog.log(context, "SUCCESS", "switch_user", "Switched to user id: ${getAndroidId(userHandle)}")
            return@runCatching
        }

        method.invoke(userManager, userHandle)
        SecurityLog.log(context, "SUCCESS", "switch_user", "User switched")
    }

    /**
     * Get list of all users on the device
     */
    fun getAllUsers(): List<UserInfo> {
        return userManager.users
    }

    /**
     * Get current active user info
     */
    fun getCurrentUser(): UserInfo {
        return userManager.userInfoForSerialNumber(
            userManager.currentUserSerialNumber
        ) ?: throw IllegalStateException("Cannot get current user")
    }

    /**
     * Get secondary user info if exists
     */
    fun getSecondaryUserInfo(): UserInfo? {
        val handle = prefs.getSecondaryUserHandle() ?: return null
        val serialNumber = userManager.getSerialNumberForUser(handle)
        return userManager.userInfoForSerialNumber(serialNumber)
    }

    /**
     * Check if a secondary user exists
     */
    fun hasSecondaryUser(): Boolean {
        return getSecondaryUserInfo() != null
    }

    /**
     * Get UserHandle from user ID
     */
    @SuppressLint("NewApi")
    fun getUserHandle(userId: Int): UserHandle {
        return UserHandle.of(userId)
    }

    /**
     * Get user ID from UserHandle
     */
    private fun getAndroidId(handle: UserHandle): Int {
        return try {
            val method = UserHandle::class.java.getDeclaredMethod("getIdentifier")
            method.invoke(handle) as Int
        } catch (e: Exception) {
            -1
        }
    }

    // ===== User Restrictions =====

    /**
     * Apply restrictions to secondary user to maintain isolation
     */
    @SuppressLint("NewApi")
    fun applyUserRestrictions(userHandle: UserHandle) = runCatching {
        val restrictions = arrayOf(
            UserManager.DISALLOW_INSTALL_APPS,
            UserManager.DISALLOW_UNINSTALL_APPS,
            UserManager.DISALLOW_SHARE_LOCATION,
            UserManager.DISALLOW_USB_FILE_TRANSFER,
        )

        // These restrictions are configurable - user can adjust in dashboard
        val currentRestrictions = prefs.getUserRestrictions()
        for (restriction in currentRestrictions) {
            userManager.setUserRestriction(userHandle, restriction, true)
        }
    }

    /**
     * Remove all restrictions from secondary user
     */
    @SuppressLint("NewApi")
    fun removeUserRestrictions(userHandle: UserHandle) = runCatching {
        val restrictionKeys = listOf(
            UserManager.DISALLOW_INSTALL_APPS,
            UserManager.DISALLOW_UNINSTALL_APPS,
            UserManager.DISALLOW_SHARE_LOCATION,
            UserManager.DISALLOW_USB_FILE_TRANSFER,
            UserManager.DISALLOW_DEBUGGING_FEATURES,
            UserManager.DISALLOW_INSTALL_UNKNOWN_SOURCES,
            UserManager.DISALLOW_FACTORY_RESET,
            UserManager.DISALLOW_MODIFY_ACCOUNTS,
        )
        for (key in restrictionKeys) {
            userManager.setUserRestriction(userHandle, key, false)
        }
    }

    // ===== User State =====

    /**
     * Check if the device supports multi-user
     */
    fun isMultiUserSupported(): Boolean {
        return try {
            UserManager::class.java.getMethod("supportsMultipleUsers")
                .invoke(userManager) as? Boolean ?: false
        } catch (e: Exception) {
            // Fallback: check max users
            try {
                val method = UserManager::class.java.getMethod("getMaxSupportedUsers")
                (method.invoke(userManager) as? Int ?: 1) > 1
            } catch (e2: Exception) {
                Build.VERSION.SDK_INT >= Build.VERSION_CODES.LOLLIPOP
            }
        }
    }

    /**
     * Check if current process is running in secondary user space
     */
    @SuppressLint("NewApi")
    fun isSecondaryUser(): Boolean {
        val currentSerial = userManager.currentUserSerialNumber
        val secondaryHandle = prefs.getSecondaryUserHandle() ?: return false
        val secondarySerial = userManager.getSerialNumberForUser(secondaryHandle)
        return currentSerial == secondarySerial
    }

    /**
     * Get the number of users on device
     */
    fun getUserCount(): Int {
        return userManager.users.size
    }

    // ===== Guest/Emergency User =====

    /**
     * Enable guest mode (quick temporary space)
     */
    @SuppressLint("NewApi")
    fun enableGuestSession(): Result<UserHandle> = runCatching {
        // Try to enable system guest
        val method = try {
            UserManager::class.java.getMethod("enableGuest")
        } catch (e: NoSuchMethodException) {
            null
        }

        method?.invoke(userManager)

        // Find guest user
        userManager.users.find { it.isGuest }
            ?.let { getUserHandle(it.id) }
            ?: throw IllegalStateException("Guest user not available")
    }

    suspend fun switchToSecondaryUserAsync() = withContext(Dispatchers.IO) {
        val handle = prefs.getSecondaryUserHandle()
            ?: throw IllegalStateException("No secondary user configured")
        switchUser(handle).getOrThrow()
    }

    suspend fun switchToMainUserAsync() = withContext(Dispatchers.IO) {
        switchUser(getUserHandle(0)).getOrThrow()
    }
}
