package com.dualpersona.system.ui.dashboard

import android.content.Intent
import android.os.Bundle
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
        findViewById<TextView>(R.id.tv_dashboard_title).text = getString(R.string.dashboard_title)

        // Profile A
        findViewById<TextView>(R.id.tv_profile_a_name).text = prefs.getProfileName(0)
        findViewById<TextView>(R.id.tv_profile_a_cred).text =
            getString(R.string.dash_credential, prefs.getCredentialType(0))

        // Profile B
        findViewById<TextView>(R.id.tv_profile_b_name).text = prefs.getProfileName(1)
        val userInfo = userManager.getSecondaryUserInfo()
        if (userInfo != null) {
            findViewById<TextView>(R.id.tv_profile_b_status).text =
                getString(R.string.dash_status_active_id, userInfo["id"] as? Int ?: 0)
        } else {
            findViewById<TextView>(R.id.tv_profile_b_status).text =
                getString(R.string.dash_status_not_created)
        }

        // Isolation
        val tvIsolation = findViewById<TextView>(R.id.tv_isolation_status)
        tvIsolation.text = getString(R.string.dash_isolation_checking)
        lifecycleScope.launch {
            val report = withContext(Dispatchers.IO) { dataGuard.verifyIsolation() }
            tvIsolation.text = if (report.allPassed)
                getString(R.string.isolation_secure) else getString(R.string.isolation_warning)
            tvIsolation.setTextColor(getColor(
                if (report.allPassed) android.R.color.holo_green_dark
                else android.R.color.holo_red_dark
            ))
        }

        // Services
        findViewById<TextView>(R.id.tv_service_status).text =
            if (prefs.isServiceStarted()) getString(R.string.services_running)
            else getString(R.string.services_stopped)

        // Stealth
        findViewById<TextView>(R.id.tv_stealth_status).text =
            if (prefs.isStealthModeEnabled()) getString(R.string.stealth_active)
            else getString(R.string.stealth_off)

        // Buttons
        findViewById<Button>(R.id.btn_switch_to_b)?.setOnClickListener { switchToUserB() }
        findViewById<Button>(R.id.btn_switch_to_a)?.setOnClickListener { switchToUserA() }
        findViewById<Button>(R.id.btn_view_logs)?.setOnClickListener { showSecurityLogs() }
        findViewById<Button>(R.id.btn_change_secret)?.setOnClickListener { changeSecretCode() }
        findViewById<Button>(R.id.btn_toggle_stealth)?.setOnClickListener { toggleStealthMode() }
        findViewById<Button>(R.id.btn_reset)?.setOnClickListener { confirmReset() }
        findViewById<Button>(R.id.btn_refresh)?.setOnClickListener { refreshStatus() }
        findViewById<Button>(R.id.btn_check_isolation)?.setOnClickListener { runIsolationCheck() }
    }

    private fun refreshStatus() {
        findViewById<TextView>(R.id.tv_profile_a_name)?.text = prefs.getProfileName(0)
        findViewById<TextView>(R.id.tv_profile_a_cred)?.text =
            getString(R.string.dash_credential, prefs.getCredentialType(0))
        findViewById<TextView>(R.id.tv_profile_b_name)?.text = prefs.getProfileName(1)

        val userInfo = userManager.getSecondaryUserInfo()
        if (userInfo != null) {
            findViewById<TextView>(R.id.tv_profile_b_status)?.text =
                getString(R.string.dash_status_active_id, userInfo["id"] as? Int ?: 0)
        } else {
            findViewById<TextView>(R.id.tv_profile_b_status)?.text =
                getString(R.string.dash_status_not_created)
        }

        findViewById<TextView>(R.id.tv_stealth_status)?.text =
            if (prefs.isStealthModeEnabled()) getString(R.string.stealth_active)
            else getString(R.string.stealth_off)

        findViewById<TextView>(R.id.tv_service_status)?.text =
            if (prefs.isServiceStarted()) getString(R.string.services_running)
            else getString(R.string.services_stopped)
    }

    private fun switchToUserB() {
        if (!userManager.hasSecondaryUser()) {
            Toast.makeText(this, getString(R.string.dash_user_not_created), Toast.LENGTH_SHORT).show()
            return
        }
        lifecycleScope.launch {
            try {
                userManager.switchToSecondaryUserAsync()
                Toast.makeText(this@DashboardActivity,
                    getString(R.string.dash_switching, prefs.getProfileName(1)), Toast.LENGTH_SHORT).show()
            } catch (e: Exception) {
                Toast.makeText(this@DashboardActivity,
                    getString(R.string.dash_switch_failed, e.message), Toast.LENGTH_LONG).show()
            }
        }
    }

    private fun switchToUserA() {
        lifecycleScope.launch {
            try {
                userManager.switchToMainUserAsync()
                Toast.makeText(this@DashboardActivity,
                    getString(R.string.dash_switching, prefs.getProfileName(0)), Toast.LENGTH_SHORT).show()
            } catch (e: Exception) {
                Toast.makeText(this@DashboardActivity,
                    getString(R.string.dash_switch_failed, e.message), Toast.LENGTH_LONG).show()
            }
        }
    }

    private fun showSecurityLogs() {
        val logs = dataGuard.getRecentSecurityEvents(50)
        val logText = if (logs.isEmpty()) {
            getString(R.string.dash_no_logs)
        } else {
            logs.joinToString("\n")
        }

        AlertDialog.Builder(this)
            .setTitle(getString(R.string.view_security_logs))
            .setMessage(logText)
            .setPositiveButton(getString(R.string.dash_clear_logs)) { _, _ ->
                dataGuard.clearSecurityLogs()
                Toast.makeText(this, getString(R.string.dash_clear_logs), Toast.LENGTH_SHORT).show()
            }
            .setNegativeButton(getString(R.string.dialog_close), null)
            .show()
    }

    private fun changeSecretCode() {
        val et = EditText(this).apply {
            inputType = android.text.InputType.TYPE_CLASS_NUMBER
            hint = getString(R.string.dash_new_code)
            filters = arrayOf(android.text.InputFilter.LengthFilter(6))
            setText(stealthManager.getSecretCode())
        }

        AlertDialog.Builder(this)
            .setTitle(getString(R.string.dash_change_code))
            .setView(et)
            .setPositiveButton(getString(R.string.dialog_save)) { _, _ ->
                val code = et.text.toString()
                if (code.length in 4..6) {
                    stealthManager.setSecretCode(code)
                    Toast.makeText(this, getString(R.string.dialog_code_updated, code), Toast.LENGTH_LONG).show()
                    SecurityLog.log(this, "INFO", "secret_code_change", "Secret code changed")
                } else {
                    Toast.makeText(this, getString(R.string.dialog_code_invalid), Toast.LENGTH_SHORT).show()
                }
            }
            .setNegativeButton(getString(R.string.dialog_cancel), null)
            .show()
    }

    private fun toggleStealthMode() {
        val currentState = prefs.isStealthModeEnabled()
        val newState = !currentState
        val actionStr = if (newState) getString(R.string.dialog_stealth_enable)
                        else getString(R.string.dialog_stealth_disable)
        val currentStateStr = if (currentState) getString(R.string.dialog_stealth_on)
                              else getString(R.string.dialog_stealth_off)

        AlertDialog.Builder(this)
            .setTitle(getString(R.string.dash_stealth_mode))
            .setMessage(getString(R.string.dialog_stealth_current, currentStateStr) + "\n\n" +
                    getString(R.string.dialog_stealth_action, actionStr))
            .setPositiveButton(actionStr) { _, _ ->
                if (newState) stealthManager.enableStealthMode()
                else stealthManager.disableStealthMode()
                refreshStatus()
            }
            .setNegativeButton(getString(R.string.dialog_cancel), null)
            .show()
    }

    private fun runIsolationCheck() {
        lifecycleScope.launch {
            val report = withContext(Dispatchers.IO) { dataGuard.verifyIsolation() }

            val message = StringBuilder()
            message.appendLine("Data Isolation Report")
            message.appendLine("Overall: ${if (report.allPassed) "PASS" else "FAIL"}")
            message.appendLine()

            for (check in report.checks) {
                val icon = if (check.passed) "[OK]" else "[FAIL]"
                message.appendLine("$icon ${check.name}: ${check.description}")
            }

            AlertDialog.Builder(this@DashboardActivity)
                .setTitle(getString(R.string.run_isolation_check))
                .setMessage(message.toString())
                .setPositiveButton(getString(R.string.dialog_ok), null)
                .show()

            refreshStatus()
        }
    }

    private fun confirmReset() {
        AlertDialog.Builder(this)
            .setTitle(getString(R.string.reset_system))
            .setMessage(getString(R.string.dash_reset_confirm))
            .setPositiveButton(getString(R.string.dash_reset_all)) { _, _ -> resetSystem() }
            .setNegativeButton(getString(R.string.dialog_cancel), null)
            .show()
    }

    private fun resetSystem() {
        lifecycleScope.launch {
            stopService(Intent(this@DashboardActivity, SystemService::class.java))
            stopService(Intent(this@DashboardActivity, GuardService::class.java))

            userManager.removeSecondaryUser()
            prefs.resetAll()
            stealthManager.disableStealthMode()
            dataGuard.clearSecurityLogs()

            SecurityLog.log(this@DashboardActivity, "INFO", "system_reset", "System reset complete")

            Toast.makeText(this@DashboardActivity,
                getString(R.string.dash_reset_done), Toast.LENGTH_LONG).show()
            finish()
        }
    }

    override fun onResume() {
        super.onResume()
        if (prefs.isStealthModeEnabled()) {
            android.os.Handler(android.os.Looper.getMainLooper()).postDelayed({
                if (!isFinishing && !isDestroyed) {
                    stealthManager.enableStealthMode()
                }
            }, 30_000)
        }
    }
}
