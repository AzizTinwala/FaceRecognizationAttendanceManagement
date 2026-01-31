package com.example.facerecognizationattendancemanagement

import androidx.room.TypeConverter

/**
 * EmbeddingConverter allows Room to store the FloatArray face embeddings.
 * Since Room cannot store arrays directly, we convert them to a comma-separated String
 * and back.
 */
class EmbeddingConverter {

    /**
     * Converts a FloatArray into a comma-separated String for database storage.
     * Example: [0.1, 0.2] -> "0.1,0.2"
     */
    @TypeConverter
    fun fromFloatArray(array: FloatArray): String {
        return array.joinToString(separator = ",")
    }

    /**
     * Converts a comma-separated String back into a FloatArray.
     * Typically used when loading users from the database into memory.
     */
    @TypeConverter
    fun toFloatArray(data: String): FloatArray {
        if (data.isEmpty()) return floatArrayOf()
        return data.split(",").map { it.toFloat() }.toFloatArray()
    }
}
