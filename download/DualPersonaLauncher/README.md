# Dual Persona Launcher — Secure Dual Space OS

## 📱 نظرة عامة

تطبيق أندرويد يتيح إنشاء بيئتين (أو ثلاث) منفصلتين تمامًا داخل نفس الهاتف. يتم التبديل بين البيئات عبر إدخال رمز PIN مختلف عند شاشة القفل.

## 🎯 الميزات الرئيسية

### 🔐 شاشة قفل مخصصة
- إدخال PIN (4-6 أرقام)
- دعم البصمة (Biometric)
- أنيميشن على الأزرار والنقاط
- اهتزاز عند الإدخال والخطأ

### 🧱 بيئات متعددة
- **البيئة الأساسية (Primary Space)**: بيئة الهاتف العادية
- **البيئة المخفية (Hidden Space)**: بيئة سرية بتطبيقات وملفات منفصلة
- **بيئة الطوارئ (Emergency Space)**: بيئة وهمية لخداع من يجبرك على فتح الهاتف

### 📱 لانشر مزدوج
- واجهة مستخدم مختلفة لكل بيئة
- شبكة تطبيقات قابلة للتخصيص (3x3 إلى 6x6)
- درج تطبيقات (App Drawer) مع بحث
- دعم إخفاء التطبيقات

### 🔒 نظام أمان متقدم
- تشفير AES-256-GCM للبيانات
- تجزئة PBKDF2-HMAC-SHA256 للرموز
- Android KeyStore لحماية المفاتيح
- تشفير EncryptedSharedPreferences
- وضع التخفي (Stealth Mode)
- كشف التسلل (Intrusion Detection)
- التدمير الذاتي (Self Destruct)

### 🗂️ عزل البيانات
- مجلدات منفصلة لكل بيئة
- تشفير ملفات البيئة المخفية
- مسح آمن (Secure Wipe)
- نسخ احتياطي مشفر

## 🛠️ البنية التقنية

```
app/src/main/java/com/dualpersona/launcher/
├── DualPersonaApp.kt                    # Application class
├── activities/
│   ├── SplashActivity.kt                # شاشة البداية
│   ├── LockScreenActivity.kt            # شاشة القفل
│   ├── LauncherHomeActivity.kt          # الشاشة الرئيسية
│   ├── SetupWizardActivity.kt           # معالج الإعداد
│   ├── EnvironmentSettingsActivity.kt   # إعدادات البيئة
│   ├── SecuritySettingsActivity.kt      # إعدادات الأمان
│   ├── AppManagerActivity.kt            # إدارة التطبيقات
│   ├── ThemeSettingsActivity.kt         # إعدادات المظهر
│   ├── EmergencySetupActivity.kt        # إعداد الطوارئ
│   └── BackupRestoreActivity.kt         # النسخ الاحتياطي
├── security/
│   ├── EncryptionManager.kt             # AES-256-GCM + PBKDF2
│   ├── AuthManager.kt                   # إدارة المصادقة
│   └── StealthManager.kt               # وضع التخفي
├── engine/
│   └── EnvironmentEngine.kt             # محرك البيئات
├── isolation/
│   ├── DataIsolationManager.kt          # عزل الملفات
│   └── SandboxManager.kt                # إدارة الصناديق الرملية
├── launcher/
│   ├── HomeAppAdapter.kt                # محول الشاشة الرئيسية
│   └── AppDrawerAdapter.kt              # محول درج التطبيقات
├── data/
│   ├── AppDatabase.kt                   # قاعدة بيانات Room
│   ├── entity/
│   │   ├── AppInfoEntity.kt
│   │   ├── WallpaperEntity.kt
│   │   └── SecurityEventEntity.kt
│   └── dao/
│       ├── AppInfoDao.kt
│       ├── WallpaperDao.kt
│       └── SecurityEventDao.kt
├── service/
│   ├── LockScreenService.kt             # خدمة شاشة القفل
│   └── EnvironmentService.kt            # خدمة البيئة
├── receiver/
│   ├── BootReceiver.kt                  # مستقبل التشغيل
│   ├── ScreenReceiver.kt               # مستقبل الشاشة
│   └── DeviceAdminReceiver.kt          # مستقبل مسؤول الجهاز
└── utils/
    ├── Constants.kt                     # الثوابت
    └── PreferencesManager.kt            # إدارة التفضيلات
```

## 🚀 كيفية التشغيل

### المتطلبات
- **Android Studio**: Iguana (2023.2.1) أو أحدث
- **Gradle**: 8.5
- **Kotlin**: 1.9.22
- **Min SDK**: 28 (Android 9.0 Pie)
- **Target SDK**: 34 (Android 14)

### خطوات التثبيت

1. **استنساخ/نسخ المشروع**
   ```bash
   cd DualPersonaLauncher
   ```

2. **فتح المشروع**
   - افتح Android Studio
   - اختر "Open an existing Android Studio project"
   - حدد مجلد المشروع

3. **مزامنة Gradle**
   - انتظر حتى تكتمل مزامنة Gradle
   - إذا طُلب، قم بتثبيت SDK المطلوب

4. **تشغيل التطبيق**
   - اختر جهاز أو محاكي (API 28+)
   - اضغط Run

### الإعداد الأولي

عند فتح التطبيق لأول مرة:

1. **شاشة الترحيب** — معلومات عن التطبيق
2. **إعداد PIN الأساسي** — رمز البيئة العادية (مثلاً: 1234)
3. **تأكيد PIN الأساسي** — إعادة إدخال الرمز
4. **إعداد PIN المخفي** — رمز البيئة السرية (مثلاً: 4321)
5. **تأكيد PIN المخفي** — إعادة إدخال الرمز
6. **إعداد PIN الطوارئ** (اختياري) — رمز البيئة الوهمية
7. **البصمة** (اختياري) — تفعيل فتح بالبصمة
8. **إنهاء** — جاهز للاستخدام!

## 🔧 التقنيات المستخدمة

| التقنية | الاستخدام |
|---------|-----------|
| Kotlin | اللغة الأساسية |
| Android SDK 34 | منصة التطوير |
| Room Database | قاعدة البيانات المحلية |
| AES-256-GCM | تشفير البيانات |
| PBKDF2-HMAC-SHA256 | تجزئة كلمات المرور |
| Android KeyStore | تخزين آمن للمفاتيح |
| EncryptedSharedPreferences | تفضيلات مشفرة |
| BiometricPrompt | المصادقة بالبصمة |
| Material Design 3 | واجهة المستخدم |
| Coroutines + Flow | البرمجة غير المتزامنة |
| Work Manager | المهام في الخلفية |

## 🧪 سيناريو الاستخدام

1. المستخدم يشغل الهاتف ← تظهر شاشة القفل
2. يدخل `1234` ← يفتح الهاتف العادي (Primary Space)
3. يعيد القفل ← يدخل `4321` ← يفتح الهاتف السري (Hidden Space)
4. في حالة الإجبار ← يدخل `9999` ← يفتح بيئة وهمية (Emergency Space)

## ⚠️ التحديات والملاحظات

- **بدون Root**: العزل ليس 100% كاملاً (يعتمد على Work Profile)
- **مع Root**: يمكن تعديل AOSP لتحقيق عزل حقيقي
- **الأداء**: استخدام الخدمات قد يؤثر على البطارية
- **الشاشة الأصلية**: لا يمكن استبدال شاشة القفل الأصلية بالكامل بدون صلاحيات

## 📋 الصلاحيات المطلوبة

- `QUERY_ALL_PACKAGES` — لسرد جميع التطبيقات
- `USE_BIOMETRIC` — للبصمة
- `SYSTEM_ALERT_WINDOW` — لعرض شاشة القفل
- `FOREGROUND_SERVICE` — للخدمات الخلفية
- `RECEIVE_BOOT_COMPLETED` — للتشغيل عند الإقلاع
- `Device Admin` — لسياسات الأمان

## 📄 الرخصة

هذا المشروع لأغراض تعليمية وتطويرية.
