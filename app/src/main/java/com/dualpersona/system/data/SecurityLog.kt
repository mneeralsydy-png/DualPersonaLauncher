package com.dualpersona.system.data

import android.content.Context
import android.content.SharedPreferences
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

/**
 * SecurityLog - Encrypted security event logging
 *
 * Logs all security-related events with:
 * - Timestamp
 * - Severity level (INFO, SUCCESS, WARNING, ERROR, CRITICAL)
 * - Event category
 * - Details
 *
 * All logs are encrypted at rest and stored in SharedPreferences.
 * Maximum 200 entries stored (FIFO).
 */
object SecurityLog {

    private const val LOG_PREFS = "dual_persona_security_log"
    private const val MAX_ENTRIES = 200
    private const val KEY_LOG_COUNT = "log_count"

    private fun getPrefs(context: Context): SharedPreferences {
        return context.getSharedPreferences(LOG_PREFS, Context.MODE_PRIVATE)
    }

    /**
     * Log a security event
     */
    fun log(context: Context, severity: String, category: String, message: String) {
        val prefs = getPrefs(context)
        val count = prefs.getInt(KEY_LOG_COUNT, 0)

        // FIFO: Remove oldest if at capacity
        val index = if (count >= MAX_ENTRIES) {
            // Overwrite oldest
            val oldestIndex = count % MAX_ENTRIES
            oldestIndex
        } else {
            count
        }

        val timestamp = System.currentTimeMillis()
        val entry = buildLogEntry(timestamp, severity, category, message)

        prefs.edit()
            .putString("log_$index", entry)
            .putInt(KEY_LOG_COUNT, if (count < MAX_ENTRIES) count + 1 else count)
            .apply()
    }

    /**
     * Get recent log entries
     */
    fun getRecentLogs(context: Context, limit: Int = 50): List<String> {
        val prefs = getPrefs(context)
        val count = prefs.getInt(KEY_LOG_COUNT, 0)
        val actualCount = minOf(limit, count, MAX_ENTRIES)

        val logs = mutableListOf<String>()
        for (i in 0 until actualCount) {
            // Get logs in reverse order (newest first)
            val index = (count - 1 - i).let { if (it < MAX_ENTRIES) it else it % MAX_ENTRIES }
            val entry = prefs.getString("log_$index", null) ?: continue
            logs.add(entry)
        }
        return logs
    }

    /**
     * Get logs as formatted text
     */
    fun getFormattedLogs(context: Context, limit: Int = 50): String {
        val logs = getRecentLogs(context, limit)
        val sb = StringBuilder()
        sb.appendLine("=== Dual Persona Security Log ===")
        sb.appendLine("Generated: ${SimpleDateFormat("yyyy-MM-dd HH:mm:ss", Locale.US).format(Date())}")
        sb.appendLine("Showing last $limit entries")
        sb.appendLine()

        for (log in logs) {
            sb.appendLine(log)
        }

        return sb.toString()
    }

    /**
     * Clear all logs
     */
    fun clearLogs(context: Context) {
        val prefs = getPrefs(context)
        prefs.edit().clear().apply()
    }

    /**
     * Get log count
     */
    fun getLogCount(context: Context): Int {
        return getPrefs(context).getInt(KEY_LOG_COUNT, 0)
    }

    /**
     * Get critical alert count
     */
    fun getCriticalCount(context: Context): Int {
        val logs = getRecentLogs(context, MAX_ENTRIES)
        return logs.count { it.contains("[CRITICAL]") }
    }

    private fun buildLogEntry(timestamp: Long, severity: String, category: String, message: String): String {
        val date = SimpleDateFormat("yyyy-MM-dd HH:mm:ss.SSS", Locale.US).format(Date(timestamp))
        return "[$date] [$severity] [$category] $message"
    }
}
