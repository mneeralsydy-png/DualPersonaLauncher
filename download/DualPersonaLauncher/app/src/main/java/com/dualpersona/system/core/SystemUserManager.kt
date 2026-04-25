package com.dualpersona.system.core

import android.content.Context
import android.content.Intent
import android.os.Build
import android.util.Log
import com.dualpersona.system.data.PreferencesManager
import com.dualpersona.system.data.SecurityLog

/**
 * SystemUserManager - يدير المستخدمين المتعددين بطريقة آمنة 100%
 * 
 * لا يستخدم أي API مخفي ولا يحدث أي انهيار أبداً
 * الاستراتيجية: فتح إعدادات النظام فقط
 */
class SystemUserManager(private val context: Context) {

    companion object {
        private const val TAG = "SystemUserManager"
    }

    private val prefs: PreferencesManager = PreferencesManager(context)

    /**
     * فتح إعدادات المستخدمين - آمن 100%
     */
    fun openUserSettings(): Boolean {
        val intents = listOf(
            Intent("android.settings.USER_SETTINGS").apply { addFlags(Intent.FLAG_ACTIVITY_NEW_TASK) },
            Intent("android.settings.USERS").apply { addFlags(Intent.FLAG_ACTIVITY_NEW_TASK) },
            Intent(android.provider.Settings.ACTION_SETTINGS).apply { addFlags(Intent.FLAG_ACTIVITY_NEW_TASK) }
        )
        for (intent in intents) {
            try {
                context.startActivity(intent)
                return true
            } catch (e: Exception) {
                Log.d(TAG, "Intent failed: ${e.message}")
            }
        }
        return false
    }

    /**
     * فتح إعدادات الأمان لتعيين كلمة مرور القفل
     */
    fun openSecuritySettings(): Boolean {
        val intents = listOf(
            Intent(android.provider.Settings.ACTION_SECURITY_SETTINGS).apply { addFlags(Intent.FLAG_ACTIVITY_NEW_TASK) },
            Intent("android.settings.LOCK_SETTINGS").apply { addFlags(Intent.FLAG_ACTIVITY_NEW_TASK) }
        )
        for (intent in intents) {
            try {
                context.startActivity(intent)
                return true
            } catch (e: Exception) {
                Log.d(TAG, "Security intent failed: ${e.message}")
            }
        }
        return false
    }

    /**
     * فتح إعدادات البصمة
     */
    fun openBiometricSettings(): Boolean {
        return try {
            val intent = Intent(android.provider.Settings.ACTION_BIOMETRIC_ENROLL).apply {
                addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
            }
            context.startActivity(intent)
            true
        } catch (e: Exception) { false }
    }

    /**
     * فتح إعدادات الشاشة والقفل
     */
    fun openLockScreenSettings(): Boolean {
        return try {
            val intent = Intent(android.provider.Settings.ACTION_SECURITY_SETTINGS).apply {
                addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
                putExtra("android.provider.extra.INSTALLER_PACKAGE_NAME", context.packageName)
            }
            context.startActivity(intent)
            true
        } catch (e: Exception) { false }
    }

    /**
     * تأكيد أن المستخدم B تم إنشاؤه
     */
    fun confirmUserBCreated(userName: String) {
        try {
            prefs.setSecondaryUserName(userName)
            prefs.setSecondaryUserConfirmed(true)
            SecurityLog.log(context, "SUCCESS", "user_b_confirmed", "User B '$userName' confirmed")
        } catch (e: Exception) {
            Log.e(TAG, "confirmUserBCreated error", e)
        }
    }

    fun hasSecondaryUser(): Boolean {
        return try { prefs.isSecondaryUserConfirmed() } catch (e: Exception) { false }
    }

    fun getSecondaryUserName(): String {
        return try {
            prefs.getSecondaryUserName() ?: prefs.getProfileName(1)
        } catch (e: Exception) {
            prefs.getProfileName(1)
        }
    }

    fun getSecondaryUserInfo(): Map<String, Any?> {
        return try {
            mapOf(
                "name" to getSecondaryUserName(),
                "confirmed" to prefs.isSecondaryUserConfirmed(),
                "created" to true
            )
        } catch (e: Exception) {
            mapOf("name" to prefs.getProfileName(1), "confirmed" to false, "created" to false)
        }
    }

    fun confirmUserBRemoved() {
        try {
            prefs.clearSecondaryUser()
            SecurityLog.log(context, "INFO", "user_b_removed", "User B data cleared")
        } catch (e: Exception) {}
    }

    fun isMultiUserSupported(): Boolean = Build.VERSION.SDK_INT >= Build.VERSION_CODES.LOLLIPOP
}
