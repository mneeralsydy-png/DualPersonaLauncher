package com.dualpersona.launcher.data

import android.content.Context
import androidx.room.Database
import androidx.room.Room
import androidx.room.RoomDatabase
import com.dualpersona.launcher.data.dao.AppInfoDao
import com.dualpersona.launcher.data.dao.SecurityEventDao
import com.dualpersona.launcher.data.dao.WallpaperDao
import com.dualpersona.launcher.data.entity.AppInfoEntity
import com.dualpersona.launcher.data.entity.SecurityEventEntity
import com.dualpersona.launcher.data.entity.WallpaperEntity
import com.dualpersona.launcher.utils.DatabaseConstants

@Database(
    entities = [
        AppInfoEntity::class,
        WallpaperEntity::class,
        SecurityEventEntity::class
    ],
    version = DatabaseConstants.DATABASE_VERSION,
    exportSchema = true
)
abstract class AppDatabase : RoomDatabase() {

    abstract fun appInfoDao(): AppInfoDao
    abstract fun wallpaperDao(): WallpaperDao
    abstract fun securityEventDao(): SecurityEventDao

    companion object {
        @Volatile
        private var INSTANCE: AppDatabase? = null

        fun getInstance(context: Context): AppDatabase {
            return INSTANCE ?: synchronized(this) {
                INSTANCE ?: buildDatabase(context).also { INSTANCE = it }
            }
        }

        private fun buildDatabase(context: Context): AppDatabase {
            return Room.databaseBuilder(
                context.applicationContext,
                AppDatabase::class.java,
                DatabaseConstants.DATABASE_NAME
            )
                .fallbackToDestructiveMigration()
                .build()
        }
    }
}
