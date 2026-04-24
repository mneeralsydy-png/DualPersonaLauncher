package com.dualpersona.system.ui.dashboard

import android.content.ComponentName
import android.content.Context
import android.content.Intent
import android.os.Bundle
import android.view.View
import android.widget.*
import androidx.appcompat.app.AlertDialog
import androidx.appcompat.app.AppCompatActivity
import androidx.lifecycle.lifecycleScope
import com.dualpersona.system.R
import com.dualpersona.system.core.*
import com.dualpersona.system.data.PreferencesManager
import com.dualpersona.system.data.SecurityLog
import com.dualpersona.system.service.GuardService
import com.dualpersona.system.service.SystemService
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

/**
 * DashboardActivity - Hidden management dashboard
 *
 * Only accessible via secret dialer code (*#*#CODE#*#*)
 * Provides full control over the dual persona system:
 * - View both profiles
 * - Switch users
 * - Modify security settings
 * - View security logs
 * - Toggle stealth mode
 * - Remove secondary user
 * - Reset system
 */
class DashboardActivity : AppCompatActivity() {

    private lateinit var prefs: PreferencesManager
    private lateinit var userManager: SystemUserManager
    private lateinit var credentialManager: CredentialManager
    private lateinit var stealthManager: StealthManager
    private lateinit var dataGuard: DataGuard
    private lateinit var envConfig: EnvironmentConfig

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        prefs = PreferencesManager(this)
        userManager = SystemUserManager(this)
        credentialManager = CredentialManager(this)
        stealthManager = StealthManager(this)
        dataGuard = DataGuard(this)
        envConfig = EnvironmentConfig(this)

        setContentView(R.layout.activity_dashboard)
        setupViews()
        refreshStatus()
    }

    private fun setupViews() {
        // Header
        findViewById<TextView>(R.id.tv_dashboard_title).text = "Dual Persona Control Panel"

        // Profile A info
        findViewById<TextView>(R.id.tv_profile_a_name).text = prefs.getProfileName(0)
        findViewById<TextView>(R.id.tv_profile_a_cred).text =
            "Credential: ${prefs.getCredentialType(0)}"

        // Profile B info
        findViewById<TextView>(R.id.tv_profile_b_name).text = prefs.getProfileName(1)
        val userInfo = userManager.getSecondaryUserInfo()
        findViewById<TextView>(R.id.tv_profile_b_status).text =
            if (userInfo != null) "Status: Active" else "Status: Not Created"

        // Isolation status
        val tvIsolation = findViewById<TextView>(R.id.tv_isolation_status)
        lifecycleScope.launch {
            val report = withContext(Dispatchers.IO) { dataGuard.verifyIsolation() }
            tvIsolation.text = if (report.allPassed) "Data Isolation: Secure" else "Data Isolation: Check Failed"
            tvIsolation.setTextColor(getColor(
                if (report.allPassed) android.R.color.holo_green_dark
                else android.R.color.holo_red_dark
            ))
        }

        // Service status
        findViewById<TextView>(R.id.tv_service_status).text =
            if (prefs.isServiceStarted()) "Services: Running" else "Services: Stopped"

        // Stealth status
        findViewById<TextView>(R.id.tv_stealth_status).text =
            if (prefs.isStealthModeEnabled()) "Stealth: Active" else "Stealth: Off"

        // ===== Action Buttons =====

        // Switch to Profile B
        findViewById<Button>(R.id.btn_switch_to_b)?.setOnClickListener {
            switchToUserB()
        }

        // Switch to Profile A
        findViewById<Button>(R.id.btn_switch_to_a)?.setOnClickListener {
            switchToUserA()
        }

        // Security Logs
        findViewById<Button>(R.id.btn_view_logs)?.setOnClickListener {
            showSecurityLogs()
        }

        // Change Secret Code
        findViewById<Button>(R.id.btn_change_secret)?.setOnClickListener {
            changeSecretCode()
        }

        // Toggle Stealth
        findViewById<Button>(R.id.btn_toggle_stealth)?.setOnClickListener {
            toggleStealthMode()
        }

        // Reset System
        findViewById<Button>(R.id.btn_reset)?.setOnClickListener {
            confirmReset()
        }

        // Refresh
        findViewById<Button>(R.id.btn_refresh)?.setOnClickListener {
            refreshStatus()
        }

        // Isolation Check
        findViewById<Button>(R.id.btn_check_isolation)?.setOnClickListener {
            runIsolationCheck()
        }
    }

    private fun refreshStatus() {
        // Refresh all status fields
        findViewById<TextView>(R.id.tv_profile_a_name)?.text = prefs.getProfileName(0)
        findViewById<TextView>(R.id.tv_profile_a_cred)?.text =
            "Credential: ${prefs.getCredentialType(0)}"
        findViewById<TextView>(R.id.tv_profile_b_name)?.text = prefs.getProfileName(1)
        val userInfo = userManager.getSecondaryUserInfo()
        findViewById<TextView>(R.id.tv_profile_b_status)?.text =
            if (userInfo != null) "Status: Active (ID: ${userInfo.id})" else "Status: Not Created"
        findViewById<TextView>(R.id.tv_stealth_status)?.text =
            if (prefs.isStealthModeEnabled()) "Stealth: Active" else "Stealth: Off"
        findViewById<TextView>(R.id.tv_service_status)?.text =
            if (prefs.isServiceStarted()) "Services: Running" else "Services: Stopped"
    }

    // ===== User Switching =====

    private fun switchToUserB() {
        if (!userManager.hasSecondaryUser()) {
            Toast.makeText(this, "Secondary user not created", Toast.LENGTH_SHORT).show()
            return
        }

        lifecycleScope.launch {
            try {
                userManager.switchToSecondaryUserAsync()
                Toast.makeText(this@DashboardActivity,
                    "Switching to ${prefs.getProfileName(1)}...", Toast.LENGTH_SHORT).show()
            } catch (e: Exception) {
                Toast.makeText(this@DashboardActivity,
                    "Switch failed: ${e.message}", Toast.LENGTH_LONG).show()
            }
        }
    }

    private fun switchToUserA() {
        lifecycleScope.launch {
            try {
                userManager.switchToMainUserAsync()
                Toast.makeText(this@DashboardActivity,
                    "Switching to ${prefs.getProfileName(0)}...", Toast.LENGTH_SHORT).show()
            } catch (e: Exception) {
                Toast.makeText(this@DashboardActivity,
                    "Switch failed: ${e.message}", Toast.LENGTH_LONG).show()
            }
        }
    }

    // ===== Security Logs =====

    private fun showSecurityLogs() {
        val logs = dataGuard.getRecentSecurityEvents(50)
        val logText = if (logs.isEmpty()) {
            "No security events recorded."
        } else {
            logs.joinToString("\n")
        }

        AlertDialog.Builder(this)
            .setTitle("Security Log")
            .setMessage(logText)
            .setPositiveButton("Clear Logs") { _, _ ->
                dataGuard.clearSecurityLogs()
                Toast.makeText(this, "Logs cleared", Toast.LENGTH_SHORT).show()
            }
            .setNegativeButton("Close", null)
            .show()
    }

    // ===== Secret Code =====

    private fun changeSecretCode() {
        val et = EditText(this).apply {
            inputType = android.text.InputType.TYPE_CLASS_NUMBER
            hint = "New 4-6 digit code"
            filters = arrayOf(android.text.InputFilter.LengthFilter(6))
            setText(stealthManager.getSecretCode())
        }

        AlertDialog.Builder(this)
            .setTitle("Change Secret Access Code")
            .setView(et)
            .setPositiveButton("Save") { _, _ ->
                val code = et.text.toString()
                if (code.length in 4..6) {
                    stealthManager.setSecretCode(code)
                    Toast.makeText(this, "Code updated to: *#*#$code#*#*", Toast.LENGTH_LONG).show()
                    SecurityLog.log(this, "INFO", "secret_code_change", "Secret code changed")
                } else {
                    Toast.makeText(this, "Code must be 4-6 digits", Toast.LENGTH_SHORT).show()
                }
            }
            .setNegativeButton("Cancel", null)
            .show()
    }

    // ===== Stealth Toggle =====

    private fun toggleStealthMode() {
        val currentState = prefs.isStealthModeEnabled()
        val newState = !currentState
        val action = if (newState) "enable" else "disable"

        AlertDialog.Builder(this)
            .setTitle("Stealth Mode")
            .setMessage("Currently: ${if (currentState) "Active" else "Off"}\n\n" +
                    "Do you want to $action stealth mode?")
            .setPositiveButton(if (newState) "Enable" else "Disable") { _, _ ->
                if (newState) {
                    stealthManager.enableStealthMode()
                } else {
                    stealthManager.disableStealthMode()
                }
                refreshStatus()
            }
            .setNegativeButton("Cancel", null)
            .show()
    }

    // ===== Isolation Check =====

    private fun runIsolationCheck() {
        lifecycleScope.launch {
            val report = withContext(Dispatchers.IO) { dataGuard.verifyIsolation() }

            val message = StringBuilder()
            message.appendLine("Isolation Report - ${java.text.SimpleDateFormat("yyyy-MM-dd HH:mm:ss", java.util.Locale.US).format(java.util.Date(report.timestamp))}")
            message.appendLine("Overall: ${if (report.allPassed) "PASS" else "FAIL"}")
            message.appendLine()

            for (check in report.checks) {
                val icon = if (check.passed) "[OK]" else "[FAIL]"
                message.appendLine("$icon ${check.name}: ${check.description}")
            }

            AlertDialog.Builder(this@DashboardActivity)
                .setTitle("Data Isolation Check")
                .setMessage(message.toString())
                .setPositiveButton("OK", null)
                .show()

            refreshStatus()
        }
    }

    // ===== Reset =====

    private fun confirmReset() {
        AlertDialog.Builder(this)
            .setTitle("Reset System")
            .setMessage("WARNING: This will:\n" +
                    "1. Remove secondary user and ALL their data\n" +
                    "2. Clear all settings\n" +
                    "3. Stop all services\n" +
                    "4. Un-hide the app\n\n" +
                    "This action CANNOT be undone!")
            .setPositiveButton("RESET EVERYTHING") { _, _ ->
                resetSystem()
            }
            .setNegativeButton("Cancel", null)
            .show()
    }

    private fun resetSystem() {
        lifecycleScope.launch {
            // Stop services
            stopService(Intent(this@DashboardActivity, SystemService::class.java))
            stopService(Intent(this@DashboardActivity, GuardService::class.java))

            // Remove secondary user
            userManager.removeSecondaryUser()

            // Clear preferences
            prefs.resetAll()

            // Show app icon
            stealthManager.disableStealthMode()

            // Clear security logs
            dataGuard.clearSecurityLogs()

            SecurityLog.log(this@DashboardActivity, "INFO", "system_reset", "System reset complete")

            Toast.makeText(this@DashboardActivity,
                "System reset complete. Restart app for setup.", Toast.LENGTH_LONG).show()

            finish()
        }
    }

    override fun onResume() {
        super.onResume()
        // Re-enter stealth mode after timeout if stealth is enabled
        if (prefs.isStealthModeEnabled()) {
            android.os.Handler(android.os.Looper.getMainLooper()).postDelayed({
                if (!isFinishing && !isDestroyed) {
                    stealthManager.enableStealthMode()
                }
            }, 30_000) // Re-hide after 30 seconds
        }
    }
}
