package com.dualpersona.launcher.launcher

import android.content.pm.PackageManager
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.ImageView
import android.widget.TextView
import androidx.recyclerview.widget.DiffUtil
import androidx.recyclerview.widget.ListAdapter
import androidx.recyclerview.widget.RecyclerView
import com.dualpersona.launcher.R
import com.dualpersona.launcher.data.entity.AppInfoEntity

/**
 * RecyclerView adapter for the app drawer (all apps view).
 * Displays all apps for the current environment in a scrollable grid.
 */
class AppDrawerAdapter(
    private val onAppClick: (AppInfoEntity) -> Unit,
    private val onAppLongClick: (AppInfoEntity) -> Unit
) : ListAdapter<AppInfoEntity, AppDrawerAdapter.DrawerViewHolder>(AppDiffCallback) {

    private var packageManager: PackageManager? = null

    override fun onAttachedToRecyclerView(recyclerView: RecyclerView) {
        super.onAttachedToRecyclerView(recyclerView)
        packageManager = recyclerView.context.packageManager
    }

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): DrawerViewHolder {
        val view = LayoutInflater.from(parent.context)
            .inflate(R.layout.item_app_icon, parent, false)
        return DrawerViewHolder(view)
    }

    override fun onBindViewHolder(holder: DrawerViewHolder, position: Int) {
        val app = getItem(position)
        holder.bind(app, packageManager)
    }

    inner class DrawerViewHolder(itemView: View) : RecyclerView.ViewHolder(itemView) {
        private val iconView: ImageView = itemView.findViewById(R.id.appIcon)
        private val labelView: TextView = itemView.findViewById(R.id.appLabel)

        fun bind(app: AppInfoEntity, pm: PackageManager?) {
            labelView.text = app.customLabel ?: app.appName

            try {
                pm?.let {
                    val appInfo = it.getApplicationInfo(app.packageName, 0)
                    iconView.setImageDrawable(appInfo.loadIcon(it))
                }
            } catch (e: PackageManager.NameNotFoundException) {
                iconView.setImageResource(android.R.drawable.sym_def_app_icon)
            }

            itemView.setOnClickListener { onAppClick(app) }
            itemView.setOnLongClickListener {
                onAppLongClick(app)
                true
            }
        }
    }

    companion object AppDiffCallback : DiffUtil.ItemCallback<AppInfoEntity>() {
        override fun areItemsTheSame(oldItem: AppInfoEntity, newItem: AppInfoEntity): Boolean {
            return oldItem.id == newItem.id
        }

        override fun areContentsTheSame(oldItem: AppInfoEntity, newItem: AppInfoEntity): Boolean {
            return oldItem == newItem
        }
    }
}
