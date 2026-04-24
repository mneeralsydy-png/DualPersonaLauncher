package com.dualpersona.launcher.activities

import android.content.Intent
import android.os.Bundle
import android.os.Handler
import android.os.Looper
import androidx.appcompat.app.AppCompatActivity
import com.dualpersona.launcher.R
import com.dualpersona.launcher.utils.PreferencesManager

/**
 * Splash screen — decides whether to show setup wizard or lock screen.
 */
class SplashActivity : AppCompatActivity() {

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_splash)

        val prefs = PreferencesManager(this)

        Handler(Looper.getMainLooper()).postDelayed({
            val target = if (prefs.isSetupComplete) {
                LockScreenActivity::class.java
            } else {
                SetupWizardActivity::class.java
            }

            startActivity(Intent(this, target).apply {
                flags = Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TASK
            })
            overridePendingTransition(android.R.anim.fade_in, android.R.anim.fade_out)
            finish()
        }, SPLASH_DELAY)
    }

    companion object {
        private const val SPLASH_DELAY = 1500L
    }
}
