package com.dualpersona.system.receiver

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.os.UserManager
import com.dualpersona.system.core.DataGuard
import com.dualpersona.system.data.PreferencesManager
import com.dualpersona.system.data.SecurityLog

/**
 * UserSwitchReceiver - Monitors user profile switches
 *
 * Listens for:
 * - ACTION_USER_SWITCHED: When the active user changes
 * - ACTION_USER_FOREGROUND: When a user comes to foreground
 * - ACTION_USER_BACKGROUND: When a user goes to background
 *
 * This receiver logs all user switches and can trigger
 * security measures when unexpected switches occur.
 */
class UserSwitchReceiver : BroadcastReceiver() {

    override fun onReceive(context: Context, intent: Intent) {
        if (intent.action == null) return

        val prefs = PreferencesManager(context)
        if (!prefs.isSetupComplete()) return

        val dataGuard = DataGuard(context)

        when (intent.action) {
            Intent.ACTION_USER_SWITCHED -> {
                val newUserHandle = intent.getParcelableExtra<android.os.UserHandle>(
                    Intent.EXTRA_USER)
                val userId = getUserId(newUserHandle)
                val isSecondary = userId != 0

                SecurityLog.log(context, "INFO", "user_switched",
                    "Switched to user ID: $userId " +
                    "(isSecondary: $isSecondary)")

                // Run isolation verification after switch
                dataGuard.verifyIsolation()

                // Save current user state
                prefs.setProfileName(userId, if (isSecondary) "User B" else "User A")
            }

            Intent.ACTION_USER_FOREGROUND -> {
                SecurityLog.log(context, "INFO", "user_foreground",
                    "User brought to foreground")
            }

            Intent.ACTION_USER_BACKGROUND -> {
                SecurityLog.log(context, "INFO", "user_background",
                    "User sent to background")
            }
        }
    }

    private fun getUserId(handle: android.os.UserHandle?): Int {
        return try {
            handle?.let {
                val method = android.os.UserHandle::class.java
                    .getDeclaredMethod("getIdentifier")
                method.invoke(it) as Int
            } ?: -1
        } catch (e: Exception) {
            -1
        }
    }
}
