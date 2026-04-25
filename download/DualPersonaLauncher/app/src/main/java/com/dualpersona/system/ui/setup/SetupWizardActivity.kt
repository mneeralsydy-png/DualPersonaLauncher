package com.dualpersona.system.ui.setup

import android.app.admin.DevicePolicyManager
import android.content.ComponentName
import android.content.Context
import android.content.Intent
import android.os.Build
import android.os.Bundle
import android.util.Log
import android.view.View
import android.widget.LinearLayout
import android.widget.Toast
import androidx.activity.result.contract.ActivityResultContracts
import androidx.appcompat.app.AlertDialog
import androidx.appcompat.app.AppCompatActivity
import androidx.lifecycle.lifecycleScope
import com.dualpersona.system.R
import com.dualpersona.system.core.CredentialManager
import com.dualpersona.system.core.StealthManager
import com.dualpersona.system.core.SystemUserManager
import com.dualpersona.system.data.PreferencesManager
import com.dualpersona.system.data.SecurityLog
import com.dualpersona.system.receiver.DualPersonaAdmin
import kotlinx.coroutines.launch

/**
 * SetupWizardActivity - معالج الإعداد المبسط والآمن 100%
 * 
 * 3 خطوات فقط:
 * 1. ترحيب وتأكيد صلاحيات
 * 2. إعداد الملف الشخصي A و B  
 * 3. الإعدادات النهائية والتفعيل
 */
class SetupWizardActivity : AppCompatActivity() {

    companion object {
        private const val TAG = "SetupWizard"
    }

    private var currentStep = 0
    private val totalSteps = 3
    private var progressDialog: AlertDialog? = null

    private val deviceAdminLauncher = registerForActivityResult(
        ActivityResultContracts.StartActivityForResult()
    ) { result ->
        try {
            if (result.resultCode == RESULT_OK) {
                showStep(currentStep)
            } else {
                Toast.makeText(this, getString(R.string.dialog_admin_required), Toast.LENGTH_LONG).show()
            }
        } catch (e: Exception) {
            Log.e(TAG, "deviceAdminLauncher error", e)
        }
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        try {
            super.onCreate(savedInstanceState)

            val prefs = PreferencesManager(this)

            if (prefs.isSetupComplete()) {
                if (prefs.isStealthModeEnabled()) {
                    finish()
                    return
                }
                showSetupAlreadyDone(prefs)
                return
            }

            currentStep = prefs.getCurrentSetupStep()
            showStep(currentStep)
        } catch (e: Exception) {
            Log.e(TAG, "onCreate error", e)
            Toast.makeText(this, "حدث خطأ. يرجى إعادة المحاولة.", Toast.LENGTH_LONG).show()
            finish()
        }
    }

    // ==================================================================
    // عرض الخطوات
    // ==================================================================

    private fun showStep(step: Int) {
        try {
            currentStep = step
            PreferencesManager(this).setCurrentSetupStep(step)
            when (step) {
                0 -> showWelcomeStep()
                1 -> showProfilesStep()
                2 -> showFinalizeStep()
                else -> finish()
            }
        } catch (e: Exception) {
            Log.e(TAG, "showStep($step) error", e)
            Toast.makeText(this, "خطأ في تحميل الخطوة.", Toast.LENGTH_LONG).show()
        }
    }

    // ==================================================================
    // الخطوة 0: ترحيب + صلاحيات
    // ==================================================================

    private fun showWelcomeStep() {
        try {
            setContentView(R.layout.activity_setup_wizard)
            val tvTitle = findViewById<android.widget.TextView>(R.id.tv_title)
            val tvDesc = findViewById<android.widget.TextView>(R.id.tv_description)
            val tvStep = findViewById<android.widget.TextView>(R.id.tv_step)
            val btnNext = findViewById<android.widget.Button>(R.id.btn_next)
            val btnBack = findViewById<android.widget.Button>(R.id.btn_back)
            val progressBar = findViewById<android.widget.ProgressBar>(R.id.progress_bar)
            val containerExtra = findViewById<LinearLayout>(R.id.container_extra)

            tvTitle.text = getString(R.string.app_name)
            tvDesc.text = getString(R.string.setup_welcome_desc)
            tvStep.text = "1/$totalSteps"
            progressBar.progress = 33
            btnBack.visibility = View.GONE
            containerExtra.removeAllViews()

            // Device admin checkbox
            val dpm = getSystemService(Context.DEVICE_POLICY_SERVICE) as? DevicePolicyManager
            val adminComponent = ComponentName(this, DualPersonaAdmin::class.java)
            val isAdmin = try { dpm?.isAdminActive(adminComponent) == true } catch (e: Exception) { false }

            containerExtra.addView(makeLabel(getString(R.string.setup_permissions_title)))
            containerExtra.addView(makeTextView(getString(R.string.perm_device_admin), 16f, if (isAdmin) getColor(android.R.color.holo_green_dark) else getColor(android.R.color.darker_gray)))
            
            val statusText = if (isAdmin) "مفعّل" else "غير مفعّل"
            val statusColor = if (isAdmin) getColor(android.R.color.holo_green_dark) else getColor(android.R.color.holo_red_dark)
            containerExtra.addView(makeTextView("  الحالة: $statusText", 14f, statusColor))

            if (isAdmin) {
                btnNext.text = getString(R.string.next)
                btnNext.setOnClickListener { showStep(1) }
            } else {
                containerExtra.addView(makeTextView(getString(R.string.perm_device_admin_desc), 12f, getColor(android.R.color.darker_gray)))
                btnNext.text = getString(R.string.grant_permissions)
                btnNext.setOnClickListener {
                    try {
                        val intent = Intent(DevicePolicyManager.ACTION_ADD_DEVICE_ADMIN).apply {
                            putExtra(DevicePolicyManager.EXTRA_DEVICE_ADMIN, adminComponent)
                            putExtra(DevicePolicyManager.EXTRA_ADD_EXPLANATION, getString(R.string.dialog_admin_required))
                        }
                        deviceAdminLauncher.launch(intent)
                    } catch (e: Exception) {
                        Log.e(TAG, "admin launch failed", e)
                        Toast.makeText(this, "خطأ: ${e.message}", Toast.LENGTH_LONG).show()
                    }
                }
            }
        } catch (e: Exception) {
            Log.e(TAG, "showWelcomeStep error", e)
        }
    }

    // ==================================================================
    // الخطوة 1: إعداد الملفات الشخصية A و B
    // ==================================================================

    private fun showProfilesStep() {
        try {
            setContentView(R.layout.activity_setup_wizard)
            val tvTitle = findViewById<android.widget.TextView>(R.id.tv_title)
            val tvDesc = findViewById<android.widget.TextView>(R.id.tv_description)
            val tvStep = findViewById<android.widget.TextView>(R.id.tv_step)
            val btnNext = findViewById<android.widget.Button>(R.id.btn_next)
            val btnBack = findViewById<android.widget.Button>(R.id.btn_back)
            val progressBar = findViewById<android.widget.ProgressBar>(R.id.progress_bar)
            val containerExtra = findViewById<LinearLayout>(R.id.container_extra)

            tvTitle.text = "إعداد الملفات الشخصية"
            tvDesc.text = "أدخل أسماء الملفات الشخصية وقم بإنشاء المستخدم الثاني"
            tvStep.text = "2/$totalSteps"
            progressBar.progress = 66
            btnBack.visibility = View.VISIBLE
            btnBack.setOnClickListener { showStep(0) }
            containerExtra.removeAllViews()

            val prefs = PreferencesManager(this)
            val userManager = SystemUserManager(this)
            val credentialManager = CredentialManager(this)

            // --- Profile A ---
            containerExtra.addView(makeLabel("الملف الشخصي A (الهاتف الحالي):"))
            containerExtra.addView(makeTextView("هاتفك الحالي = الملف الشخصي A. كل شيء يبقى كما هو بدون تغيير.", 13f, getColor(android.R.color.holo_green_dark)))

            val etNameA = android.widget.EditText(this).apply {
                hint = "مثال: شخصي"
                setText(prefs.getProfileName(0))
                setPadding(0, 16, 0, 8)
            }
            containerExtra.addView(etNameA)

            // Current credential type
            val credType = if (credentialManager.hasDeviceCredential()) {
                if (credentialManager.getBiometricStatus() == CredentialManager.BiometricStatus.ENROLLED) {
                    "بصمة + رقم PIN"
                } else {
                    "رقم PIN / كلمة مرور / نمط"
                }
            } else {
                "لم يتم تعيين قفل شاشة"
            }
            containerExtra.addView(makeTextView("قفل الشاشة الحالي: $credType", 13f, getColor(android.R.color.darker_gray)))

            // --- Separator ---
            containerExtra.addView(makeSeparator())

            // --- Profile B ---
            containerExtra.addView(makeLabel("الملف الشخصي B (مستخدم جديد):"))
            
            if (userManager.hasSecondaryUser()) {
                containerExtra.addView(makeTextView("تم إنشاء المستخدم B: ${userManager.getSecondaryUserName()}", 14f, getColor(android.R.color.holo_green_dark)))
                btnNext.text = getString(R.string.next)
                btnNext.setOnClickListener {
                    try {
                        val nameA = etNameA.text.toString().ifBlank { "الملف الشخصي A" }
                        prefs.setProfileName(0, nameA)
                        showStep(2)
                    } catch (e: Exception) {
                        showStep(2)
                    }
                }
            } else {
                val etNameB = android.widget.EditText(this).apply {
                    hint = "مثال: عمل"
                    setText(prefs.getProfileName(1))
                    setPadding(0, 16, 0, 8)
                }
                containerExtra.addView(etNameB)

                containerExtra.addView(makeTextView(
                    "سيتم فتح إعدادات النظام لإنشاء المستخدم الجديد.\n" +
                    "اضغط على \"إضافة مستخدم\" واختر \"مستخدم جديد\".",
                    12f, getColor(android.R.color.holo_orange_dark)
                ))

                btnNext.text = "فتح الإعدادات لإنشاء المستخدم B"
                btnNext.setOnClickListener {
                    try {
                        val nameA = etNameA.text.toString().ifBlank { "الملف الشخصي A" }
                        val nameB = etNameB.text.toString().ifBlank { "الملف الشخصي B" }
                        prefs.setProfileName(0, nameA)
                        prefs.setProfileName(1, nameB)
                        
                        // Open system settings
                        userManager.openUserSettings()
                        
                        // Ask for confirmation
                        showConfirmUserBCreatedDialog(nameB)
                    } catch (e: Exception) {
                        Log.e(TAG, "createUserB error", e)
                        Toast.makeText(this, "خطأ: ${e.message}", Toast.LENGTH_LONG).show()
                    }
                }
            }
        } catch (e: Exception) {
            Log.e(TAG, "showProfilesStep error", e)
            Toast.makeText(this, "خطأ في تحميل الصفحة.", Toast.LENGTH_LONG).show()
        }
    }

    private fun showConfirmUserBCreatedDialog(name: String) {
        try {
            AlertDialog.Builder(this)
                .setTitle("هل أنشأت المستخدم \"$name\"؟")
                .setMessage("بعد إنشاء المستخدم من إعدادات النظام، اضغط \"نعم\".\n\nإذا لم تنشئه بعد، اضغط \"لا\".")
                .setPositiveButton("نعم، تم الإنشاء") { _, _ ->
                    try {
                        SystemUserManager(this).confirmUserBCreated(name)
                        Toast.makeText(this, "تم تأكيد المستخدم B بنجاح!", Toast.LENGTH_LONG).show()
                        showStep(2)
                    } catch (e: Exception) {
                        showStep(2)
                    }
                }
                .setNegativeButton("لا، لم أنشئه بعد") { _, _ ->
                    Toast.makeText(this, "يمكنك إنشاؤه لاحقاً من لوحة التحكم.", Toast.LENGTH_LONG).show()
                    showStep(2)
                }
                .setCancelable(false)
                .show()
        } catch (e: Exception) {
            showStep(2)
        }
    }

    // ==================================================================
    // الخطوة 2: الإعدادات النهائية والتفعيل
    // ==================================================================

    private fun showFinalizeStep() {
        try {
            setContentView(R.layout.activity_setup_wizard)
            val tvTitle = findViewById<android.widget.TextView>(R.id.tv_title)
            val tvDesc = findViewById<android.widget.TextView>(R.id.tv_description)
            val tvStep = findViewById<android.widget.TextView>(R.id.tv_step)
            val btnNext = findViewById<android.widget.Button>(R.id.btn_next)
            val btnBack = findViewById<android.widget.Button>(R.id.btn_back)
            val progressBar = findViewById<android.widget.ProgressBar>(R.id.progress_bar)
            val containerExtra = findViewById<LinearLayout>(R.id.container_extra)

            tvTitle.text = "الإعدادات النهائية"
            tvDesc.text = "راجع الإعدادات وقم بتفعيل النظام"
            tvStep.text = "3/$totalSteps"
            progressBar.progress = 100
            btnBack.visibility = View.VISIBLE
            btnBack.setOnClickListener { showStep(1) }
            containerExtra.removeAllViews()

            val prefs = PreferencesManager(this)
            val userManager = SystemUserManager(this)
            val stealthManager = StealthManager(this)

            // Summary
            containerExtra.addView(makeLabel("ملخص الإعداد:"))
            containerExtra.addView(makeTextView("الملف الشخصي A: ${prefs.getProfileName(0)}", 14f, 0))
            
            val userBStatus = if (userManager.hasSecondaryUser())
                "تم الإنشاء (${userManager.getSecondaryUserName()})"
            else
                "لم يتم الإنشاء (يمكن إنشاؤه لاحقاً)"
            containerExtra.addView(makeTextView("الملف الشخصي B: $userBStatus", 14f, 0))

            containerExtra.addView(makeSeparator())

            // Secret code
            containerExtra.addView(makeLabel("رمز الوصول السري:"))
            containerExtra.addView(makeTextView("اطلب *#*#CODE#*#* في تطبيق الهاتف للوصول للتطبيق بعد إخفائه", 12f, getColor(android.R.color.darker_gray)))

            val etSecret = android.widget.EditText(this).apply {
                hint = "رمز من 4 أرقام"
                inputType = android.text.InputType.TYPE_CLASS_NUMBER or android.text.InputType.TYPE_NUMBER_VARIATION_PASSWORD
                setText(stealthManager.getSecretCode())
                setPadding(0, 16, 0, 24)
                filters = arrayOf(android.text.InputFilter.LengthFilter(6))
            }
            containerExtra.addView(etSecret)

            // Stealth mode checkbox
            val cbStealth = android.widget.CheckBox(this).apply {
                text = "إخفاء التطبيق بعد التفعيل (وضع التخفي)"
                textSize = 15f
                isChecked = true
                setPadding(0, 16, 0, 8)
            }
            containerExtra.addView(cbStealth)

            containerExtra.addView(makeTextView(
                "ملاحظة: يجب الانتقال إلى المستخدم B وتعيين كلمة مرور القفل الخاصة به\nمن: الإعدادات > الأمان > قفل الشاشة",
                12f, getColor(android.R.color.holo_orange_dark)
            ))

            btnNext.text = "تفعيل النظام"
            btnNext.setOnClickListener {
                try {
                    val code = etSecret.text.toString().ifBlank { StealthManager.DEFAULT_SECRET_CODE }
                    stealthManager.setSecretCode(code)
                    prefs.setStealthModeEnabled(cbStealth.isChecked)
                    activateSystem()
                } catch (e: Exception) {
                    Log.e(TAG, "finalize error", e)
                    Toast.makeText(this, "خطأ: ${e.message}", Toast.LENGTH_LONG).show()
                }
            }
        } catch (e: Exception) {
            Log.e(TAG, "showFinalizeStep error", e)
        }
    }

    // ==================================================================
    // التفعيل
    // ==================================================================

    private fun activateSystem() {
        lifecycleScope.launch {
            try {
                showProgress("جارٍ تفعيل النظام...")
                val prefs = PreferencesManager(this@SetupWizardActivity)
                val stealthManager = StealthManager(this@SetupWizardActivity)

                prefs.setSetupComplete(true)
                startServices()
                
                if (prefs.isStealthModeEnabled()) {
                    stealthManager.enableStealthMode()
                }

                SecurityLog.log(this@SetupWizardActivity, "SUCCESS", "system_activate", "System activated")
                hideProgress()

                AlertDialog.Builder(this@SetupWizardActivity)
                    .setTitle("تم تفعيل النظام!")
                    .setMessage(getString(R.string.setup_success_message))
                    .setPositiveButton("حسناً") { _, _ -> finish() }
                    .setCancelable(false)
                    .show()
            } catch (e: Exception) {
                Log.e(TAG, "activateSystem error", e)
                runOnUiThread {
                    try { hideProgress() } catch (ignored: Exception) {}
                    Toast.makeText(this@SetupWizardActivity, "خطأ في التفعيل: ${e.message}", Toast.LENGTH_LONG).show()
                }
            }
        }
    }

    private fun startServices() {
        val services = listOf(
            com.dualpersona.system.service.SystemService::class.java,
            com.dualpersona.system.service.GuardService::class.java
        )
        for (svc in services) {
            try {
                val intent = Intent(this, svc)
                if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
                    startForegroundService(intent)
                } else {
                    startService(intent)
                }
            } catch (e: Exception) {
                Log.e(TAG, "Failed to start ${svc.simpleName}", e)
            }
        }
    }

    // ==================================================================
    // إعداد مكتمل مسبقاً
    // ==================================================================

    private fun showSetupAlreadyDone(prefs: PreferencesManager) {
        val stealthManager = StealthManager(this)
        AlertDialog.Builder(this)
            .setTitle("النظام نشط بالفعل")
            .setMessage("الرمز السري: *#*#${stealthManager.getSecretCode()}#*#*")
            .setPositiveButton("إعادة التكوين") { _, _ ->
                prefs.setSetupComplete(false)
                prefs.setCurrentSetupStep(0)
                stealthManager.showLauncherIcon()
                showStep(0)
            }
            .setNegativeButton("إغلاق") { _, _ -> finish() }
            .show()
    }

    // ==================================================================
    // أدوات إنشاء العروض
    // ==================================================================

    private fun makeLabel(text: String): android.widget.TextView {
        return android.widget.TextView(this).apply {
            this.text = text
            textSize = 16f
            setPadding(0, 16, 0, 8)
            setTypeface(null, android.graphics.Typeface.BOLD)
        }
    }

    private fun makeTextView(text: String, size: Float, color: Int): android.widget.TextView {
        return android.widget.TextView(this).apply {
            this.text = text
            textSize = size
            if (color != 0) setTextColor(color)
            setPadding(0, 4, 0, 4)
        }
    }

    private fun makeSeparator(): android.view.View {
        return android.view.View(this).apply {
            setBackgroundColor(getColor(android.R.color.darker_gray))
            layoutParams = LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.MATCH_PARENT, 1
            ).apply { setMargins(0, 24, 0, 24) }
        }
    }

    // ==================================================================
    // مربع حوار التقدم
    // ==================================================================

    private fun showProgress(message: String) {
        try {
            if (isFinishing) return
            hideProgress()
            progressDialog = AlertDialog.Builder(this)
                .setMessage(message)
                .setCancelable(false)
                .create()
            progressDialog?.show()
        } catch (e: Exception) {}
    }

    private fun hideProgress() {
        try {
            if (progressDialog?.isShowing == true) progressDialog?.dismiss()
            progressDialog = null
        } catch (e: Exception) { progressDialog = null }
    }

    // ==================================================================
    // دورة الحياة
    // ==================================================================

    override fun onDestroy() {
        try { hideProgress() } catch (e: Exception) {}
        super.onDestroy()
    }

    override fun onBackPressed() {
        try {
            if (currentStep > 0) showStep(currentStep - 1)
            else super.onBackPressed()
        } catch (e: Exception) { super.onBackPressed() }
    }
}
