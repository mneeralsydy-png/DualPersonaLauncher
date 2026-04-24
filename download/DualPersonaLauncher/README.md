# Dual Persona System

## System-Integrated Dual Profile Manager for Android

<p align="center">
  <img src="https://img.shields.io/badge/Platform-Android_10+-green" />
  <img src="https://img.shields.io/badge/Language-Kotlin-blue" />
  <img src="https://img.shields.io/badge/Min_SDK-28-orange" />
  <img src="https://img.shields.io/badge/Target_SDK-34-red" />
  <img src="https://img.shields.io/badge/License-MIT-yellow" />
</p>

---

## Overview

Dual Persona System transforms a single Android device into two completely separate phones, integrated directly at the **system level**. It is NOT a launcher — it uses Android's native multi-user architecture to create two fully isolated environments.

### How It Works

1. **System Lock Screen**: Uses the standard Android lock screen (PIN / Pattern / Fingerprint)
2. **Two Credentials**: Each profile has its own lock screen credential
3. **Automatic Switching**: When you unlock with credential A, you get Profile A. Credential B gives you Profile B
4. **Complete Isolation**: Each profile has independent apps, data, photos, contacts, messages, and settings
5. **Stealth Mode**: After setup, the app disappears completely from the phone

### NOT a Launcher

This is a **system-level integration tool**, not a home screen replacement:
- No custom lock screen
- No custom home screen
- No custom app drawer
- Uses Android's native lock screen and user system
- Completely invisible after setup

---

## Features

### Core Functionality
| Feature | Description |
|---------|-------------|
| **Dual User Profiles** | Creates a secondary Android user with complete data isolation |
| **System Lock Screen** | Uses native Android PIN/Pattern/Fingerprint |
| **Auto Profile Switching** | System automatically loads the correct profile based on unlock credential |
| **Data Isolation** | Separate storage, apps, contacts, photos, messages per profile |
| **Stealth Mode** | App disappears after setup — accessible only via secret dialer code |
| **Secret Dialer Access** | Access dashboard by dialing *#*#CODE#*#* |
| **Device Admin** | System-level permissions for full user management |

### Security
| Feature | Description |
|---------|-------------|
| **AES-256-GCM Encryption** | All app data encrypted at rest |
| **Android Keystore** | Keys stored in hardware-backed keystore |
| **Encrypted Preferences** | All settings encrypted using EncryptedSharedPreferences |
| **Security Logging** | All events logged with timestamps and severity |
| **Isolation Verification** | Periodic checks to ensure data isolation is maintained |
| **Suspicious Activity Detection** | Monitors for cross-user data access attempts |
| **Failed Attempt Tracking** | Lockout protection after too many wrong credentials |

### Management
| Feature | Description |
|---------|-------------|
| **Hidden Dashboard** | Full control panel accessible via secret code |
| **User Switching** | Switch between profiles from dashboard |
| **Profile Customization** | Set names, themes, notification policies per profile |
| **Security Logs** | View all security events |
| **Secret Code Customization** | Change the dialer access code |
| **Complete Reset** | Remove secondary user and reset system |

---

## Technical Architecture

### System Integration

```
┌─────────────────────────────────────────────────────┐
│                   Android System                      │
│                                                       │
│  ┌──────────────────┐    ┌──────────────────┐        │
│  │    User A (0)    │    │    User B (1)    │        │
│  │                  │    │                  │        │
│  │  - Own PIN       │    │  - Own PIN       │        │
│  │  - Own Apps      │    │  - Own Apps      │        │
│  │  - Own Data      │    │  - Own Data      │        │
│  │  - Own Storage   │    │  - Own Storage   │        │
│  │  - Own Contacts  │    │  - Own Contacts  │        │
│  └──────────────────┘    └──────────────────┘        │
│                                                       │
│  ┌─────────────────────────────────────┐              │
│  │         Dual Persona System          │              │
│  │  ┌─────────────┐ ┌──────────────┐  │              │
│  │  │SystemService│ │ GuardService │  │              │
│  │  └─────────────┘ └──────────────┘  │              │
│  │  ┌──────────────┐ ┌─────────────┐  │              │
│  │  │StealthManager│ │ DataGuard   │  │              │
│  │  └──────────────┘ └─────────────┘  │              │
│  └─────────────────────────────────────┘              │
└─────────────────────────────────────────────────────┘
```

### Key Components

```
com.dualpersona.system/
├── DualPersonaApp.kt              # Application class
├── core/
│   ├── SystemUserManager.kt       # Android UserManager integration
│   ├── CredentialManager.kt       # Lock screen credential management
│   ├── StealthManager.kt          # App hiding/revealing
│   ├── EnvironmentConfig.kt       # Per-profile configuration
│   └── DataGuard.kt               # Data isolation enforcement
├── ui/
│   ├── setup/
│   │   └── SetupWizardActivity.kt # Multi-step setup wizard
│   └── dashboard/
│       └── DashboardActivity.kt   # Hidden management panel
├── receiver/
│   ├── DualPersonaAdmin.kt        # Device Admin receiver
│   ├── BootReceiver.kt            # Auto-start on boot
│   ├── UserSwitchReceiver.kt      # Monitor user switches
│   └── SecretDialReceiver.kt      # Dialer code access
├── service/
│   ├── SystemService.kt           # Main background service
│   └── GuardService.kt            # Security monitoring
└── data/
    ├── PreferencesManager.kt      # Encrypted preferences
    └── SecurityLog.kt             # Security event logging
```

---

## Setup Process

### Step 1: Welcome
Overview of what Dual Persona System does.

### Step 2: Permissions
- **Device Administrator**: Required for system-level user management
- Notification Access: For service management

### Step 3: Configure Profile A
- Set profile name (e.g., "Personal", "Main")
- Choose credential type (PIN/Pattern/Password/Fingerprint)
- Set credential through Android Settings

### Step 4: Configure Profile B
- Set profile name (e.g., "Work", "Private")
- Choose credential type
- App creates secondary Android user
- Set credential for Profile B through Android Settings

### Step 5: Security Settings
- Set secret dialer code (default: 7890)
- Enable stealth mode
- Configure max failed attempts

### Step 6: Activate
- Services start in background
- Stealth mode activates (if enabled)
- App disappears from device

---

## Usage After Setup

### Switching Profiles
1. Lock your phone (power button)
2. On the lock screen, enter the credential for the desired profile
3. Android automatically loads that profile

### Accessing Hidden Dashboard
1. Open the phone dialer
2. Dial your secret code: *#*#7890#*#*
3. Dashboard appears with full management options
4. Dashboard auto-hides after 30 seconds

### Switching via Dashboard
1. Open dashboard via secret code
2. Tap "Switch to Profile A" or "Switch to Profile B"

---

## Requirements

- **Android 9+ (API 28)** minimum
- **Device Admin permissions** (granted during setup)
- **Multi-user support** (most modern phones support this)
- **Fingerprint hardware** (optional, for biometric credentials)

### Device Compatibility
- Works best on stock/near-stock Android (Pixel, Motorola, Nokia, etc.)
- Some manufacturers may restrict multi-user features
- Samsung devices may have limitations ( Knox )
- Root NOT required but provides enhanced functionality

---

## Security Model

### Data Isolation
Android's multi-user architecture provides kernel-level isolation:
- Each user has a separate Linux UID
- Each user has separate storage partitions (`/data/user/<id>/`)
- Apps cannot access other users' data
- File-based encryption (FBE) ensures data at rest is encrypted

### Encryption
- All app preferences encrypted with **AES-256-GCM**
- Keys stored in **Android Keystore** (hardware-backed)
- PBKDF2 key derivation for sensitive operations
- EncryptedSharedPreferences for all settings

### Monitoring
- Periodic isolation verification (every 5 minutes)
- Security event logging with severity levels
- Suspicious activity detection and alerting
- Failed authentication tracking with lockout

---

## Build Instructions

### Prerequisites
- Android Studio Hedgehog or newer
- JDK 17
- Android SDK 34
- Gradle 8.5+

### Build Steps
```bash
# Clone the repository
git clone https://github.com/mneeralsydy-png/DualPersonaLauncher.git
cd DualPersonaLauncher

# Build debug APK
./gradlew assembleDebug

# Build release APK
./gradlew assembleRelease

# APK location
# Debug: app/build/outputs/apk/debug/app-debug.apk
# Release: app/build/outputs/apk/release/app-release.apk
```

---

## Important Notes

1. **After setup, the app is NOT visible** — it runs entirely in the background
2. **Each user sets their own credential** through Android Settings > Security
3. **The system lock screen is unchanged** — it works exactly as before
4. **Access the app ONLY via secret dialer code** (*#*#CODE#*#*)
5. **Secondary user data is completely separate** — apps, photos, contacts, messages
6. **Factory reset removes both profiles** — backup important data before resetting

---

## How This Differs from a Launcher

| Feature | This App | Traditional Launcher |
|---------|----------|---------------------|
| Lock Screen | Uses system lock screen | Custom lock screen |
| Home Screen | System default | Custom home screen |
| User Isolation | Kernel-level (separate UIDs) | App-level (same user) |
| Data Separation | Complete (different partitions) | Simulated |
| Credential Integration | System credentials | App-managed credentials |
| Visibility | Completely invisible | Visible as launcher |
| User Switching | Native Android user switching | App-level switching |
| System Integration | Full (Device Admin) | Limited (just launcher) |

---

## License

MIT License - Free for personal and commercial use.
