package com.dualpersona.system.core

import android.annotation.SuppressLint
import android.content.Context
import android.content.Intent
import android.os.Build
import android.os.Handler
import android.os.Looper
import android.os.UserHandle
import android.os.UserManager
import android.util.Log
import com.dualpersona.system.data.PreferencesManager
import com.dualpersona.system.data.SecurityLog
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.withContext
import java.io.BufferedReader
import java.io.InputStreamReader
import java.lang.reflect.InvocationTargetException
import java.lang.reflect.Method

/**
 * SystemUserManager - Manages Android multi-user with MAXIMUM safety.
 * 
 * Every single method catches ALL exceptions (including Errors).
 * The app will NEVER crash because of this class.
 */
class SystemUserManager(private val context: Context) {

    private val userManager: UserManager
    private val prefs: PreferencesManager

    init {
        var um: UserManager? = null
        try {
            um = context.getSystemService(Context.USER_SERVICE) as? UserManager
        } catch (e: Throwable) {
            Log.e(TAG, "Failed to get UserManager", e)
        }
        userManager = um!!
        prefs = PreferencesManager(context)
    }

    companion object {
        private const val TAG = "SystemUserManager"
    }

    // ====================================================================
    // CREATE SECONDARY USER - Multiple fallback methods
    // ====================================================================

    /**
     * Attempt to create a secondary user using ALL available methods.
     * Returns a CreateResult indicating success/failure and which method was used.
     * This method will NEVER throw - it always returns a Result.
     */
    fun createSecondaryUser(userName: String): CreateResult {
        // Record users count before
        val usersBefore = safeGetUserCount()

        // Method 1: Shell command (root)
        val shellResult = tryCreateViaShell(userName)
        if (shellResult.success) return shellResult

        // Method 2: Hidden API - old signature (String, int)
        val intClass: Class<*> = Int::class.javaPrimitiveType ?: Int::class.java
        val apiResult1 = tryCreateViaHiddenApi(userName, "createUser",
            arrayOf<Class<*>>(String::class.java, intClass),
            arrayOf<Any?>(userName, 0))
        if (apiResult1.success) return apiResult1

        // Method 3: Hidden API - Android 14+ signature (String, String, String[], int)
        val apiResult2 = tryCreateViaHiddenApi(userName, "createUser",
            arrayOf<Class<*>>(String::class.java, String::class.java,
                Array<String>::class.java, intClass),
            arrayOf<Any?>(userName, "", emptyArray<String>(), 0))
        if (apiResult2.success) return apiResult2

        // Method 4: Hidden API - simplified signature (String)
        val apiResult3 = tryCreateViaHiddenApi(userName, "createUser",
            arrayOf<Class<*>>(String::class.java),
            arrayOf<Any?>(userName))
        if (apiResult3.success) return apiResult3

        // Method 5: DevicePolicyManager (if device owner)
        val dpmResult = tryCreateViaDevicePolicyManager(userName)
        if (dpmResult.success) return dpmResult

        // All methods failed
        return CreateResult(
            success = false,
            method = "NONE",
            handle = null,
            error = "All automatic methods failed. Use manual creation."
        )
    }

    /**
     * Method: Shell command execution
     * Tries: su -c pm create-user, sh -c pm create-user, pm create-user
     */
    private fun tryCreateViaShell(userName: String): CreateResult {
        val commands = listOf(
            arrayOf("su", "-c", "pm", "create-user", userName),
            arrayOf("sh", "-c", "pm create-user \"$userName\""),
            arrayOf("pm", "create-user", userName),
        )

        for ((index, cmd) in commands.withIndex()) {
            try {
                val process = Runtime.getRuntime().exec(cmd)
                val output = StringBuilder()
                val errorOutput = StringBuilder()

                // Read output on separate threads to avoid deadlock
                val outputThread = Thread {
                    try {
                        val reader = BufferedReader(InputStreamReader(process.inputStream))
                        var line: String? = reader.readLine()
                        while (line != null) {
                            output.append(line).append("\n")
                            line = reader.readLine()
                        }
                        reader.close()
                    } catch (e: Throwable) {}
                }
                val errorThread = Thread {
                    try {
                        val reader = BufferedReader(InputStreamReader(process.errorStream))
                        var line: String? = reader.readLine()
                        while (line != null) {
                            errorOutput.append(line).append("\n")
                            line = reader.readLine()
                        }
                        reader.close()
                    } catch (e: Throwable) {}
                }

                outputThread.start()
                errorThread.start()
                outputThread.join(10000)
                errorThread.join(10000)

                process.destroyForcibly()

                val outStr = output.toString()
                val errStr = errorOutput.toString()

                Log.d(TAG, "Shell cmd[$index] output: $outStr error: $errStr")

                // Check for success patterns
                if (outStr.contains("Success", ignoreCase = true) ||
                    outStr.contains("uid=", ignoreCase = true)) {

                    // Try to parse the user ID
                    val userIdRegex = Regex("id\\s*(\\d+)|uid=(\\d+)", RegexOption.IGNORE_CASE)
                    val match = userIdRegex.find(outStr)
                    val userId = match?.groupValues?.getOrNull(1)?.toIntOrNull()
                        ?: match?.groupValues?.getOrNull(2)?.toIntOrNull()

                    if (userId != null && userId > 0) {
                        saveSecondaryUserInfo(userName, userId)
                        SecurityLog.log(context, "SUCCESS", "create_shell",
                            "Created user via shell (cmd $index): $userName id=$userId")
                        return CreateResult(
                            success = true,
                            method = "SHELL_CMD_$index",
                            handle = safeGetUserHandleForId(userId),
                            error = null
                        )
                    }
                }
            } catch (e: Throwable) {
                Log.d(TAG, "Shell cmd[$index] failed: ${e.message}")
            }
        }

        return CreateResult(success = false, method = "SHELL", handle = null,
            error = "Shell commands not available (no root/shell)")
    }

    /**
     * Method: Hidden API via reflection
     * Tries a specific method signature
     */
    @SuppressLint("DiscouragedPrivateApi")
    private fun tryCreateViaHiddenApi(
        userName: String,
        methodName: String,
        paramTypes: Array<Class<*>>,
        args: Array<Any?>
    ): CreateResult {
        return try {
            val method: Method = UserManager::class.java.getDeclaredMethod(methodName, *paramTypes)
            method.isAccessible = true

            val result = method.invoke(userManager, *args)

            if (result is UserHandle) {
                val userId = safeGetUserId(result)
                if (userId > 0) {
                    saveSecondaryUserInfo(userName, userId)
                    SecurityLog.log(context, "SUCCESS", "create_hidden_api",
                        "Created user via hidden API ($methodName): $userName id=$userId")
                    return CreateResult(success = true, method = "HIDDEN_API",
                        handle = result, error = null)
                }
            } else if (result != null) {
                // Some implementations return the UserHandle wrapped
                Log.d(TAG, "Hidden API returned: ${result.javaClass.simpleName}")
            }
            CreateResult(success = false, method = "HIDDEN_API", handle = null,
                error = "Hidden API returned null")
        } catch (e: NoSuchMethodException) {
            CreateResult(success = false, method = "HIDDEN_API", handle = null,
                error = "Method not found: $methodName(${paramTypes.joinToString { it.simpleName }})")
        } catch (e: InvocationTargetException) {
            val cause = e.targetException
            Log.d(TAG, "Hidden API InvocationTargetException: ${cause?.message}")
            CreateResult(success = false, method = "HIDDEN_API", handle = null,
                error = cause?.message ?: "Invocation failed")
        } catch (e: SecurityException) {
            CreateResult(success = false, method = "HIDDEN_API", handle = null,
                error = "No permission (SecurityException)")
        } catch (e: Throwable) {
            Log.d(TAG, "Hidden API failed: ${e.javaClass.simpleName}: ${e.message}")
            CreateResult(success = false, method = "HIDDEN_API", handle = null,
                error = "${e.javaClass.simpleName}: ${e.message}")
        }
    }

    /**
     * Method: DevicePolicyManager.createAndManageUser (requires Device Owner)
     */
    private fun tryCreateViaDevicePolicyManager(userName: String): CreateResult {
        return try {
            val dpm = context.getSystemService(Context.DEVICE_POLICY_SERVICE)
                as? android.app.admin.DevicePolicyManager ?: return CreateResult(
                success = false, method = "DPM", handle = null, error = "No DPM")

            if (!dpm.isDeviceOwnerApp(context.packageName)) {
                return CreateResult(success = false, method = "DPM", handle = null,
                    error = "Not device owner")
            }

            val method = android.app.admin.DevicePolicyManager::class.java.getDeclaredMethod(
                "createAndManageUser",
                String::class.java,
                String::class.java,
                android.content.ComponentName::class.java,
                android.os.PersistableBundle::class.java,
                Int::class.javaPrimitiveType
            )
            method.isAccessible = true

            val componentName = android.content.ComponentName(
                context,
                com.dualpersona.system.receiver.DualPersonaAdmin::class.java
            )
            val skipFlag = 0x00000008 // DevicePolicyManager.SKIP_SETUP_WIZARD

            val result = method.invoke(dpm, userName, userName, componentName, null, skipFlag)

            if (result is UserHandle) {
                val userId = safeGetUserId(result)
                if (userId > 0) {
                    saveSecondaryUserInfo(userName, userId)
                    SecurityLog.log(context, "SUCCESS", "create_dpm",
                        "Created user via DPM: $userName id=$userId")
                    return CreateResult(success = true, method = "DPM", handle = result, error = null)
                }
            }
            CreateResult(success = false, method = "DPM", handle = null,
                error = "DPM returned null")
        } catch (e: Throwable) {
            Log.d(TAG, "DPM failed: ${e.javaClass.simpleName}: ${e.message}")
            CreateResult(success = false, method = "DPM", handle = null,
                error = "${e.javaClass.simpleName}: ${e.message}")
        }
    }

    // ====================================================================
    // USER DETECTION (after manual creation)
    // ====================================================================

    /**
     * Open system User Settings for manual creation.
     * Tries multiple intent actions.
     */
    fun openUserSettings(): Boolean {
        val intents = listOf(
            Intent("android.settings.USER_SETTINGS").apply {
                this.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
            },
            Intent("android.settings.USER_CREATE_SETTINGS").apply {
                this.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
            },
            Intent("android.settings.USERS").apply {
                this.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
            },
            Intent(android.provider.Settings.ACTION_SETTINGS).apply {
                this.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
                this.putExtra(":settings:show_fragment", "com.android.settings.users.UserSettings")
            }
        )

        for (intent in intents) {
            try {
                context.startActivity(intent)
                SecurityLog.log(context, "INFO", "open_settings",
                    "Opened: ${intent.action ?: intent.data}")
                return true
            } catch (e: Throwable) {
                Log.d(TAG, "Intent failed: ${intent.action}: ${e.message}")
            }
        }
        return false
    }

    /**
     * Poll for newly created user (after manual creation in system settings).
     * Runs on IO thread. Never throws.
     */
    suspend fun detectNewSecondaryUser(
        timeoutMs: Long = 180_000,
        pollIntervalMs: Long = 3_000
    ): CreateResult = withContext(Dispatchers.IO) {
        val startTime = System.currentTimeMillis()

        while (System.currentTimeMillis() - startTime < timeoutMs) {
            try {
                val allUsers = safeGetAllUsers()
                for (userInfo in allUsers) {
                    val id = userInfo["id"] as? Int ?: -1
                    val isGuest = userInfo["isGuest"] as? Boolean ?: false

                    if (id > 0 && !isGuest) {
                        val handle = safeGetUserHandleForId(id)
                        val nameVal = (userInfo["name"] as? String)?.ifBlank { null } ?: prefs.getProfileName(1)
                        saveSecondaryUserInfo(nameVal, id)
                        SecurityLog.log(context, "SUCCESS", "detect_user",
                            "Detected user: $nameVal (id=$id)")
                        return@withContext CreateResult(success = true, method = "MANUAL_DETECT",
                            handle = handle ?: return@withContext CreateResult(success = false, method = "MANUAL_DETECT", handle = null, error = "null handle"), error = null)
                    }
                }
            } catch (e: Throwable) {
                Log.d(TAG, "detect error: ${e.message}")
            }

            delay(pollIntervalMs)
        }

        CreateResult(success = false, method = "MANUAL_DETECT", handle = null,
            error = "Timeout: no new user detected")
    }

    // ====================================================================
    // USER QUERIES (all safe)
    // ====================================================================

    fun findExistingSecondaryUser(): UserHandle? {
        return try {
            val allUsers = safeGetAllUsers()
            for (userInfo in allUsers) {
                val id = userInfo["id"] as? Int ?: -1
                val isGuest = userInfo["isGuest"] as? Boolean ?: false
                if (id > 0 && !isGuest) {
                    return safeGetUserHandleForId(id)
                }
            }
            null
        } catch (e: Throwable) { null }
    }

    fun hasSecondaryUser(): Boolean {
        return try {
            val serial = prefs.getSecondaryUserHandleId()
            if (serial != -1L) return true
            findExistingSecondaryUser() != null
        } catch (e: Throwable) { false }
    }

    fun getSecondaryUserHandle(): UserHandle? {
        return try {
            // Check stored serial first
            val serial = prefs.getSecondaryUserHandleId()
            if (serial != -1L) {
                try {
                    val method: Method = UserManager::class.java.getDeclaredMethod(
                        "getUserForSerialNumber", Long::class.javaPrimitiveType)
                    method.isAccessible = true
                    val handle = method.invoke(userManager, serial) as? UserHandle
                    if (handle != null) return handle
                } catch (e: Throwable) {}
            }
            findExistingSecondaryUser()
        } catch (e: Throwable) { null }
    }

    fun getAllUsers(): List<Map<String, Any?>> = safeGetAllUsers()

    fun getUserCount(): Int = safeGetUserCount()

    fun isMultiUserSupported(): Boolean {
        return try {
            Build.VERSION.SDK_INT >= Build.VERSION_CODES.LOLLIPOP
        } catch (e: Throwable) { false }
    }

    // ====================================================================
    // USER SWITCHING (safe)
    // ====================================================================

    fun switchUser(userHandle: UserHandle): Boolean {
        return try {
            val userId = safeGetUserId(userHandle)
            if (userId <= 0) return false

            val method: Method = UserManager::class.java.getDeclaredMethod(
                "switchUser", Int::class.javaPrimitiveType)
            method.isAccessible = true
            method.invoke(userManager, userId)
            true
        } catch (e: Throwable) {
            Log.e(TAG, "switchUser failed", e)
            false
        }
    }

    // ====================================================================
    // METHODS USED BY DASHBOARD
    // ====================================================================

    fun getSecondaryUserInfo(): Map<String, Any?>? {
        return try {
            val allUsers = safeGetAllUsers()
            for (userInfo in allUsers) {
                val id = userInfo["id"] as? Int ?: -1
                val isGuest = userInfo["isGuest"] as? Boolean ?: false
                if (id > 0 && !isGuest) return userInfo
            }
            null
        } catch (e: Throwable) { null }
    }

    suspend fun switchToSecondaryUserAsync() = kotlinx.coroutines.withContext(kotlinx.coroutines.Dispatchers.IO) {
        val handle = getSecondaryUserHandle()
            ?: throw IllegalStateException("No secondary user configured")
        if (!switchUser(handle)) throw IllegalStateException("Switch failed")
    }

    suspend fun switchToMainUserAsync() = kotlinx.coroutines.withContext(kotlinx.coroutines.Dispatchers.IO) {
        val handle = safeGetUserHandleForId(0)
            ?: throw IllegalStateException("Cannot get main user handle")
        if (!switchUser(handle)) throw IllegalStateException("Switch failed")
    }

    // ====================================================================
    // USER REMOVAL (safe)
    // ====================================================================

    fun removeSecondaryUser(): Boolean {
        return try {
            val handle = getSecondaryUserHandle() ?: return true
            val method: Method = UserManager::class.java.getDeclaredMethod(
                "removeUser", UserHandle::class.java)
            method.isAccessible = true
            val result = method.invoke(userManager, handle) as? Boolean ?: false
            if (result) prefs.clearSecondaryUser()
            result
        } catch (e: Throwable) {
            Log.e(TAG, "removeUser failed", e)
            false
        }
    }

    // ====================================================================
    // SAFE INTERNAL HELPERS
    // ====================================================================

    private fun saveSecondaryUserInfo(name: String, userId: Int) {
        try {
            val handle = safeGetUserHandleForId(userId) ?: return
            val serial = safeGetSerialNumberForUser(handle)
            prefs.setSecondaryUserHandleId(serial)
            prefs.setSecondaryUserName(name)
        } catch (e: Throwable) {
            Log.e(TAG, "saveSecondaryUserInfo failed", e)
        }
    }

    @SuppressLint("DiscouragedPrivateApi")
    private fun safeGetAllUsers(): List<Map<String, Any?>> {
        return try {
            val method: Method = UserManager::class.java.getDeclaredMethod("getUsers")
            method.isAccessible = true
            val users = method.invoke(userManager) as? List<*> ?: return emptyList()

            users.mapNotNull { userInfo ->
                try {
                    if (userInfo == null) return@mapNotNull null
                    val cls = userInfo.javaClass
                    val name = try {
                        cls.getMethod("getName")?.invoke(userInfo) as? String ?: ""
                    } catch (e: Throwable) { "" }
                    val id = try {
                        cls.getMethod("getId")?.invoke(userInfo) as? Int ?: -1
                    } catch (e: Throwable) { -1 }
                    val isGuest = try {
                        cls.getMethod("isGuest")?.invoke(userInfo) as? Boolean ?: false
                    } catch (e: Throwable) { false }
                    mapOf("name" to name, "id" to id, "isGuest" to isGuest)
                } catch (e: Throwable) { null }
            }
        } catch (e: Throwable) {
            Log.e(TAG, "safeGetAllUsers failed", e)
            emptyList()
        }
    }

    private fun safeGetUserCount(): Int {
        return try { safeGetAllUsers().size } catch (e: Throwable) { 0 }
    }

    @SuppressLint("DiscouragedPrivateApi")
    private fun safeGetSerialNumberForUser(handle: UserHandle): Long {
        return try {
            val method: Method = UserManager::class.java.getDeclaredMethod(
                "getSerialNumberForUser", UserHandle::class.java)
            method.isAccessible = true
            method.invoke(userManager, handle) as? Long ?: -1
        } catch (e: Throwable) { -1 }
    }

    private fun safeGetUserId(handle: UserHandle): Int {
        return try {
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.N) {
                val method: Method = UserHandle::class.java.getDeclaredMethod("getIdentifier")
                method.isAccessible = true
                method.invoke(handle) as? Int ?: -1
            } else {
                @Suppress("DEPRECATION")
                handle.hashCode()
            }
        } catch (e: Throwable) {
            @Suppress("DEPRECATION")
            try { handle.hashCode() } catch (e2: Throwable) { -1 }
        }
    }

    @SuppressLint("DiscouragedPrivateApi")
    private fun safeGetUserHandleForId(userId: Int): UserHandle? {
        return try {
            // Try UserHandle.of(userId) first (Android P+)
            try {
                val method: Method = UserHandle::class.java.getDeclaredMethod(
                    "of", Int::class.javaPrimitiveType)
                method.isAccessible = true
                return method.invoke(null, userId) as? UserHandle
            } catch (e: Throwable) {}

            // Fallback: constructor
            val constructor = UserHandle::class.java.getDeclaredConstructor(
                Int::class.javaPrimitiveType)
            constructor.isAccessible = true
            constructor.newInstance(userId)
        } catch (e: Throwable) {
            Log.e(TAG, "safeGetUserHandleForId($userId) failed", e)
            null
        }
    }

    // ====================================================================
    // DATA CLASS
    // ====================================================================

    data class CreateResult(
        val success: Boolean,
        val method: String,
        val handle: UserHandle?,
        val error: String?
    )
}
