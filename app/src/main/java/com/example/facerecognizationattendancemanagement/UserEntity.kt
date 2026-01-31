package com.example.facerecognizationattendancemanagement

import androidx.room.Entity
import androidx.room.PrimaryKey

/**
 * UserEntity represents a single row in the 'users' database table.
 * It stores the person's unique ID, their name, and their facial embedding vector.
 */
@Entity(tableName = "users")
data class UserEntity(
    // Unique identifier for the user (e.g., Employee ID or UUID)
    @PrimaryKey val id: String,
    
    // Full name of the user
    val name: String,
    
    /**
     * The 192-dimensional vector that mathematically represents the user's face.
     * This is converted to/from a String or Blob via EmbeddingConverter for Room storage.
     */
    val embedding: FloatArray
) {
    /**
     * Custom equals and hashCode are required because FloatArray uses reference equality by default,
     * which can cause issues with Room comparison logic.
     */
    override fun equals(other: Any?): Boolean {
        if (this === other) return true
        if (javaClass != other?.javaClass) return false

        other as UserEntity

        if (id != other.id) return false
        if (name != other.name) return false
        if (!embedding.contentEquals(other.embedding)) return false

        return true
    }

    override fun hashCode(): Int {
        var result = id.hashCode()
        result = 31 * result + name.hashCode()
        result = 31 * result + embedding.contentHashCode()
        return result
    }
}
