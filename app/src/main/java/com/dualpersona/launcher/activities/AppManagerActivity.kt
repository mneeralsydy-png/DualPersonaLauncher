package com.dualpersona.launcher.activities

import android.os.Bundle
import android.view.View
import android.view.ViewGroup
import android.widget.TextView
import androidx.appcompat.app.AppCompatActivity
import androidx.lifecycle.lifecycleScope
import androidx.recyclerview.widget.LinearLayoutManager
import androidx.recyclerview.widget.RecyclerView
import com.dualpersona.launcher.DualPersonaApp
import com.dualpersona.launcher.R
import com.dualpersona.launcher.data.dao.AppInfoDao
import com.dualpersona.launcher.data.entity.AppInfoEntity
import com.dualpersona.launcher.engine.EnvironmentEngine
import kotlinx.coroutines.flow.collectLatest
import kotlinx.coroutines.launch

/**
 * App Manager Activity — manage apps in the current environment.
 * Tabs: Installed, Hidden, Available
 */
class AppManagerActivity : AppCompatActivity() {

    private lateinit var appDao: AppInfoDao
    private lateinit var environmentEngine: EnvironmentEngine

    private lateinit var appList: RecyclerView
    private lateinit var appCount: TextView

    private var currentTab = 0
    private var currentEnvironment = "primary"

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_app_manager)

        val app = DualPersonaApp.getInstance()
        appDao = app.database.appInfoDao()
        environmentEngine = app.environmentEngine
        currentEnvironment = environmentEngine.getCurrentEnvironment()

        appList = findViewById(R.id.appList)
        appCount = findViewById(R.id.textAppCount)

        appList.layoutManager = LinearLayoutManager(this)

        findViewById<View>(R.id.btnBack).setOnClickListener { finish() }

        setupTabs()
        loadApps()
    }

    private fun setupTabs() {
        val tabLayout = findViewById<com.google.android.material.tabs.TabLayout>(R.id.tabLayout)
        tabLayout.addOnTabSelectedListener(object : com.google.android.material.tabs.TabLayout.OnTabSelectedListener {
            override fun onTabSelected(tab: com.google.android.material.tabs.TabLayout.Tab) {
                currentTab = tab.position
                loadApps()
            }
            override fun onTabUnselected(tab: com.google.android.material.tabs.TabLayout.Tab) {}
            override fun onTabReselected(tab: com.google.android.material.tabs.TabLayout.Tab) {}
        })
    }

    private fun loadApps() {
        lifecycleScope.launch {
            when (currentTab) {
                0 -> appDao.getAppsForEnvironment(currentEnvironment)
                1 -> appDao.getHiddenApps(currentEnvironment)
                else -> appDao.getAppsForEnvironment(currentEnvironment)
            }.collectLatest { apps ->
                appCount.text = "${apps.size} apps"
                appList.adapter = AppManagerAdapter(apps,
                    onHideClick = { entity ->
                        lifecycleScope.launch {
                            appDao.setHidden(entity.packageName, currentEnvironment, !entity.isHidden)
                        }
                    },
                    onHomeClick = { entity ->
                        lifecycleScope.launch {
                            appDao.setOnHomeScreen(entity.packageName, currentEnvironment, !entity.isOnHomeScreen)
                        }
                    }
                )
            }
        }
    }

    inner class AppManagerAdapter(
        private val apps: List<AppInfoEntity>,
        private val onHideClick: (AppInfoEntity) -> Unit,
        private val onHomeClick: (AppInfoEntity) -> Unit
    ) : RecyclerView.Adapter<AppManagerAdapter.ViewHolder>() {

        inner class ViewHolder(view: View) : RecyclerView.ViewHolder(view) {
            val appName: TextView = view.findViewById(android.R.id.text1)
            val appPackage: TextView = view.findViewById(android.R.id.text2)
            val btnAction: TextView = view.findViewById(android.R.id.button1)
        }

        override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): ViewHolder {
            val view = android.view.LayoutInflater.from(parent.context)
                .inflate(android.R.layout.simple_list_item_2, parent, false)
            return ViewHolder(view)
        }

        override fun onBindViewHolder(holder: ViewHolder, position: Int) {
            val app = apps[position]
            holder.appName.text = app.appName
            holder.appPackage.text = app.packageName
            holder.btnAction.text = if (app.isHidden) "Show" else "Hide"
            holder.btnAction.setOnClickListener { onHideClick(app) }
        }

        override fun getItemCount() = apps.size
    }
}