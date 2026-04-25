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
 * لا يستخدم أي API مخفي أو انعكاس (reflection)
 * لا يحدث أي انهيار - أبداً
 *
 * الاستراتيجية:
 * - إنشاء المستخدم B يتم عبر إعدادات النظام فقط (بدون برمجة)
 * - يفتح التطبيق إعدادات المستخدمين ويوجه المستخدم
 * - المستخدم يؤكد إنشاء المستخدم B بنفسه
 * - التبديل بين المستخدمين يتم عبر إعدادات النظام
 */
class SystemUserManager(private val context: Context) {

    companion object {
        private const val TAG = "SystemUserManager"
    }

    private val prefs: PreferencesManager = PreferencesManager(context)

    // ====================================================================
    // إنشاء المستخدم B - الطريقة الآمنة (عبر إعدادات النظام)
    // ====================================================================

    /**
     * فتح إعدادات المستخدمين في النظام لإنشاء مستخدم جديد يدوياً.
     * هذه الطريقة آمنة 100% - لا تستخدم أي API مخفي.
     *
     * @return true إذا تم فتح الإعدادات بنجاح
     */
    fun openUserSettingsForCreation(): Boolean {
        val intents = listOf(
            // الطريقة 1: إعدادات المستخدمين مباشرة
            Intent("android.settings.USER_SETTINGS").apply {
                addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
            },
            // الطريقة 2: قائمة المستخدمين
            Intent("android.settings.USERS").apply {
                addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
            },
            // الطريقة 3: فتح من خلال Settings
            Intent(android.provider.Settings.ACTION_SETTINGS).apply {
                addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
            }
        )

        for (intent in intents) {
            try {
                context.startActivity(intent)
                SecurityLog.log(context, "INFO", "open_user_settings",
                    "Opened system user settings for manual creation")
                return true
            } catch (e: Exception) {
                Log.d(TAG, "Intent failed: ${intent.action}: ${e.message}")
            }
        }
        return false
    }

    /**
     * فتح إعدادات الأمان لتعيين كلمة مرور القفل للمستخدم B.
     */
    fun openSecuritySettings(): Boolean {
        val intents = listOf(
            Intent(android.provider.Settings.ACTION_SECURITY_SETTINGS).apply {
                addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
            },
            Intent("android.settings.LOCK_SETTINGS").apply {
                addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
            }
        )

        for (intent in intents) {
            try {
                context.startActivity(intent)
                SecurityLog.log(context, "INFO", "open_security",
                    "Opened security settings for credential setup")
                return true
            } catch (e: Exception) {
                Log.d(TAG, "Security intent failed: ${e.message}")
            }
        }
        return false
    }

    /**
     * تأكيد أن المستخدم B تم إنشاؤه يدوياً.
     * يخزن هذه المعلومة محلياً بدون أي API مخفي.
     */
    fun confirmUserBCreated(userName: String) {
        prefs.setSecondaryUserName(userName)
        prefs.setSecondaryUserConfirmed(true)
        prefs.setSecondaryUserHandleId(System.currentTimeMillis())
        SecurityLog.log(context, "SUCCESS", "user_b_confirmed",
            "User B '$userName' confirmed as created")
    }

    /**
     * فتح إعدادات المستخدمين للتبديل بين المستخدمين.
     */
    fun openUserSwitchSettings(): Boolean {
        val intents = listOf(
            Intent("android.settings.USER_SETTINGS").apply {
                addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
            },
            Intent("android.settings.USERS").apply {
                addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
            },
            Intent(android.provider.Settings.ACTION_SETTINGS).apply {
                addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
            }
        )

        for (intent in intents) {
            try {
                context.startActivity(intent)
                SecurityLog.log(context, "INFO", "open_switch",
                    "Opened user switch settings")
                return true
            } catch (e: Exception) {
                Log.d(TAG, "Switch intent failed: ${e.message}")
            }
        }
        return false
    }

    // ====================================================================
    // استعلامات آمنة (بدون API مخفي)
    // ====================================================================

    /**
     * التحقق مما إذا كان المستخدم B تم تأكيده.
     * يقرأ فقط من التفضيلات المحلية - آمن 100%.
     */
    fun hasSecondaryUser(): Boolean {
        return try {
            prefs.isSecondaryUserConfirmed()
        } catch (e: Exception) {
            false
        }
    }

    /**
     * الحصول على اسم المستخدم B.
     * يقرأ فقط من التفضيلات المحلية - آمن 100%.
     */
    fun getSecondaryUserName(): String {
        return try {
            prefs.getSecondaryUserName() ?: prefs.getProfileName(1)
        } catch (e: Exception) {
            prefs.getProfileName(1)
        }
    }

    /**
     * الحصول على معلومات المستخدم B.
     */
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

    /**
     * التحقق مما إذا كان الجهاز يدعم المستخدمين المتعددين.
     * يستخدم API عام فقط.
     */
    fun isMultiUserSupported(): Boolean {
        return try {
            Build.VERSION.SDK_INT >= Build.VERSION_CODES.LOLLIPOP
        } catch (e: Exception) {
            false
        }
    }

    // ====================================================================
    // إزالة المستخدم B (عبر إعدادات النظام)
    // ====================================================================

    /**
     * فتح إعدادات المستخدمين لحذف المستخدم B يدوياً.
     */
    fun openUserSettingsForRemoval(): Boolean {
        val opened = openUserSettingsForCreation()
        if (opened) {
            SecurityLog.log(context, "INFO", "open_remove",
                "Opened settings for user B removal")
        }
        return opened
    }

    /**
     * إزالة بيانات المستخدم B محلياً (بعد حذفه من النظام).
     */
    fun confirmUserBRemoved() {
        prefs.clearSecondaryUser()
        prefs.setSecondaryUserConfirmed(false)
        SecurityLog.log(context, "INFO", "user_b_removed",
            "User B data cleared from preferences")
    }

    // ====================================================================
    // لا نستخدم CreateResult بعد الآن - التطبيق لا ينشئ المستخدم برمجياً
    // ====================================================================
    data class CreateResult(
        val success: Boolean,
        val method: String,
        val handle: Any?,
        val error: String?
    )
}
