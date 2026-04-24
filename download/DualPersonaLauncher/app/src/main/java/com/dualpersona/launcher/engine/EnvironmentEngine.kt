package com.dualpersona.launcher.engine

import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import com.dualpersona.launcher.DualPersonaApp
import com.dualpersona.launcher.activities.LauncherHomeActivity
import com.dualpersona.launcher.activities.LockScreenActivity
import com.dualpersona.launcher.data.dao.AppInfoDao
import com.dualpersona.launcher.data.entity.AppInfoEntity
import com.dualpersona.launcher.utils.EnvironmentType
import com.dualpersona.launcher.utils.PreferencesManager
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch

/**
 * Core engine managing the dual environment system.
 *
 * Responsibilities:
 * - Track active environment (primary / hidden / emergency)
 * - Switch between environments
 * - Initialize and populate environments with apps
 * - Manage per-environment state
 */
class EnvironmentEngine(private val context: Context) {

    private val preferencesManager = PreferencesManager(context)
    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.Main)
    private val packageManager = context.packageManager

    private val appDao: AppInfoDao by lazy {
        DualPersonaApp.getInstance().database.appInfoDao()
    }

    // Current active environment state
    private val _activeEnvironment = MutableStateFlow(EnvironmentType.PRIMARY)
    val activeEnvironment: StateFlow<String> = _activeEnvironment.asStateFlow()

    private val _isTransitioning = MutableStateFlow(false)
    val isTransitioning: StateFlow<Boolean> = _isTransitioning.asStateFlow()

    init {
        // Restore last environment
        _activeEnvironment.value = preferencesManager.currentEnvironment
    }

    /**
     * Switch to the specified environment.
     * Triggers lock screen before switching.
     */
    fun switchToEnvironment(environment: String, requireAuth: Boolean = true) {
        if (environment == _activeEnvironment.value && !requireAuth) return

        scope.launch {
            _isTransitioning.value = true

            // Log the switch
            logSecurityEvent("environment_switch", environment)

            if (requireAuth) {
                // Show lock screen to authenticate for the new environment
                val intent = Intent(context, LockScreenActivity::class.java).apply {
                    flags = Intent.FLAG_ACTIVITY_NEW_TASK or
                            Intent.FLAG_ACTIVITY_CLEAR_TASK or
                            Intent.FLAG_ACTIVITY_EXCLUDE_FROM_RECENTS
                    putExtra("target_environment", environment)
                    putExtra("is_switching", true)
                }
                context.startActivity(intent)
            } else {
                applyEnvironment(environment)
            }

            _isTransitioning.value = false
        }
    }

    /**
     * Apply the environment — update state, preferences, and refresh UI.
     */
    fun applyEnvironment(environment: String) {
        _activeEnvironment.value = environment
        preferencesManager.currentEnvironment = environment
    }

    /**
     * Lock the current environment and show lock screen.
     */
    fun lockEnvironment() {
        val intent = Intent(context, LockScreenActivity::class.java).apply {
            flags = Intent.FLAG_ACTIVITY_NEW_TASK or
                    Intent.FLAG_ACTIVITY_CLEAR_TOP or
                    Intent.FLAG_ACTIVITY_SINGLE_TOP
        }
        context.startActivity(intent)
    }

    /**
     * Populate an environment with all installed apps.
     * Called during initial setup.
     */
    suspend fun populateEnvironment(environment: String) {
        val installedApps = getInstalledApps()
        val entities = installedApps.mapIndexed { index, appInfo ->
            AppInfoEntity(
                packageName = appInfo.packageName,
                appName = appInfo.loadLabel(packageManager).toString(),
                environment = environment,
                position = index,
                page = if (index < 20) 0 else (index / 20),
                isOnHomeScreen = index < 20,
                installTime = System.currentTimeMillis()
            )
        }
        appDao.insertAll(entities)
    }

    /**
     * Add a specific app to an environment.
     */
    suspend fun addAppToEnvironment(packageName: String, environment: String) {
        try {
            val appInfo = packageManager.getApplicationInfo(packageName, 0)
            val count = getAppCount(environment)
            val entity = AppInfoEntity(
                packageName = packageName,
                appName = appInfo.loadLabel(packageManager).toString(),
                environment = environment,
                position = count,
                page = count / 20,
                isOnHomeScreen = true
            )
            appDao.insert(entity)
        } catch (e: PackageManager.NameNotFoundException) {
            // App not installed
        }
    }

    /**
     * Remove an app from an environment.
     */
    suspend fun removeAppFromEnvironment(packageName: String, environment: String) {
        appDao.deleteByPackageAndEnvironment(packageName, environment)
    }

    /**
     * Get all installed apps on the device.
     */
    private fun getInstalledApps(): List<android.content.pm.ApplicationInfo> {
        return packageManager.getInstalledApplications(PackageManager.GET_META_DATA)
            .filter { it packageName != context.packageName } // Exclude ourselves
            .filter {
                // Only show launchable apps
                packageManager.getLaunchIntentForPackage(it.packageName) != null
            }
            .sortedBy { it.loadLabel(packageManager).toString().lowercase() }
    }

    private suspend fun getAppCount(environment: String): Int {
        return appDao.getAppCount(environment).values.first() ?: 0
    }

    private fun logSecurityEvent(eventType: String, environment: String) {
        scope.launch(Dispatchers.IO) {
            try {
                DualPersonaApp.getInstance().database.securityEventDao().insert(
                    com.dualpersona.launcher.data.entity.SecurityEventEntity(
                        eventType = eventType,
                        environment = environment,
                        details = "From: ${_activeEnvironment.value}"
                    )
                )
            } catch (_: Exception) {}
        }
    }

    fun getCurrentEnvironment(): String = _activeEnvironment.value
}
