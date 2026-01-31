package com.example.facerecognizationattendancemanagement

import android.graphics.Bitmap
import android.graphics.Rect
import android.util.Log
import com.google.mlkit.vision.common.InputImage
import com.google.mlkit.vision.face.Face
import com.google.mlkit.vision.face.FaceDetection
import com.google.mlkit.vision.face.FaceDetectorOptions

/**
 * FaceProcessor utilizes Google ML Kit to detect faces within a bitmap.
 * It provides detailed metadata including head rotation (Euler angles) 
 * and facial contours for UI rendering.
 */
class FaceProcessor {

    /**
     * Data class to hold processed face information.
     * @param bitmap The cropped face image normalized for the TFLite model.
     * @param eulerX Rotation around the X-axis (Pitch/Up-Down).
     * @param eulerY Rotation around the Y-axis (Yaw/Left-Right).
     * @param eulerZ Rotation around the Z-axis (Roll/Tilt).
     * @param originalFace The raw ML Kit Face object for landmark/contour access.
     */
    data class FaceData(
        val bitmap: Bitmap,
        val eulerX: Float,
        val eulerY: Float,
        val eulerZ: Float,
        val originalFace: Face? = null
    )

    // Configure the face detector for high accuracy and landmark/contour tracking
    private val detector = FaceDetection.getClient(
        FaceDetectorOptions.Builder()
            .setPerformanceMode(FaceDetectorOptions.PERFORMANCE_MODE_ACCURATE)
            .setLandmarkMode(FaceDetectorOptions.LANDMARK_MODE_ALL)
            .setClassificationMode(FaceDetectorOptions.CLASSIFICATION_MODE_ALL)
            .setContourMode(FaceDetectorOptions.CONTOUR_MODE_ALL) // Required for FaceOverlayView
            .build()
    )

    /**
     * Processes a bitmap and invokes callbacks based on the detection result.
     * @param bitmap The input image (usually a frame from the camera).
     * @param onFaceDetected Callback invoked when at least one face is found.
     * @param onNoFace Callback invoked when no face is found or processing fails.
     */
    fun detectFace(
        bitmap: Bitmap,
        onFaceDetected: (FaceData) -> Unit,
        onNoFace: () -> Unit = {}
    ) {
        val image = InputImage.fromBitmap(bitmap, 0)

        detector.process(image)
            .addOnSuccessListener { faces ->
                if (faces.isNotEmpty()) {
                    val face = faces[0] // Process the primary face
                    val rect = face.boundingBox
                    
                    try {
                        // Crop the original bitmap to the face's bounding box
                        val faceBitmap = cropFace(bitmap, rect)
                        onFaceDetected(
                            FaceData(
                                bitmap = faceBitmap,
                                eulerX = face.headEulerAngleX,
                                eulerY = face.headEulerAngleY,
                                eulerZ = face.headEulerAngleZ,
                                originalFace = face
                            )
                        )
                    } catch (e: Exception) {
                        Log.e("FaceProcessor", "Error cropping face", e)
                        onNoFace()
                    }
                } else {
                    onNoFace()
                }
            }
            .addOnFailureListener {
                Log.e("FaceProcessor", "Face detection failed", it)
                onNoFace()
            }
    }

    /**
     * Safely crops a bitmap given a rectangle, ensuring coordinates stay within image bounds.
     */
    private fun cropFace(bitmap: Bitmap, rect: Rect): Bitmap {
        val x = rect.left.coerceIn(0, bitmap.width - 1)
        val y = rect.top.coerceIn(0, bitmap.height - 1)
        val width = rect.width().coerceIn(1, bitmap.width - x)
        val height = rect.height().coerceIn(1, bitmap.height - y)

        return Bitmap.createBitmap(bitmap, x, y, width, height)
    }
}
