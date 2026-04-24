package com.dualpersona.launcher.receiver

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import com.dualpersona.launcher.DualPersonaApp
import com.dualpersona.launcher.activities.LockScreenActivity
import com.dualpersona.launcher.utils.PreferencesManager

/**
 * Receiver that monitors screen on/off events
 * to trigger the lock screen when auto-lock is enabled.
 */
class ScreenReceiver : BroadcastReceiver() {

    override fun onReceive(context: Context, intent: Intent?) {
        val prefs = PreferencesManager(context)

        when (intent?.action) {
            Intent.ACTION_SCREEN_OFF -> {
                if (prefs.isAutoLockEnabled) {
                    showLockScreen(context)
                }
            }
            Intent.ACTION_USER_PRESENT -> {
                // User is present — lock screen will handle auth
            }
        }
    }

    private fun showLockScreen(context: Context) {
        val lockIntent = Intent(context, LockScreenActivity::class.java).apply {
            flags = Intent.FLAG_ACTIVITY_NEW_TASK or
                    Intent.FLAG_ACTIVITY_CLEAR_TOP or
                    Intent.FLAG_ACTIVITY_SINGLE_TOP
        }
        context.startActivity(lockIntent)
    }
}
