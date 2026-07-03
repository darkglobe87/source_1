package com.example.data

import android.content.Context
import androidx.room.Database
import androidx.room.Room
import androidx.room.RoomDatabase

@Database(entities = [Movie::class, UserState::class], version = 3, exportSchema = false)
abstract class AppDatabase : RoomDatabase() {
    abstract fun appDao(): AppDao

    companion object {
        @Volatile
        private var INSTANCE: AppDatabase? = null

        fun getDatabase(context: Context): AppDatabase {
            return INSTANCE ?: synchronized(this) {
                val instance = Room.databaseBuilder(
                    context.applicationContext,
                    AppDatabase::class.java,
                    "bad_plots_database"
                )
                    // No formal migration path yet at this stage of development -
                    // safe to rebuild the local DB on schema changes (Room requires
                    // a version bump whenever an entity's columns change, like the
                    // new Movie.imageRes field). This resets local progress/coins
                    // once on this update; not something to keep doing once shipped.
                    .fallbackToDestructiveMigration(dropAllTables = true)
                    .build()
                INSTANCE = instance
                instance
            }
        }
    }
}
