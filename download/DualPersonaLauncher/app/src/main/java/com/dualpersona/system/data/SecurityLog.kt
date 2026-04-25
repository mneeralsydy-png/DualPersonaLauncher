package com.dualpersona.system.data

import android.content.Context
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

/**
 * SecurityLog - تسجيل الأحداث الأمنية (مبسط ومستقر)
 */
object SecurityLog {

    fun log(context: Context, severity: String, category: String, message: String) {
        try {
            val prefs = PreferencesManager(context)
            val timestamp = SimpleDateFormat("yyyy-MM-dd HH:mm:ss", Locale.US).format(Date())
            val entry = "[$timestamp] [$severity] [$category] $message"
            prefs.addSecurityLog(entry)
        } catch (e: Exception) {
            // Silent fail - logging should never crash the app
        }
    }

    fun getRecentLogs(context: Context, limit: Int = 50): List<String> {
        return try {
            PreferencesManager(context).getSecurityLogs().take(limit)
        } catch (e: Exception) { emptyList() }
    }

    fun clearLogs(context: Context) {
        try { PreferencesManager(context).clearSecurityLogs() } catch (e: Exception) {}
    }
}
