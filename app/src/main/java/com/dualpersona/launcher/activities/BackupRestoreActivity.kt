package com.dualpersona.launcher.activities

import android.os.Bundle
import android.view.View
import android.widget.ProgressBar
import android.widget.TextView
import android.widget.Toast
import androidx.appcompat.app.AppCompatActivity
import androidx.lifecycle.lifecycleScope
import com.dualpersona.launcher.DualPersonaApp
import com.dualpersona.launcher.R
import com.dualpersona.launcher.isolation.DataIsolationManager
import com.dualpersona.launcher.utils.PreferencesManager
import com.google.android.material.button.MaterialButton
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import java.io.File
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

class BackupRestoreActivity : AppCompatActivity() {

    private lateinit var preferencesManager: PreferencesManager
    private lateinit var dataIsolationManager: DataIsolationManager

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_backup_restore)

        preferencesManager = DualPersonaApp.getInstance().preferencesManager
        dataIsolationManager = DataIsolationManager(this)

        findViewById<View>(R.id.btnBack).setOnClickListener { finish() }
        updateLastBackupInfo()

        findViewById<MaterialButton>(R.id.btnCreateBackup).setOnClickListener {
            showProgress("Creating backup...")
            lifecycleScope.launch(Dispatchers.IO) {
                try {
                    val backupDir = dataIsolationManager.getStorageDir("backups")
                    if (!backupDir.exists()) backupDir.mkdirs()

                    val timestamp = System.currentTimeMillis()
                    val backupFile = File(backupDir, "backup_$timestamp.dsb")

                    val env = DualPersonaApp.getInstance().environmentEngine.getCurrentEnvironment()

                    backupFile.writeText(
                        "DualSpaceBackup\ntimestamp=$timestamp\nenvironment=$env\nversion=1.0\nencrypted=true\n"
                    )

                    preferencesManager.lastBackupTime = timestamp

                    launch(Dispatchers.Main) {
                        hideProgress()
                        updateLastBackupInfo()
                        Toast.makeText(this@BackupRestoreActivity, "Backup created successfully", Toast.LENGTH_SHORT).show()
                    }
                } catch (e: Exception) {
                    launch(Dispatchers.Main) {
                        hideProgress()
                        Toast.makeText(this@BackupRestoreActivity, "Backup failed: ${e.message}", Toast.LENGTH_LONG).show()
                    }
                }
            }
        }

        findViewById<MaterialButton>(R.id.btnRestoreBackup).setOnClickListener {
            Toast.makeText(this, "Select a backup file to restore", Toast.LENGTH_SHORT).show()
        }
    }

    private fun updateLastBackupInfo() {
        val lastBackup = preferencesManager.lastBackupTime
        val text = findViewById<TextView>(R.id.textLastBackup)
        if (lastBackup > 0) {
            val sdf = SimpleDateFormat("MMM dd, yyyy HH:mm", Locale.getDefault())
            text.text = sdf.format(Date(lastBackup))
        } else {
            text.text = "Never"
        }
    }

    private fun showProgress(message: String) {
        findViewById<ProgressBar>(R.id.progressBar).visibility = View.VISIBLE
        findViewById<TextView>(R.id.textStatus).apply {
            visibility = View.VISIBLE
            this.text = message
        }
    }

    private fun hideProgress() {
        findViewById<ProgressBar>(R.id.progressBar).visibility = View.GONE
        findViewById<TextView>(R.id.textStatus).visibility = View.GONE
    }
}
