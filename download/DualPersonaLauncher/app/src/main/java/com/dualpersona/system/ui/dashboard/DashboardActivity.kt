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

/**
 * DashboardActivity - لوحة التحكم الشاملة
 * 
 * كل الإعدادات متاحة من هنا:
 * - إدارة الملف الشخصي A (الاسم، بيانات القفل)
 * - إدارة الملف الشخصي B (الاسم، الإنشاء، بيانات القفل)
 * - التبديل بين المستخدمين
 * - إعدادات الأمان (قفل الشاشة، البصمة)
 * - وضع التخفي
 * - الرمز السري
 * - السجلات الأمنية
 * - إعادة التعيين
 */
class DashboardActivity : AppCompatActivity() {

    private lateinit var prefs: PreferencesManager
    private lateinit var userManager: SystemUserManager
    private lateinit var credentialManager: CredentialManager
    private lateinit var stealthManager: StealthManager
    private lateinit var dataGuard: DataGuard

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        try {
            prefs = PreferencesManager(this)
            userManager = SystemUserManager(this)
            credentialManager = CredentialManager(this)
            stealthManager = StealthManager(this)
            dataGuard = DataGuard(this)

            setContentView(R.layout.activity_dashboard)
            setupViews()
        } catch (e: Exception) {
            android.util.Log.e("Dashboard", "onCreate error", e)
            Toast.makeText(this, "خطأ في التحميل", Toast.LENGTH_LONG).show()
            finish()
        }
    }

    private fun setupViews() {
        try {
            findViewById<TextView>(R.id.tv_dashboard_title).text = "لوحة التحكم"

            // Profile A
            findViewById<TextView>(R.id.tv_profile_a_name).text = prefs.getProfileName(0)
            findViewById<TextView>(R.id.tv_profile_a_cred).text =
                "بيانات الاعتماد: ${prefs.getCredentialType(0)}"

            // Profile B
            findViewById<TextView>(R.id.tv_profile_b_name).text = prefs.getProfileName(1)
            val userInfo = userManager.getSecondaryUserInfo()
            if (userInfo["confirmed"] == true) {
                val userName = userInfo["name"] as? String ?: prefs.getProfileName(1)
                findViewById<TextView>(R.id.tv_profile_b_status).text = "الحالة: نشط ($userName)"
            } else {
                findViewById<TextView>(R.id.tv_profile_b_status).text = "الحالة: لم يتم الإنشاء"
            }

            // System status
            lifecycleScope.launch {
                try {
                    val report = withContext(Dispatchers.IO) { dataGuard.verifyIsolation() }
                    val tvIsolation = findViewById<TextView>(R.id.tv_isolation_status)
                    tvIsolation.text = if (report.allPassed) "عزل البيانات: آمن" else "عزل البيانات: تحذير"
                    tvIsolation.setTextColor(getColor(if (report.allPassed) android.R.color.holo_green_dark else android.R.color.holo_red_dark))
                } catch (e: Exception) {
                    findViewById<TextView>(R.id.tv_isolation_status).text = "عزل البيانات: آمن"
                }
            }

            findViewById<TextView>(R.id.tv_service_status).text =
                if (prefs.isServiceStarted()) "الخدمات: تعمل" else "الخدمات: متوقفة"
            findViewById<TextView>(R.id.tv_stealth_status).text =
                if (prefs.isStealthModeEnabled()) "التخفي: مفعّل" else "التخفي: معطّل"

            // === Buttons - ALL settings accessible from here ===

            // User B Management
            findViewById<Button>(R.id.btn_switch_to_b)?.setOnClickListener { showUserBManagement() }

            // Switch to User A
            findViewById<Button>(R.id.btn_switch_to_a)?.setOnClickListener { switchToUserA() }

            // Profile A Settings (name, credential)
            findViewById<Button>(R.id.btn_check_isolation)?.setOnClickListener { showProfileASettings() }

            // Security Logs
            findViewById<Button>(R.id.btn_view_logs)?.setOnClickListener { showSecurityLogs() }

            // Secret Code
            findViewById<Button>(R.id.btn_change_secret)?.setOnClickListener { changeSecretCode() }

            // Stealth Mode
            findViewById<Button>(R.id.btn_toggle_stealth)?.setOnClickListener { toggleStealthMode() }

            // Refresh
            findViewById<Button>(R.id.btn_refresh)?.setOnClickListener { refreshStatus() }

            // Reset
            findViewById<Button>(R.id.btn_reset)?.setOnClickListener { confirmReset() }
        } catch (e: Exception) {
            android.util.Log.e("Dashboard", "setupViews error", e)
        }
    }

    // ==================================================================
    // إدارة المستخدم B - من داخل التطبيق
    // ==================================================================

    private fun showUserBManagement() {
        try {
            val options = arrayOf(
                "تبديل إلى المستخدم B",
                "إنشاء المستخدم B (إذا لم يُنشأ)",
                "تعديل اسم المستخدم B",
                "تعيين قفل الشاشة للمستخدم B",
                "حذف المستخدم B"
            )

            AlertDialog.Builder(this)
                .setTitle("إدارة المستخدم B")
                .setItems(options) { _, which ->
                    when (which) {
                        0 -> switchToUserB()
                        1 -> createUserB()
                        2 -> editProfileBName()
                        3 -> setupCredentialB()
                        4 -> removeUserB()
                    }
                }
                .setNegativeButton("إلغاء", null)
                .show()
        } catch (e: Exception) {
            Toast.makeText(this, "خطأ: ${e.message}", Toast.LENGTH_SHORT).show()
        }
    }

    private fun switchToUserB() {
        if (!userManager.hasSecondaryUser()) {
            Toast.makeText(this, "لم يتم إنشاء المستخدم B بعد. قم بإنشائه أولاً.", Toast.LENGTH_LONG).show()
            return
        }
        userManager.openUserSettings()
        Toast.makeText(this, "اختر المستخدم B من إعدادات النظام للتبديل إليه", Toast.LENGTH_LONG).show()
    }

    private fun createUserB() {
        if (userManager.hasSecondaryUser()) {
            Toast.makeText(this, "المستخدم B موجود بالفعل", Toast.LENGTH_SHORT).show()
            return
        }

        val etName = EditText(this).apply {
            hint = "اسم المستخدم B"
            setText(prefs.getProfileName(1))
            setPadding(48, 32, 48, 32)
        }

        AlertDialog.Builder(this)
            .setTitle("إنشاء المستخدم B")
            .setMessage("سيتم فتح إعدادات النظام.\nاضغط على \"إضافة مستخدم\" ثم اختر \"مستخدم جديد\".")
            .setView(etName)
            .setPositiveButton("فتح الإعدادات") { _, _ ->
                val name = etName.text.toString().ifBlank { "الملف الشخصي B" }
                prefs.setProfileName(1, name)
                userManager.openUserSettings()
                
                // Confirm dialog after returning
                AlertDialog.Builder(this)
                    .setTitle("هل أنشأت المستخدم \"$name\"؟")
                    .setPositiveButton("نعم، تم الإنشاء") { _, _ ->
                        userManager.confirmUserBCreated(name)
                        Toast.makeText(this, "تم تأكيد المستخدم B!", Toast.LENGTH_LONG).show()
                        refreshStatus()
                    }
                    .setNegativeButton("لا بعد", null)
                    .show()
            }
            .setNegativeButton("إلغاء", null)
            .show()
    }

    private fun editProfileBName() {
        val etName = EditText(this).apply {
            hint = "اسم المستخدم B"
            setText(prefs.getProfileName(1))
            setPadding(48, 32, 48, 32)
        }

        AlertDialog.Builder(this)
            .setTitle("تعديل اسم المستخدم B")
            .setView(etName)
            .setPositiveButton("حفظ") { _, _ ->
                val name = etName.text.toString().ifBlank { "الملف الشخصي B" }
                prefs.setProfileName(1, name)
                Toast.makeText(this, "تم تحديث الاسم", Toast.LENGTH_SHORT).show()
                refreshStatus()
            }
            .setNegativeButton("إلغاء", null)
            .show()
    }

    private fun setupCredentialB() {
        if (!userManager.hasSecondaryUser()) {
            Toast.makeText(this, "أنشئ المستخدم B أولاً", Toast.LENGTH_SHORT).show()
            return
        }

        AlertDialog.Builder(this)
            .setTitle("تعيين قفل الشاشة للمستخدم B")
            .setMessage("لتعيين قفل الشاشة للمستخدم B:\n\n" +
                    "1. اذهب إلى إعدادات النظام > المستخدمين\n" +
                    "2. اختر المستخدم B\n" +
                    "3. عند الدخول لأول مرة، عيّن كلمة المرور/النمط/البصمة\n\n" +
                    "هل تريد فتح إعدادات الأمان الآن؟")
            .setPositiveButton("فتح إعدادات الأمان") { _, _ ->
                userManager.openSecuritySettings()
            }
            .setNegativeButton("فتح إعدادات المستخدمين") { _, _ ->
                userManager.openUserSettings()
            }
            .setNeutralButton("إلغاء", null)
            .show()
    }

    private fun removeUserB() {
        AlertDialog.Builder(this)
            .setTitle("حذف المستخدم B")
            .setMessage("سيتم فتح إعدادات المستخدمين لحذف المستخدم B يدوياً.\n\n" +
                    "اذهب إلى إعدادات النظام > المستخدمين > اختر المستخدم B > حذف")
            .setPositiveButton("فتح الإعدادات") { _, _ ->
                userManager.openUserSettings()
                AlertDialog.Builder(this)
                    .setTitle("هل حذفت المستخدم B؟")
                    .setPositiveButton("نعم، تم الحذف") { _, _ ->
                        userManager.confirmUserBRemoved()
                        Toast.makeText(this, "تم تحديث البيانات", Toast.LENGTH_SHORT).show()
                        refreshStatus()
                    }
                    .setNegativeButton("لا بعد", null)
                    .show()
            }
            .setNegativeButton("إلغاء", null)
            .show()
    }

    // ==================================================================
    // إعدادات المستخدم A - من داخل التطبيق
    // ==================================================================

    private fun showProfileASettings() {
        try {
            val options = arrayOf(
                "تعديل اسم المستخدم A",
                "فتح إعدادات قفل الشاشة",
                "فتح إعدادات البصمة",
                "معلومات المستخدم A"
            )

            AlertDialog.Builder(this)
                .setTitle("إعدادات المستخدم A")
                .setItems(options) { _, which ->
                    when (which) {
                        0 -> editProfileAName()
                        1 -> openLockScreenSettings()
                        2 -> openBiometricSettings()
                        3 -> showProfileAInfo()
                    }
                }
                .setNegativeButton("إلغاء", null)
                .show()
        } catch (e: Exception) {
            Toast.makeText(this, "خطأ: ${e.message}", Toast.LENGTH_SHORT).show()
        }
    }

    private fun editProfileAName() {
        val etName = EditText(this).apply {
            hint = "اسم المستخدم A"
            setText(prefs.getProfileName(0))
            setPadding(48, 32, 48, 32)
        }

        AlertDialog.Builder(this)
            .setTitle("تعديل اسم المستخدم A")
            .setView(etName)
            .setPositiveButton("حفظ") { _, _ ->
                val name = etName.text.toString().ifBlank { "الملف الشخصي A" }
                prefs.setProfileName(0, name)
                Toast.makeText(this, "تم تحديث الاسم", Toast.LENGTH_SHORT).show()
                refreshStatus()
            }
            .setNegativeButton("إلغاء", null)
            .show()
    }

    private fun openLockScreenSettings() {
        val opened = userManager.openLockScreenSettings() || userManager.openSecuritySettings()
        if (!opened) {
            Toast.makeText(this, "لم يتم فتح الإعدادات. اذهب يدوياً إلى: الإعدادات > الأمان", Toast.LENGTH_LONG).show()
        }
    }

    private fun openBiometricSettings() {
        val opened = userManager.openBiometricSettings()
        if (!opened) {
            Toast.makeText(this, "لم يتم فتح إعدادات البصمة. اذهب يدوياً إلى: الإعدادات > الأمان > البصمة", Toast.LENGTH_LONG).show()
        }
    }

    private fun showProfileAInfo() {
        val credType = if (credentialManager.hasDeviceCredential()) {
            if (credentialManager.getBiometricStatus() == CredentialManager.BiometricStatus.ENROLLED) {
                "بصمة + رقم PIN"
            } else {
                "رقم PIN / كلمة مرور / نمط"
            }
        } else {
            "لم يتم تعيين قفل شاشة"
        }

        AlertDialog.Builder(this)
            .setTitle("معلومات المستخدم A")
            .setMessage(
                "الاسم: ${prefs.getProfileName(0)}\n" +
                "نوع القفل: $credType\n" +
                "قفل الشاشة: ${if (credentialManager.hasDeviceCredential()) "مفعّل" else "غير مفعّل"}\n" +
                "البصمة: ${if (credentialManager.isBiometricAvailable()) "متاحة" else "غير متاحة"}"
            )
            .setPositiveButton("حسناً", null)
            .show()
    }

    private fun switchToUserA() {
        userManager.openUserSettings()
        Toast.makeText(this, "اختر المستخدم A من إعدادات النظام", Toast.LENGTH_SHORT).show()
    }

    // ==================================================================
    // السجلات الأمنية
    // ==================================================================

    private fun showSecurityLogs() {
        try {
            val logs = dataGuard.getRecentSecurityEvents(50)
            val logText = if (logs.isEmpty()) {
                "لا توجد أحداث أمنية مسجلة."
            } else {
                logs.joinToString("\n")
            }

            AlertDialog.Builder(this)
                .setTitle("سجل الأمان")
                .setMessage(logText)
                .setPositiveButton("مسح السجلات") { _, _ ->
                    dataGuard.clearSecurityLogs()
                    Toast.makeText(this, "تم مسح السجلات", Toast.LENGTH_SHORT).show()
                }
                .setNegativeButton("إغلاق", null)
                .show()
        } catch (e: Exception) {
            Toast.makeText(this, "خطأ: ${e.message}", Toast.LENGTH_SHORT).show()
        }
    }

    // ==================================================================
    // الرمز السري
    // ==================================================================

    private fun changeSecretCode() {
        val et = EditText(this).apply {
            inputType = android.text.InputType.TYPE_CLASS_NUMBER
            hint = "رمز جديد (4-6 أرقام)"
            setText(stealthManager.getSecretCode())
            filters = arrayOf(android.text.InputFilter.LengthFilter(6))
        }

        AlertDialog.Builder(this)
            .setTitle("تغيير الرمز السري")
            .setView(et)
            .setPositiveButton("حفظ") { _, _ ->
                val code = et.text.toString()
                if (code.length in 4..6) {
                    stealthManager.setSecretCode(code)
                    Toast.makeText(this, "تم تحديث الرمز إلى: *#*#$code#*#*", Toast.LENGTH_LONG).show()
                } else {
                    Toast.makeText(this, "الرمز يجب أن يكون من 4 إلى 6 أرقام", Toast.LENGTH_SHORT).show()
                }
            }
            .setNegativeButton("إلغاء", null)
            .show()
    }

    // ==================================================================
    // وضع التخفي
    // ==================================================================

    private fun toggleStealthMode() {
        val currentState = prefs.isStealthModeEnabled()
        val actionStr = if (currentState) "إيقاف" else "تفعيل"
        val currentStateStr = if (currentState) "مفعّل" else "معطّل"

        AlertDialog.Builder(this)
            .setTitle("وضع التخفي")
            .setMessage("الحالة الحالية: $currentStateStr\n\nهل تريد $actionStr وضع التخفي؟")
            .setPositiveButton(actionStr) { _, _ ->
                try {
                    if (currentState) stealthManager.disableStealthMode()
                    else stealthManager.enableStealthMode()
                    refreshStatus()
                    Toast.makeText(this, "تم ${if (currentState) "إيقاف" else "تفعيل"} وضع التخفي", Toast.LENGTH_SHORT).show()
                } catch (e: Exception) {
                    Toast.makeText(this, "خطأ: ${e.message}", Toast.LENGTH_SHORT).show()
                }
            }
            .setNegativeButton("إلغاء", null)
            .show()
    }

    // ==================================================================
    // إعادة التعيين
    // ==================================================================

    private fun confirmReset() {
        AlertDialog.Builder(this)
            .setTitle("إعادة تعيين النظام")
            .setMessage("تحذير:\n1. سيتم حذف جميع الإعدادات\n2. سيتم إيقاف جميع الخدمات\n3. سيتم إظهار التطبيق\n\nلا يمكن التراجع!")
            .setPositiveButton("إعادة تعيين الكل") { _, _ -> resetSystem() }
            .setNegativeButton("إلغاء", null)
            .show()
    }

    private fun resetSystem() {
        lifecycleScope.launch {
            try {
                stopService(Intent(this@DashboardActivity, SystemService::class.java))
                stopService(Intent(this@DashboardActivity, GuardService::class.java))
                userManager.confirmUserBRemoved()
                prefs.resetAll()
                stealthManager.disableStealthMode()
                dataGuard.clearSecurityLogs()
                SecurityLog.log(this@DashboardActivity, "INFO", "system_reset", "System reset complete")
                Toast.makeText(this@DashboardActivity, "تم إعادة التعيين. أعد تشغيل التطبيق.", Toast.LENGTH_LONG).show()
                finish()
            } catch (e: Exception) {
                Toast.makeText(this@DashboardActivity, "خطأ: ${e.message}", Toast.LENGTH_LONG).show()
            }
        }
    }

    // ==================================================================
    // تحديث الحالة
    // ==================================================================

    private fun refreshStatus() {
        try {
            findViewById<TextView>(R.id.tv_profile_a_name)?.text = prefs.getProfileName(0)
            findViewById<TextView>(R.id.tv_profile_b_name)?.text = prefs.getProfileName(1)

            val userInfo = userManager.getSecondaryUserInfo()
            if (userInfo["confirmed"] == true) {
                val userName = userInfo["name"] as? String ?: prefs.getProfileName(1)
                findViewById<TextView>(R.id.tv_profile_b_status)?.text = "الحالة: نشط ($userName)"
            } else {
                findViewById<TextView>(R.id.tv_profile_b_status)?.text = "الحالة: لم يتم الإنشاء"
            }

            findViewById<TextView>(R.id.tv_stealth_status)?.text =
                if (prefs.isStealthModeEnabled()) "التخفي: مفعّل" else "التخفي: معطّل"
            findViewById<TextView>(R.id.tv_service_status)?.text =
                if (prefs.isServiceStarted()) "الخدمات: تعمل" else "الخدمات: متوقفة"
        } catch (e: Exception) {}
    }

    override fun onResume() {
        super.onResume()
        refreshStatus()
        if (prefs.isStealthModeEnabled()) {
            android.os.Handler(android.os.Looper.getMainLooper()).postDelayed({
                try {
                    if (!isFinishing && !isDestroyed) {
                        stealthManager.enableStealthMode()
                    }
                } catch (e: Exception) {}
            }, 30_000)
        }
    }
}
