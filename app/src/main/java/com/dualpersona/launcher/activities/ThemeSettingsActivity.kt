package com.dualpersona.launcher.activities

import android.os.Bundle
import android.view.View
import android.widget.SeekBar
import android.widget.TextView
import android.widget.Toast
import androidx.appcompat.app.AppCompatActivity
import androidx.appcompat.widget.SwitchCompat
import com.dualpersona.launcher.DualPersonaApp
import com.dualpersona.launcher.R
import com.dualpersona.launcher.utils.PreferencesManager

/**
 * Theme Settings Activity.
 * Allows customization of colors, grid size, dark mode.
 */
class ThemeSettingsActivity : AppCompatActivity() {

    private lateinit var preferencesManager: PreferencesManager

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_theme_settings)

        preferencesManager = DualPersonaApp.getInstance().preferencesManager

        findViewById<View>(R.id.btnBack).setOnClickListener { finish() }

        // Dark mode toggle
        findViewById<SwitchCompat>(R.id.switchDarkMode).apply {
            isChecked = preferencesManager.isDarkMode
            setOnCheckedChangeListener { _, isChecked ->
                preferencesManager.isDarkMode = isChecked
                Toast.makeText(this@ThemeSettingsActivity, "Restart to apply theme", Toast.LENGTH_SHORT).show()
            }
        }

        // Grid size
        val gridText = findViewById<TextView>(R.id.textGridSize)
        gridText.text = "${preferencesManager.gridColumns} x ${preferencesManager.gridColumns}"

        findViewById<SeekBar>(R.id.seekGridSize).apply {
            min = 3
            max = 6
            progress = preferencesManager.gridColumns
            setOnSeekBarChangeListener(object : SeekBar.OnSeekBarChangeListener {
                override fun onProgressChanged(seekBar: SeekBar?, progress: Int, fromUser: Boolean) {
                    gridText.text = "$progress x $progress"
                }
                override fun onStartTrackingTouch(seekBar: SeekBar?) {}
                override fun onStopTrackingTouch(seekBar: SeekBar?) {
                    preferencesManager.gridColumns = seekBar?.progress ?: 4
                    Toast.makeText(this@ThemeSettingsActivity, "Grid size updated", Toast.LENGTH_SHORT).show()
                }
            })
        }
    }
}
