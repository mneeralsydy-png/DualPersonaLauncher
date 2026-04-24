package com.dualpersona.system.ui.setup

import android.app.admin.DevicePolicyManager
import android.content.ComponentName
import android.content.Context
import android.content.Intent
import android.os.Build
import android.os.Bundle
import android.provider.Settings
import android.view.View
import android.widget.ArrayAdapter
import android.widget.Toast
import androidx.activity.result.contract.ActivityResultContracts
import androidx.appcompat.app.AlertDialog
import androidx.appcompat.app.AppCompatActivity
import androidx.lifecycle.lifecycleScope
import com.dualpersona.system.R
import com.dualpersona.system.core.*
import com.dualpersona.system.data.PreferencesManager
import com.dualpersona.system.data.SecurityLog
import com.dualpersona.system.receiver.DualPersonaAdmin
import kotlinx.coroutines.Job
import kotlinx.coroutines.launch

class SetupWizardActivity : AppCompatActivity() {

    private lateinit var prefs: PreferencesManager
    private lateinit var userManager: SystemUserManager
    private lateinit var credentialManager: CredentialManager
    private lateinit var stealthManager: StealthManager

    private var currentStep = 0
    private val totalSteps = 5
    private var detectJob: Job? = null

    private val deviceAdminLauncher = registerForActivityResult(
        ActivityResultContracts.StartActivityForResult()
    ) { result ->
        if (result.resultCode == RESULT_OK) {
            SecurityLog.log(this, "SUCCESS", "device_admin", "Device admin granted")
            updateStep()
        } else {
            Toast.makeText(this, getString(R.string.dialog_admin_required), Toast.LENGTH_LONG).show()
        }
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        prefs = PreferencesManager(this)
        userManager = SystemUserManager(this)
        credentialManager = CredentialManager(this)
        stealthManager = StealthManager(this)

        currentStep = prefs.getCurrentSetupStep()

        if (prefs.isSetupComplete()) {
            if (prefs.isStealthModeEnabled()) {
                finish()
                return
            }
            showSetupCompleteDialog()
            return
        }

        showStep(currentStep)
    }

    private fun showStep(step: Int) {
        currentStep = step
        prefs.setCurrentSetupStep(step)

        when (step) {
            0 -> showWelcomeStep()
            1 -> showPermissionsStep()
            2 -> showUserAConfigStep()
            3 -> showUserBConfigStep()
            4 -> showSecurityAndFinalizeStep()
            else -> finish()
        }
    }

    private fun updateStep() {
        showStep(currentStep + 1)
    }

    // ===== STEP 0: Welcome =====
    private fun showWelcomeStep() {
        setContentView(R.layout.activity_setup_wizard)

        val tvTitle = findViewById<android.widget.TextView>(R.id.tv_title)
        val tvDesc = findViewById<android.widget.TextView>(R.id.tv_description)
        val tvStep = findViewById<android.widget.TextView>(R.id.tv_step)
        val btnNext = findViewById<android.widget.Button>(R.id.btn_next)
        val btnBack = findViewById<android.widget.Button>(R.id.btn_back)
        val progressBar = findViewById<android.widget.ProgressBar>(R.id.progress_bar)

        tvTitle.text = getString(R.string.app_name)
        tvDesc.text = getString(R.string.setup_welcome_desc)
        tvStep.text = "1/$totalSteps"
        progressBar.progress = (1 * 100) / totalSteps

        btnBack.visibility = View.GONE
        btnNext.text = getString(R.string.get_started)
        btnNext.setOnClickListener { showStep(1) }
    }

    // ===== STEP 1: Permissions =====
    private fun showPermissionsStep() {
        setContentView(R.layout.activity_setup_wizard)

        val tvTitle = findViewById<android.widget.TextView>(R.id.tv_title)
        val tvDesc = findViewById<android.widget.TextView>(R.id.tv_description)
        val tvStep = findViewById<android.widget.TextView>(R.id.tv_step)
        val btnNext = findViewById<android.widget.Button>(R.id.btn_next)
        val btnBack = findViewById<android.widget.Button>(R.id.btn_back)
        val progressBar = findViewById<android.widget.ProgressBar>(R.id.progress_bar)
        val containerExtra = findViewById<android.widget.LinearLayout>(R.id.container_extra)

        tvTitle.text = getString(R.string.setup_permissions_title)
        tvStep.text = "2/$totalSteps"
        progressBar.progress = (2 * 100) / totalSteps

        containerExtra.removeAllViews()
        val permChecks = mutableListOf<android.widget.CheckBox>()

        val permissions = listOf(
            getString(R.string.perm_device_admin) to getString(R.string.perm_device_admin_desc),
        )

        for ((name, desc) in permissions) {
            val cb = android.widget.CheckBox(this).apply {
                text = name
                textSize = 16f
                setPadding(0, 24, 0, 8)
                isEnabled = false
            }
            containerExtra.addView(cb)
            permChecks.add(cb)

            val tv = android.widget.TextView(this).apply {
                text = desc
                textSize = 12f
                setPadding(48, 0, 0, 16)
                setTextColor(getColor(android.R.color.darker_gray))
            }
            containerExtra.addView(tv)
        }

        val dpm = getSystemService(Context.DEVICE_POLICY_SERVICE) as DevicePolicyManager
        val adminComponent = ComponentName(this, DualPersonaAdmin::class.java)
        permChecks[0].isChecked = dpm.isAdminActive(adminComponent)

        tvDesc.text = getString(R.string.setup_permissions_desc)

        btnBack.visibility = View.VISIBLE
        btnBack.setOnClickListener { showStep(0) }

        if (dpm.isAdminActive(adminComponent)) {
            btnNext.text = getString(R.string.next)
            btnNext.setOnClickListener { showStep(2) }
        } else {
            btnNext.text = getString(R.string.grant_permissions)
            btnNext.setOnClickListener {
                val intent = Intent(DevicePolicyManager.ACTION_ADD_DEVICE_ADMIN).apply {
                    putExtra(DevicePolicyManager.EXTRA_DEVICE_ADMIN, adminComponent)
                    putExtra(DevicePolicyManager.EXTRA_ADD_EXPLANATION,
                        getString(R.string.dialog_admin_required))
                }
                deviceAdminLauncher.launch(intent)
            }
        }
    }

    // ===== STEP 2: Confirm User A (Current Phone State) =====
    private fun showUserAConfigStep() {
        setContentView(R.layout.activity_setup_wizard)

        val tvTitle = findViewById<android.widget.TextView>(R.id.tv_title)
        val tvDesc = findViewById<android.widget.TextView>(R.id.tv_description)
        val tvStep = findViewById<android.widget.TextView>(R.id.tv_step)
        val btnNext = findViewById<android.widget.Button>(R.id.btn_next)
        val btnBack = findViewById<android.widget.Button>(R.id.btn_back)
        val progressBar = findViewById<android.widget.ProgressBar>(R.id.progress_bar)
        val containerExtra = findViewById<android.widget.LinearLayout>(R.id.container_extra)

        tvTitle.text = getString(R.string.setup_user_a_title)
        tvStep.text = "3/$totalSteps"
        progressBar.progress = (3 * 100) / totalSteps

        containerExtra.removeAllViews()

        // Info: Current phone is User A
        val tvInfo = android.widget.TextView(this).apply {
            text = getString(R.string.setup_user_a_info)
            textSize = 14f
            setTextColor(getColor(android.R.color.holo_green_dark))
            setPadding(0, 8, 0, 24)
        }
        containerExtra.addView(tvInfo)

        // Name field
        containerExtra.addView(createLabel(getString(R.string.label_profile_name_a)))
        val etName = android.widget.EditText(this).apply {
            hint = getString(R.string.hint_profile_a)
            setText(prefs.getProfileName(0))
            setPadding(0, 16, 0, 32)
        }
        containerExtra.addView(etName)

        // Show current lock screen credential type
        val currentCredType = detectCurrentCredentialType()
        val tvCredStatus = android.widget.TextView(this).apply {
            text = getString(R.string.setup_user_a_current_credential, currentCredType)
            textSize = 14f
            setTextColor(getColor(android.R.color.holo_blue_dark))
            setPadding(0, 8, 0, 24)
        }
        containerExtra.addView(tvCredStatus)

        val tvNote = android.widget.TextView(this).apply {
            text = getString(R.string.setup_user_a_note)
            textSize = 12f
            setTextColor(getColor(android.R.color.darker_gray))
            setPadding(0, 0, 0, 16)
        }
        containerExtra.addView(tvNote)

        tvDesc.text = getString(R.string.setup_user_a_desc)

        btnBack.visibility = View.VISIBLE
        btnBack.setOnClickListener { showStep(1) }

        btnNext.text = getString(R.string.set_and_continue)
        btnNext.setOnClickListener {
            val name = etName.text.toString().ifBlank { getString(R.string.profile_a) }
            prefs.setProfileName(0, name)

            // Store detected credential type
            val credType = when (currentCredType) {
                "PIN", "رقم PIN" -> CredentialManager.CredentialType.PIN
                "نمط", "Pattern" -> CredentialManager.CredentialType.PATTERN
                "كلمة مرور", "Password" -> CredentialManager.CredentialType.PASSWORD
                else -> CredentialManager.CredentialType.PIN
            }
            credentialManager.storeCredentialMeta(0, credType, name)

            SecurityLog.log(this, "INFO", "setup_user_a",
                "User A confirmed: $name (current credential: $currentCredType)")
            showStep(3)
        }
    }

    private fun detectCurrentCredentialType(): String {
        return try {
            val dpm = getSystemService(Context.DEVICE_POLICY_SERVICE) as DevicePolicyManager
            val adminComponent = ComponentName(this, DualPersonaAdmin::class.java)

            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.M) {
                val keyguardMgr = getSystemService(Context.KEYGUARD_SERVICE) as? android.app.KeyguardManager
                if (keyguardMgr?.isDeviceSecure == true) {
                    if (keyguardMgr.isKeyguardSecure) {
                        if (credentialManager.getBiometricStatus() == CredentialManager.BiometricStatus.ENROLLED) {
                            return getString(R.string.cred_fingerprint_pin)
                        }
                        // Try to detect PIN vs Pattern vs Password
                        val quality = try {
                            val method = DevicePolicyManager::class.java.getMethod("getPasswordQuality")
                            method.invoke(dpm) as? Int ?: 0
                        } catch (e: Exception) { 0 }

                        return when (quality) {
                            DevicePolicyManager.PASSWORD_QUALITY_NUMERIC,
                            DevicePolicyManager.PASSWORD_QUALITY_NUMERIC_COMPLEX ->
                                getString(R.string.cred_pin)
                            DevicePolicyManager.PASSWORD_QUALITY_SOMETHING ->
                                getString(R.string.cred_pattern)
                            else ->
                                getString(R.string.cred_password)
                        }
                    }
                }
            }
            getString(R.string.cred_pin) // Default
        } catch (e: Exception) {
            getString(R.string.cred_pin)
        }
    }

    // ===== STEP 3: Create User B =====
    private fun showUserBConfigStep() {
        setContentView(R.layout.activity_setup_wizard)

        val tvTitle = findViewById<android.widget.TextView>(R.id.tv_title)
        val tvDesc = findViewById<android.widget.TextView>(R.id.tv_description)
        val tvStep = findViewById<android.widget.TextView>(R.id.tv_step)
        val btnNext = findViewById<android.widget.Button>(R.id.btn_next)
        val btnBack = findViewById<android.widget.Button>(R.id.btn_back)
        val progressBar = findViewById<android.widget.ProgressBar>(R.id.progress_bar)
        val containerExtra = findViewById<android.widget.LinearLayout>(R.id.container_extra)

        tvTitle.text = getString(R.string.setup_user_b_title)
        tvStep.text = "4/$totalSteps"
        progressBar.progress = (4 * 100) / totalSteps

        containerExtra.removeAllViews()

        // Check if user B already exists
        if (userManager.hasSecondaryUser()) {
            tvDesc.text = getString(R.string.setup_user_b_already_exists)
            containerExtra.addView(createLabel(getString(R.string.label_profile_name_b)))
            val tvName = android.widget.TextView(this).apply {
                text = prefs.getSecondaryUserName() ?: prefs.getProfileName(1)
                textSize = 16f
                setPadding(0, 16, 0, 32)
            }
            containerExtra.addView(tvName)

            btnBack.visibility = View.VISIBLE
            btnBack.setOnClickListener { showStep(2) }
            btnNext.text = getString(R.string.next)
            btnNext.setOnClickListener { showStep(4) }
            return
        }

        if (!userManager.isMultiUserSupported()) {
            tvDesc.text = getString(R.string.setup_multi_user_not_supported)
            btnNext.text = getString(R.string.skip)
            btnNext.setOnClickListener { showStep(4) }
            btnBack.setOnClickListener { showStep(2) }
            return
        }

        tvDesc.text = getString(R.string.setup_user_b_desc)

        containerExtra.addView(createLabel(getString(R.string.label_profile_name_b)))
        val etName = android.widget.EditText(this).apply {
            hint = getString(R.string.hint_profile_b)
            setText(prefs.getProfileName(1))
            setPadding(0, 16, 0, 32)
        }
        containerExtra.addView(etName)

        // Instructions for manual creation
        val tvInstructions = android.widget.TextView(this).apply {
            text = getString(R.string.setup_user_b_instructions)
            textSize = 13f
            setTextColor(getColor(android.R.color.holo_orange_dark))
            setPadding(0, 16, 0, 16)
        }
        containerExtra.addView(tvInstructions)

        btnBack.visibility = View.VISIBLE
        btnBack.setOnClickListener { showStep(2) }

        btnNext.text = getString(R.string.create_user_b)
        btnNext.setOnClickListener {
            val name = etName.text.toString().ifBlank { getString(R.string.profile_b) }
            prefs.setProfileName(1, name)

            lifecycleScope.launch {
                showProgress(getString(R.string.dialog_creating_user_b))

                // Attempt automatic creation
                val result = userManager.createSecondaryUser(name)

                if (result.isSuccess) {
                    val handle = userManager.getSecondaryUserHandle()
                    if (handle != null) {
                        userManager.applyUserRestrictions(handle)
                    }
                    hideProgress()

                    // Now ask user to set lock screen for User B
                    promptSetUserBCredential(name)
                    return@launch
                }

                // Auto-creation failed, guide to manual creation
                hideProgress()

                AlertDialog.Builder(this@SetupWizardActivity)
                    .setTitle(getString(R.string.dialog_auto_create_failed_title))
                    .setMessage(getString(R.string.dialog_auto_create_failed_msg, name))
                    .setPositiveButton(getString(R.string.dialog_open_settings)) { _, _ ->
                        // Open system user settings
                        userManager.openUserSettings()

                        // Start polling for new user in background
                        showProgress(getString(R.string.dialog_waiting_for_user))

                        detectJob = lifecycleScope.launch {
                            val detectResult = userManager.detectNewSecondaryUser(timeoutMs = 180_000)
                            hideProgress()

                            if (detectResult.isSuccess) {
                                Toast.makeText(
                                    this@SetupWizardActivity,
                                    getString(R.string.dialog_user_detected),
                                    Toast.LENGTH_SHORT
                                ).show()

                                // Store the detected user name
                                val detectedHandle = detectResult.getOrNull()
                                if (detectedHandle != null) {
                                    try {
                                        val serialMethod = android.os.UserManager::class.java.getMethod(
                                            "getSerialNumberForUser", android.os.UserHandle::class.java
                                        )
                                        val serial = serialMethod.invoke(
                                            userManager, detectedHandle
                                        ) as? Long ?: -1
                                        prefs.setSecondaryUserHandleId(serial)
                                        prefs.setSecondaryUserName(name)
                                    } catch (e: Exception) { }
                                }

                                // Ask user to set lock screen for User B
                                promptSetUserBCredential(name)
                            } else {
                                Toast.makeText(
                                    this@SetupWizardActivity,
                                    getString(R.string.dialog_user_not_detected),
                                    Toast.LENGTH_LONG
                                ).show()
                            }
                        }
                    }
                    .setNegativeButton(getString(R.string.dialog_retry_later)) { _, _ ->
                        // Skip and let user retry later
                        showStep(4)
                    }
                    .setCancelable(false)
                    .show()
            }
        }
    }

    /**
     * After User B is created, prompt user to switch to it and set a lock screen credential
     */
    private fun promptSetUserBCredential(userName: String) {
        AlertDialog.Builder(this)
            .setTitle(getString(R.string.dialog_set_user_b_credential_title))
            .setMessage(getString(R.string.dialog_set_user_b_credential_msg, userName))
            .setPositiveButton(getString(R.string.dialog_switch_and_set)) { _, _ ->
                // Store credential meta as PIN (default)
                credentialManager.storeCredentialMeta(1, CredentialManager.CredentialType.PIN, userName)

                SecurityLog.log(this, "SUCCESS", "setup_user_b",
                    "User B '$userName' created successfully")

                showStep(4)
            }
            .setNegativeButton(getString(R.string.dialog_set_later)) { _, _ ->
                credentialManager.storeCredentialMeta(1, CredentialManager.CredentialType.PIN, userName)
                showStep(4)
            }
            .setCancelable(false)
            .show()
    }

    // ===== STEP 4: Security & Finalize (Combined) =====
    private fun showSecurityAndFinalizeStep() {
        setContentView(R.layout.activity_setup_wizard)

        val tvTitle = findViewById<android.widget.TextView>(R.id.tv_title)
        val tvDesc = findViewById<android.widget.TextView>(R.id.tv_description)
        val tvStep = findViewById<android.widget.TextView>(R.id.tv_step)
        val btnNext = findViewById<android.widget.Button>(R.id.btn_next)
        val btnBack = findViewById<android.widget.Button>(R.id.btn_back)
        val progressBar = findViewById<android.widget.ProgressBar>(R.id.progress_bar)
        val containerExtra = findViewById<android.widget.LinearLayout>(R.id.container_extra)

        tvTitle.text = getString(R.string.setup_security_title)
        tvStep.text = "5/$totalSteps"
        progressBar.progress = 100
        tvDesc.text = getString(R.string.setup_security_desc)

        containerExtra.removeAllViews()

        // Summary section
        val tvSummaryTitle = android.widget.TextView(this).apply {
            text = getString(R.string.setup_complete_title)
            textSize = 17f
            setTypeface(null, android.graphics.Typeface.BOLD)
            setPadding(0, 0, 0, 16)
        }
        containerExtra.addView(tvSummaryTitle)

        val summaryLines = mutableListOf<String>()
        summaryLines.add(getString(R.string.summary_profile_a, prefs.getProfileName(0)))
        summaryLines.add("  ${getString(R.string.summary_credential, prefs.getCredentialType(0))}")
        summaryLines.add("")
        summaryLines.add(getString(R.string.summary_profile_b, prefs.getProfileName(1)))
        val userBStatus = if (userManager.hasSecondaryUser()) getString(R.string.summary_status_created) else getString(R.string.summary_status_not_created)
        summaryLines.add("  $userBStatus")
        summaryLines.add("")

        for (line in summaryLines) {
            val tv = android.widget.TextView(this).apply {
                text = line
                textSize = 14f
                setPadding(0, 4, 0, 4)
                if (!line.startsWith("  ")) {
                    setTypeface(null, android.graphics.Typeface.BOLD)
                }
            }
            containerExtra.addView(tv)
        }

        // Separator
        val separator = android.view.View(this).apply {
            setBackgroundColor(getColor(android.R.color.darker_gray))
            layoutParams = android.widget.LinearLayout.LayoutParams(
                android.widget.LinearLayout.LayoutParams.MATCH_PARENT, 1
            ).apply { setMargins(0, 24, 0, 24) }
        }
        containerExtra.addView(separator)

        // Secret code
        containerExtra.addView(createLabel(getString(R.string.label_secret_code)))
        val etSecret = android.widget.EditText(this).apply {
            hint = getString(R.string.hint_secret_code)
            inputType = android.text.InputType.TYPE_CLASS_NUMBER or
                    android.text.InputType.TYPE_NUMBER_VARIATION_PASSWORD
            setText(stealthManager.getSecretCode())
            setPadding(0, 16, 0, 32)
            filters = arrayOf(android.text.InputFilter.LengthFilter(6))
        }
        containerExtra.addView(etSecret)

        // Stealth mode
        val cbStealth = android.widget.CheckBox(this).apply {
            text = getString(R.string.label_stealth_mode)
            textSize = 16f
            isChecked = true
            setPadding(0, 16, 0, 8)
        }
        containerExtra.addView(cbStealth)

        val tvStealthInfo = android.widget.TextView(this).apply {
            text = getString(R.string.setup_stealth_info)
            textSize = 12f
            setTextColor(getColor(android.R.color.darker_gray))
            setPadding(48, 0, 0, 24)
        }
        containerExtra.addView(tvStealthInfo)

        // Important notice
        val tvNotice = android.widget.TextView(this).apply {
            text = getString(R.string.setup_important_notice)
            textSize = 13f
            setTextColor(getColor(android.R.color.holo_orange_dark))
            setPadding(0, 16, 0, 16)
        }
        containerExtra.addView(tvNotice)

        btnBack.visibility = View.VISIBLE
        btnBack.setOnClickListener { showStep(3) }

        btnNext.text = getString(R.string.activate_now)
        btnNext.setOnClickListener {
            val code = etSecret.text.toString().ifBlank { StealthManager.DEFAULT_SECRET_CODE }
            stealthManager.setSecretCode(code)
            prefs.setStealthModeEnabled(cbStealth.isChecked)
            activateSystem()
        }
    }

    private fun activateSystem() {
        lifecycleScope.launch {
            showProgress(getString(R.string.dialog_activating))

            prefs.setSetupComplete(true)
            startServices()

            if (prefs.isStealthModeEnabled()) {
                stealthManager.enableStealthMode()
            }

            SecurityLog.log(this@SetupWizardActivity, "SUCCESS", "system_activate",
                "Dual Persona System activated successfully")

            hideProgress()

            AlertDialog.Builder(this@SetupWizardActivity)
                .setTitle(getString(R.string.setup_success_title))
                .setMessage(getString(R.string.setup_success_message))
                .setPositiveButton(getString(R.string.dialog_ok)) { _, _ -> finish() }
                .setCancelable(false)
                .show()
        }
    }

    private fun startServices() {
        val systemIntent = Intent(this, com.dualpersona.system.service.SystemService::class.java)
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            startForegroundService(systemIntent)
        } else {
            startService(systemIntent)
        }

        val guardIntent = Intent(this, com.dualpersona.system.service.GuardService::class.java)
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            startForegroundService(guardIntent)
        } else {
            startService(guardIntent)
        }
    }

    private fun createLabel(text: String): android.widget.TextView {
        return android.widget.TextView(this).apply {
            this.text = text
            textSize = 15f
            setPadding(0, 8, 0, 4)
            setTypeface(null, android.graphics.Typeface.BOLD)
        }
    }

    private lateinit var progressDialog: android.app.AlertDialog

    private fun showProgress(message: String) {
        progressDialog = android.app.AlertDialog.Builder(this)
            .setMessage(message)
            .setCancelable(false)
            .create()
        progressDialog.show()
    }

    private fun hideProgress() {
        if (::progressDialog.isInitialized && progressDialog.isShowing) {
            progressDialog.dismiss()
        }
    }

    private fun showSetupCompleteDialog() {
        AlertDialog.Builder(this)
            .setTitle(getString(R.string.dialog_system_active))
            .setMessage(getString(R.string.dialog_system_active_msg, stealthManager.getSecretCode()))
            .setPositiveButton(getString(R.string.dialog_reconfigure)) { _, _ ->
                prefs.setSetupComplete(false)
                prefs.setCurrentSetupStep(0)
                showStep(0)
            }
            .setNegativeButton(getString(R.string.dialog_close)) { _, _ -> finish() }
            .show()
    }

    override fun onDestroy() {
        super.onDestroy()
        detectJob?.cancel()
    }

    override fun onBackPressed() {
        if (currentStep > 0) {
            showStep(currentStep - 1)
        } else {
            super.onBackPressed()
        }
    }
}
