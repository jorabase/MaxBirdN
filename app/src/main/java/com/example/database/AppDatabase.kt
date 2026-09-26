package com.example.database

import android.content.Context
import androidx.room.Database
import androidx.room.Room
import androidx.room.RoomDatabase

@Database(
    entities = [
        SavedItemEntity::class,
        DownloadedItemEntity::class,
        CompletedItemEntity::class,
        VideoPlaybackProgressEntity::class
    ],
    version = 4,
    exportSchema = false
)
abstract class AppDatabase : RoomDatabase() {

    abstract fun savedItemDao(): SavedItemDao
    abstract fun downloadedItemDao(): DownloadedItemDao
    abstract fun completedItemDao(): CompletedItemDao
    abstract fun videoPlaybackProgressDao(): VideoPlaybackProgressDao

    companion object {
        @Volatile
        private var INSTANCE: AppDatabase? = null

        fun getDatabase(context: Context): AppDatabase {
            return INSTANCE ?: synchronized(this) {
                val instance = Room.databaseBuilder(
                    context.applicationContext,
                    AppDatabase::class.java,
                    "shikho_learning_db"
                ).fallbackToDestructiveMigration()
                    .build()
                INSTANCE = instance
                instance
            }
        }
    }
}
