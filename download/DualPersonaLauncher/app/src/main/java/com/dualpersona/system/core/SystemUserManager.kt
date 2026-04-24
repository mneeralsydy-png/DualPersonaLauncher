package com.dualpersona.system.core

import android.annotation.SuppressLint
import android.content.Context
import android.os.Build
import android.os.Handler
import android.os.Looper
import android.os.UserHandle
import android.os.UserManager
import com.dualpersona.system.data.PreferencesManager
import com.dualpersona.system.data.SecurityLog
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.lang.reflect.Method

class SystemUserManager(private val context: Context) {

    private val userManager: UserManager =
        context.getSystemService(Context.USER_SERVICE) as UserManager

    private val prefs: PreferencesManager = PreferencesManager(context)

    // ===== User Management (using reflection for hidden APIs) =====

    fun createSecondaryUser(userName: String): Result<UserHandle> = runCatching {
        val method: Method = UserManager::class.java.getMethod("createUser", String::class.java, Int::class.javaPrimitiveType)
        val userHandle = method.invoke(userManager, userName, 0) as? UserHandle
            ?: throw IllegalStateException("Failed to create secondary user")

        val serial = getSerialNumberForUser(userHandle)
        prefs.setSecondaryUserHandleId(serial)
        prefs.setSecondaryUserName(userName)

        SecurityLog.log(context, "SUCCESS", "create_user", "Secondary user created: $userName")
        userHandle
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
        return getSecondaryUserInfo() != null
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

    // ===== Reflection Helpers =====

    private fun getSerialNumberForUser(handle: UserHandle): Long {
        return try {
            val method: Method = UserManager::class.java.getMethod("getSerialNumberForUser", UserHandle::class.java)
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
            val method: Method = UserManager::class.java.getMethod("getUserForSerialNumber", Long::class.javaPrimitiveType)
            val handle = method.invoke(userManager, serial) as? UserHandle ?: return -1
            getUserId(handle)
        } catch (e: Exception) { -1 }
    }

    fun getUserCount(): Int {
        return getAllUsers().size
    }

    fun getSecondaryUserHandle(): UserHandle? {
        val serial = prefs.getSecondaryUserHandleId()
        if (serial == -1L) return null
        return try {
            val method: Method = UserManager::class.java.getMethod("getUserForSerialNumber", Long::class.javaPrimitiveType)
            method.invoke(userManager, serial) as? UserHandle
        } catch (e: Exception) { null }
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
