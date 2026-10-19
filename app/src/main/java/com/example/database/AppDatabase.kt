package com.example.database

import android.content.Context
import androidx.room.Database
import androidx.room.Room
import androidx.room.RoomDatabase
import androidx.sqlite.db.SupportSQLiteDatabase

@Database(
    entities = [
        SavedItemEntity::class,
        DownloadedItemEntity::class,
        CompletedItemEntity::class,
        VideoPlaybackProgressEntity::class,
        com.example.modeltest.data.local.OfflineMcqAnswerEntity::class,
        com.example.modeltest.data.local.ActiveModelTestSessionEntity::class,
        LeaderboardStudentEntity::class
    ],
    version = 6,
    exportSchema = false
)
abstract class AppDatabase : RoomDatabase() {

    abstract fun savedItemDao(): SavedItemDao
    abstract fun downloadedItemDao(): DownloadedItemDao
    abstract fun completedItemDao(): CompletedItemDao
    abstract fun videoPlaybackProgressDao(): VideoPlaybackProgressDao
    abstract fun modelTestDao(): com.example.modeltest.data.local.ModelTestDao
    abstract fun leaderboardStudentDao(): LeaderboardStudentDao

    companion object {
        private const val DATABASE_NAME = "shikho_learning_db"

        @Volatile
        private var INSTANCE: AppDatabase? = null

        fun getDatabase(context: Context): AppDatabase {
            return INSTANCE ?: synchronized(this) {
                INSTANCE ?: buildDatabase(context.applicationContext).also {
                    INSTANCE = it
                }
            }
        }

        fun getInstance(context: Context): AppDatabase = getDatabase(context)

        private fun buildDatabase(appContext: Context): AppDatabase {
            return Room.databaseBuilder(
                appContext,
                AppDatabase::class.java,
                DATABASE_NAME
            )
                // Enable Write-Ahead Logging (WAL) for high-concurrency parallel download updates & smooth UI reads
                .setJournalMode(JournalMode.WRITE_AHEAD_LOGGING)
                .fallbackToDestructiveMigration()
                .fallbackToDestructiveMigrationOnDowngrade()
                .addCallback(object : Callback() {
                    override fun onOpen(db: SupportSQLiteDatabase) {
                        super.onOpen(db)
                        try {
                            // Create helpful non-intrusive indices if not present to speed up download & path lookups
                            db.execSQL(
                                "CREATE INDEX IF NOT EXISTS idx_downloaded_items_status_created " +
                                    "ON downloaded_items(status, createdAt DESC)"
                            )
                            db.execSQL(
                                "CREATE INDEX IF NOT EXISTS idx_downloaded_items_local_path " +
                                    "ON downloaded_items(localFilePath)"
                            )
                        } catch (_: Exception) {
                            // Ignore if table structure is being migrated
                        }
                    }
                })
                .build()
        }
    }
}
