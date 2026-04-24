package com.dualpersona.launcher.activities

import android.content.Intent
import android.content.pm.PackageManager
import android.net.Uri
import android.os.Bundle
import android.provider.Settings
import android.view.View
import android.widget.EditText
import android.widget.TextView
import android.widget.Toast
import androidx.appcompat.app.AppCompatActivity
import androidx.lifecycle.lifecycleScope
import androidx.recyclerview.widget.GridLayoutManager
import androidx.recyclerview.widget.LinearLayoutManager
import androidx.recyclerview.widget.RecyclerView
import com.dualpersona.launcher.DualPersonaApp
import com.dualpersona.launcher.R
import com.dualpersona.launcher.data.dao.AppInfoDao
import com.dualpersona.launcher.data.entity.AppInfoEntity
import com.dualpersona.launcher.engine.EnvironmentEngine
import com.dualpersona.launcher.launcher.AppDrawerAdapter
import com.dualpersona.launcher.launcher.HomeAppAdapter
import com.dualpersona.launcher.utils.EnvironmentType
import com.dualpersona.launcher.utils.IntentExtras
import com.dualpersona.launcher.utils.PreferencesManager
import com.google.android.material.bottomnavigation.BottomNavigationView
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.collectLatest
import kotlinx.coroutines.launch

/**
 * Main Launcher Home Activity.
 * Acts as the home screen replacement, showing apps for the current environment.
 */
class LauncherHomeActivity : AppCompatActivity() {

    private lateinit var environmentEngine: EnvironmentEngine
    private lateinit var preferencesManager: PreferencesManager
    private lateinit var appDao: AppInfoDao

    private lateinit var homeAppGrid: RecyclerView
    private lateinit var drawerAppGrid: RecyclerView
    private lateinit var homeAppAdapter: HomeAppAdapter
    private lateinit var drawerAppAdapter: AppDrawerAdapter

    private lateinit var spaceIndicator: View
    private lateinit var spaceIndicatorText: TextView
    private lateinit var spaceIndicatorDot: View
    private lateinit var drawerSheet: View
    private lateinit var drawerSearch: EditText
    private lateinit var bottomNav: BottomNavigationView

    private var currentEnvironment = EnvironmentType.PRIMARY

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_launcher_home)

        initManagers()
        initViews()
        setupAdapters()
        observeApps()
        handleIntent()
    }

    private fun initManagers() {
        val app = DualPersonaApp.getInstance()
        environmentEngine = app.environmentEngine
        preferencesManager = app.preferencesManager
        appDao = app.database.appInfoDao()
        currentEnvironment = environmentEngine.getCurrentEnvironment()
    }

    private fun initViews() {
        homeAppGrid = findViewById(R.id.homeAppGrid)
        drawerAppGrid = findViewById(R.id.drawerAppGrid)
        spaceIndicator = findViewById(R.id.spaceIndicator)
        spaceIndicatorText = findViewById(R.id.spaceIndicatorText)
        spaceIndicatorDot = findViewById(R.id.spaceIndicatorDot)
        drawerSheet = findViewById(R.id.drawerSheet)
        drawerSearch = findViewById(R.id.drawerSearch)
        bottomNav = findViewById(R.id.bottomNavigation)

        // Update space indicator
        updateSpaceIndicator()

        // Bottom navigation
        bottomNav.setOnItemSelectedListener { item ->
            when (item.itemId) {
                R.id.nav_home -> {
                    drawerSheet.visibility = View.GONE
                    true
                }
                R.id.nav_apps -> {
                    drawerSheet.visibility = View.VISIBLE
                    true
                }
                R.id.nav_settings -> {
                    startActivity(Intent(this, EnvironmentSettingsActivity::class.java))
                    true
                }
                R.id.nav_switch -> {
                    environmentEngine.lockEnvironment()
                    true
                }
                else -> false
            }
        }

        // Drawer search
        drawerSearch.setOnEditorActionListener { _, _, _ ->
            filterDrawerApps(drawerSearch.text.toString())
            true
        }

        // Close drawer when clicking overlay
        drawerSheet.setOnClickListener { view ->
            if (view.id == R.id.drawerSheet) {
                drawerSheet.visibility = View.GONE
            }
        }
    }

    private fun setupAdapters() {
        val columns = preferencesManager.gridColumns

        // Home grid — shows apps pinned to home screen
        homeAppAdapter = HomeAppAdapter(
            onAppClick = { entity -> launchApp(entity) },
            onAppLongClick = { entity -> showAppOptions(entity) }
        )
        homeAppGrid.apply {
            layoutManager = GridLayoutManager(this@LauncherHomeActivity, columns)
            adapter = homeAppAdapter
        }

        // Drawer grid — shows all apps
        drawerAppAdapter = AppDrawerAdapter(
            onAppClick = { entity -> launchApp(entity) },
            onAppLongClick = { entity -> showAppOptions(entity) }
        )
        drawerAppGrid.apply {
            layoutManager = GridLayoutManager(this@LauncherHomeActivity, columns)
            adapter = drawerAppAdapter
        }
    }

    private fun observeApps() {
        // Home screen apps
        lifecycleScope.launch {
            appDao.getHomeScreenApps(currentEnvironment).collectLatest { apps ->
                homeAppAdapter.submitList(apps)
            }
        }

        // All apps for drawer
        lifecycleScope.launch {
            appDao.getAppsForEnvironment(currentEnvironment).collectLatest { apps ->
                drawerAppAdapter.submitList(apps)
            }
        }
    }

    private fun handleIntent() {
        val env = intent.getStringExtra(IntentExtras.ENVIRONMENT_TYPE)
        if (env != null) {
            currentEnvironment = env
            environmentEngine.applyEnvironment(env)
            updateSpaceIndicator()
            setupAdapters()
            observeApps()
        }
    }

    private fun launchApp(entity: AppInfoEntity) {
        try {
            val intent = packageManager.getLaunchIntentForPackage(entity.packageName)
            if (intent != null) {
                // Record usage
                lifecycleScope.launch(Dispatchers.IO) {
                    appDao.recordAppUsage(entity.packageName, currentEnvironment)
                }
                startActivity(intent)
            } else {
                Toast.makeText(this, "Cannot launch this app", Toast.LENGTH_SHORT).show()
            }
        } catch (e: Exception) {
            Toast.makeText(this, "Error launching app", Toast.LENGTH_SHORT).show()
        }
    }

    private fun showAppOptions(entity: AppInfoEntity) {
        val options = arrayOf(
            "App Info",
            "Hide App",
            "Add to Home",
            "Uninstall"
        )

        android.app.AlertDialog.Builder(this)
            .setTitle(entity.appName)
            .setItems(options) { _, which ->
                lifecycleScope.launch {
                    when (which) {
                        0 -> openAppInfo(entity.packageName)
                        1 -> {
                            appDao.setHidden(entity.packageName, currentEnvironment, true)
                            Toast.makeText(this@LauncherHomeActivity, "App hidden", Toast.LENGTH_SHORT).show()
                        }
                        2 -> {
                            appDao.setOnHomeScreen(entity.packageName, currentEnvironment, true)
                            Toast.makeText(this@LauncherHomeActivity, "Added to home", Toast.LENGTH_SHORT).show()
                        }
                        3 -> uninstallApp(entity.packageName)
                    }
                }
            }
            .show()
    }

    private fun openAppInfo(packageName: String) {
        val intent = Intent(Settings.ACTION_APPLICATION_DETAILS_SETTINGS).apply {
            data = Uri.parse("package:$packageName")
        }
        startActivity(intent)
    }

    private fun uninstallApp(packageName: String) {
        val intent = Intent(Intent.ACTION_DELETE).apply {
            data = Uri.parse("package:$packageName")
        }
        startActivity(intent)
    }

    private fun filterDrawerApps(query: String) {
        lifecycleScope.launch {
            if (query.isBlank()) {
                appDao.getAppsForEnvironment(currentEnvironment).collectLatest { apps ->
                    drawerAppAdapter.submitList(apps)
                }
            } else {
                appDao.searchApps(currentEnvironment, query).collectLatest { apps ->
                    drawerAppAdapter.submitList(apps)
                }
            }
        }
    }

    private fun updateSpaceIndicator() {
        val (label, color) = when (currentEnvironment) {
            EnvironmentType.PRIMARY -> "Primary Space" to R.color.space_primary_badge
            EnvironmentType.HIDDEN -> "Hidden Space" to R.color.space_hidden_badge
            EnvironmentType.EMERGENCY -> "Emergency Space" to R.color.space_emergency_badge
            else -> "" to R.color.space_primary_badge
        }

        spaceIndicatorText.text = label
        spaceIndicatorDot.setBackgroundResource(color)
        spaceIndicator.visibility = if (currentEnvironment != EnvironmentType.PRIMARY) View.VISIBLE else View.GONE
    }

    override fun onNewIntent(intent: Intent?) {
        super.onNewIntent(intent)
        intent?.let { setIntent(it) }
        handleIntent()
    }

    override fun onBackPressed() {
        if (drawerSheet.visibility == View.VISIBLE) {
            drawerSheet.visibility = View.GONE
        }
        // Don't exit the launcher — go to home
    }
}
