package com.example.facerecognizationattendancemanagement

/**
 * FaceMatcher provides utility functions to compare two face embeddings.
 * It uses Cosine Similarity to determine how closely two faces match.
 */
object FaceMatcher {

    /**
     * Calculates the dot product of two normalized vectors.
     * Since the embeddings from MobileFaceNet are L2-normalized, 
     * the dot product is mathematically equivalent to Cosine Similarity.
     * @return A value between -1.0 and 1.0 (closer to 1.0 means more similar).
     */
    fun cosineSimilarity(a: FloatArray, b: FloatArray): Float {
        var dot = 0f
        for (i in a.indices) {
            dot += a[i] * b[i]
        }
        return dot
    }

    /**
     * Determines if two embeddings belong to the same person based on a threshold.
     * @param emb1 First face embedding.
     * @param emb2 Second face embedding.
     * @param threshold The similarity score required to consider it a match. 
     *                  0.75 is a good balance for MobileFaceNet.
     */
    fun isMatch(
        emb1: FloatArray,
        emb2: FloatArray,
        threshold: Float = 0.75f
    ): Boolean {
        return cosineSimilarity(emb1, emb2) > threshold
    }
}
