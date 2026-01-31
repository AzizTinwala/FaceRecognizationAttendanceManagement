package com.example.facerecognizationattendancemanagement

import androidx.room.Dao
import androidx.room.Delete
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query

/**
 * UserDao (Data Access Object) provides the methods that the rest of the app 
 * uses to interact with data in the 'users' table.
 */
@Dao
interface UserDao {

    /**
     * Inserts a new user or updates an existing one if the ID matches.
     * Use OnConflictStrategy.REPLACE to ensure the latest face embedding is stored.
     */
    @Insert(onConflict = OnConflictStrategy.REPLACE)
    fun insertUser(user: UserEntity)

    /**
     * Retrieves all enrolled users from the database.
     * Typically used at app startup to load face embeddings into memory.
     */
    @Query("SELECT * FROM users")
    fun getAllUsers(): List<UserEntity>

    /**
     * Finds a specific user by their unique ID string.
     */
    @Query("SELECT * FROM users WHERE id = :userId")
    fun getUserById(userId: String): UserEntity?

    /**
     * Removes a user record from the database.
     */
    @Delete
    fun deleteUser(user: UserEntity)
}
