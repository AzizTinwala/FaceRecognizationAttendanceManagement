package com.example.facerecognizationattendancemanagement

import android.content.Context
import android.graphics.Bitmap
import android.util.Log
import org.tensorflow.lite.Interpreter
import java.io.FileInputStream
import java.nio.ByteBuffer
import java.nio.MappedByteBuffer
import java.nio.channels.FileChannel
import androidx.core.graphics.scale
import java.nio.ByteOrder
import kotlin.math.sqrt

/**
 * MobileFaceNet is a wrapper for the TensorFlow Lite face recognition model.
 * It handles model loading, image preprocessing (resizing, normalization),
 * and running inference to generate face embeddings.
 */
class MobileFaceNet(context: Context) {

    // The TFLite interpreter used for running the model
    private val interpreter: Interpreter
    
    // Model expected input dimensions (standard for MobileFaceNet is 112x112)
    private val inputSize = 112
    
    // Size of the output feature vector (embedding)
    private val embeddingSize = 192
    
    // The batch size defined in the model (e.g., some models expect [2, 112, 112, 3])
    private var modelBatchSize = 1

    init {
        val options = Interpreter.Options()
        // XNNPACK can be enabled for better CPU performance on mobile
         options.setUseXNNPACK(true)
        
        interpreter = Interpreter(loadModelFile(context), options)
        
        // Inspect input tensor to adapt to the model's expected shape
        val inputTensor = interpreter.getInputTensor(0)
        val inputShape = inputTensor.shape()
        modelBatchSize = inputShape[0]
        
        val outputTensor = interpreter.getOutputTensor(0)
        val outputShape = outputTensor.shape()

        Log.d("MobileFaceNet", "Input Shape: ${inputShape.joinToString()}")
        Log.d("MobileFaceNet", "Output Shape: ${outputShape.joinToString()}")
    }

    /**
     * Loads the TFLite model file from the assets folder.
     */
    private fun loadModelFile(context: Context): MappedByteBuffer {
        val fileDescriptor = context.assets.openFd("MobileFaceNet.tflite")
        val inputStream = FileInputStream(fileDescriptor.fileDescriptor)
        val fileChannel = inputStream.channel
        return fileChannel.map(
            FileChannel.MapMode.READ_ONLY,
            fileDescriptor.startOffset,
            fileDescriptor.declaredLength
        )
    }

    /**
     * Generates a unique 192-float embedding for a given face bitmap.
     * @param faceBitmap The cropped face image.
     * @return Normalized FloatArray representing the facial features.
     */
    fun getEmbedding(faceBitmap: Bitmap): FloatArray {
        // Step 1: Resize to model's expected input size
        val resized = faceBitmap.scale(inputSize, inputSize)
        
        // Step 2: Convert Bitmap pixels to normalized Float32 ByteBuffer
        val input = bitmapToFloatBuffer(resized)

        // Step 3: Prepare output buffer
        val output = Array(modelBatchSize) { FloatArray(embeddingSize) }
        
        // Step 4: Run inference
        interpreter.run(input, output)

        // Step 5: Normalize the resulting vector for cosine similarity comparisons
        return l2Normalize(output[0])
    }

    /**
     * Converts a Bitmap into a direct ByteBuffer of Floats.
     * Applies normalization: (pixel - 128) / 128 to bring values into [-1, 1] range.
     */
    private fun bitmapToFloatBuffer(bitmap: Bitmap): ByteBuffer {
        // 4 bytes per float (301056 bytes if batch size is 2)
        val buffer = ByteBuffer.allocateDirect(modelBatchSize * inputSize * inputSize * 3 * 4)
        buffer.order(ByteOrder.nativeOrder())

        val pixels = IntArray(inputSize * inputSize)
        bitmap.getPixels(pixels, 0, inputSize, 0, 0, inputSize, inputSize)

        // Fill RGB values for the first image slot in the batch
        for (pixel in pixels) {
            // R
            buffer.putFloat(((pixel shr 16 and 0xFF) - 128f) / 128f)
            // G
            buffer.putFloat(((pixel shr 8 and 0xFF) - 128f) / 128f)
            // B
            buffer.putFloat(((pixel and 0xFF) - 128f) / 128f)
        }
        
        // Note: Remaining slots in the batch are left as zero if modelBatchSize > 1
        buffer.rewind()
        return buffer
    }

    /**
     * Normalizes a vector using L2 normalization so its magnitude equals 1.
     * This makes similarity calculations (like dot product) equivalent to cosine similarity.
     */
    private fun l2Normalize(embedding: FloatArray): FloatArray {
        var sum = 0f
        for (value in embedding) sum += value * value
        val norm = sqrt(sum)

        if (norm == 0f) return embedding

        for (i in embedding.indices) {
            embedding[i] /= norm
        }
        return embedding
    }
}
