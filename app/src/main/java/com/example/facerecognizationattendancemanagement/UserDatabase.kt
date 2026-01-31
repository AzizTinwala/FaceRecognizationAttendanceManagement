package com.example.facerecognizationattendancemanagement

import android.content.Context
import androidx.room.Database
import androidx.room.Room
import androidx.room.RoomDatabase
import androidx.room.TypeConverters

/**
 * UserDatabase is the main entry point to the Room database.
 * It holds the 'users' table and provides access to the UserDao.
 */
@Database(
    entities = [UserEntity::class],
    version = 1,
    exportSchema = false
)
// EmbeddingConverter is needed to handle the conversion of FloatArray to a format Room can store.
@TypeConverters(EmbeddingConverter::class)
abstract class UserDatabase : RoomDatabase() {

    /**
     * Provides access to the Data Access Object (DAO) for the 'users' table.
     */
    abstract fun userDao(): UserDao

    companion object {

        // Singleton prevents multiple instances of database opening at the same time.
        @Volatile
        private var INSTANCE: UserDatabase? = null

        /**
         * Returns the singleton instance of UserDatabase.
         * Thread-safe using the 'synchronized' block.
         */
        fun getInstance(context: Context): UserDatabase {
            return INSTANCE ?: synchronized(this) {
                val instance = Room.databaseBuilder(
                    context.applicationContext,
                    UserDatabase::class.java,
                    "user_database"
                ).build()
                INSTANCE = instance
                instance
            }
        }
    }
}
