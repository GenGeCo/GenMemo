package com.example.genmemo.data.database

import android.content.Context
import androidx.room.Database
import androidx.room.Room
import androidx.room.RoomDatabase
import androidx.room.migration.Migration
import androidx.sqlite.db.SupportSQLiteDatabase
import com.example.genmemo.data.model.Category
import com.example.genmemo.data.model.InstalledPackage
import com.example.genmemo.data.model.MemoryItem
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch

@Database(
    entities = [Category::class, MemoryItem::class, InstalledPackage::class],
    version = 3,
    exportSchema = false
)
abstract class AppDatabase : RoomDatabase() {

    abstract fun categoryDao(): CategoryDao
    abstract fun memoryItemDao(): MemoryItemDao
    abstract fun installedPackageDao(): InstalledPackageDao

    companion object {
        @Volatile
        private var INSTANCE: AppDatabase? = null

        // Migration from version 1 to 2: add prompt column
        private val MIGRATION_1_2 = object : Migration(1, 2) {
            override fun migrate(db: SupportSQLiteDatabase) {
                db.execSQL("ALTER TABLE memory_items ADD COLUMN prompt TEXT NOT NULL DEFAULT 'Chi/Cosa è?'")
            }
        }

        // Migration from version 2 to 3: add installed_packages table
        private val MIGRATION_2_3 = object : Migration(2, 3) {
            override fun migrate(db: SupportSQLiteDatabase) {
                db.execSQL("""
                    CREATE TABLE IF NOT EXISTS installed_packages (
                        packageId TEXT PRIMARY KEY NOT NULL,
                        name TEXT NOT NULL,
                        description TEXT NOT NULL,
                        author TEXT NOT NULL,
                        questionsJson TEXT NOT NULL,
                        settingsJson TEXT NOT NULL,
                        totalQuestions INTEGER NOT NULL,
                        installedAt INTEGER NOT NULL,
                        lastPlayedAt INTEGER
                    )
                """.trimIndent())
            }
        }

        fun getDatabase(context: Context): AppDatabase {
            return INSTANCE ?: synchronized(this) {
                val instance = Room.databaseBuilder(
                    context.applicationContext,
                    AppDatabase::class.java,
                    "genmemo_database"
                )
                    .addMigrations(MIGRATION_1_2, MIGRATION_2_3)
                    .addCallback(DatabaseCallback())
                    .build()
                INSTANCE = instance
                instance
            }
        }
    }

    private class DatabaseCallback : RoomDatabase.Callback() {
        override fun onCreate(db: SupportSQLiteDatabase) {
            super.onCreate(db)
            INSTANCE?.let { database ->
                CoroutineScope(Dispatchers.IO).launch {
                    // Insert default "Generale" category
                    database.categoryDao().insert(
                        Category(name = "Generale", color = 0xFF6200EE)
                    )
                }
            }
        }
    }
}
