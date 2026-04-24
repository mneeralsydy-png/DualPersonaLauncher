package com.dualpersona.launcher.activities

import android.os.Bundle
import android.widget.Button
import android.widget.EditText
import android.widget.Toast
import androidx.appcompat.app.AppCompatActivity
import com.dualpersona.launcher.R
import com.dualpersona.launcher.security.AuthManager
import com.dualpersona.launcher.utils.EnvironmentType
import com.dualpersona.launcher.utils.PreferencesManager

/**
 * Emergency Setup Activity.
 * Allows configuring the emergency/decoy environment.
 */
class EmergencySetupActivity : AppCompatActivity() {

    private lateinit var authManager: AuthManager
    private lateinit var preferencesManager: PreferencesManager

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_emergency_setup)

        authManager = AuthManager(this)
        preferencesManager = PreferencesManager(this)

        findViewById<Button>(R.id.btnSetEmergencyPin).setOnClickListener {
            val pin = findViewById<EditText>(R.id.inputEmergencyPin).text.toString()
            if (pin.length >= 4) {
                authManager.setupPin(pin, EnvironmentType.EMERGENCY)
                Toast.makeText(this, "Emergency PIN set!", Toast.LENGTH_SHORT).show()
                finish()
            } else {
                Toast.makeText(this, "PIN must be at least 4 digits", Toast.LENGTH_SHORT).show()
            }
        }
    }
}
