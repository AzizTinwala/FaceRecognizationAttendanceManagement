package com.example.facerecognizationattendancemanagement

import android.Manifest
import android.content.pm.PackageManager
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.graphics.Matrix
import android.os.Bundle
import android.os.Handler
import android.os.Looper
import android.util.Log
import android.view.View
import android.widget.Toast
import androidx.activity.result.contract.ActivityResultContracts
import androidx.appcompat.app.AppCompatActivity
import androidx.camera.core.*
import androidx.camera.lifecycle.ProcessCameraProvider
import androidx.core.content.ContextCompat
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.lifecycleScope
import com.example.facerecognizationattendancemanagement.databinding.ActivityMainBinding
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.util.concurrent.ExecutorService
import java.util.concurrent.Executors

/**
 * MainActivity handles the camera lifecycle, UI interactions, and the face enrollment flow.
 * It coordinates between CameraX for image capture, ML Kit for face processing,
 * and MobileFaceNet for generating face embeddings.
 */
class MainActivity : AppCompatActivity() {
    
    // ViewBinding for accessing layout components safely
    private lateinit var binding: ActivityMainBinding
    
    // CameraX components
    private var imageCapture: ImageCapture? = null
    private var lensFacing = CameraSelector.LENS_FACING_FRONT
    
    // Executor for background camera operations to keep UI thread responsive
    private lateinit var cameraExecutor: ExecutorService
    
    // Core processing components
    private lateinit var faceNet: MobileFaceNet       // TFLite model wrapper
    private lateinit var faceProcessor: FaceProcessor // ML Kit face detector
    private lateinit var viewModel: FaceViewModel     // Database & State handler

    // Enrollment state variables
    private var isEnrolling = false
    private var capturedEmbeddings = mutableListOf<FloatArray>()
    
    /**
     * Defines the sequence of poses required for a complete face enrollment.
     */
    private enum class CaptureStep(val instruction: String) {
        STRAIGHT("Look straight at the camera"),
        LEFT("Turn your head slightly LEFT"),
        RIGHT("Turn your head slightly RIGHT"),
        UP("Look slightly UP"),
        DOWN("Look slightly DOWN"),
        DONE("Capture Complete!")
    }
    
    private var currentStep = CaptureStep.STRAIGHT
    
    // Main thread handler for UI updates and delayed tasks
    private val mainHandler = Handler(Looper.getMainLooper())

    // Handles camera permission request result
    private val requestPermissionLauncher =
        registerForActivityResult(ActivityResultContracts.RequestMultiplePermissions()) { permissions ->
            if (permissions[Manifest.permission.CAMERA] == true) {
                startCamera()
            } else {
                Toast.makeText(this, "Camera permission is required", Toast.LENGTH_SHORT).show()
            }
        }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        binding = ActivityMainBinding.inflate(layoutInflater)
        setContentView(binding.root)

        // Initialize core components
        faceNet = MobileFaceNet(this)
        faceProcessor = FaceProcessor()
        viewModel = ViewModelProvider(this)[FaceViewModel::class.java]
        
        // Single thread executor for camera tasks
        cameraExecutor = Executors.newSingleThreadExecutor()

        // Check permissions and start camera
        if (allPermissionsGranted()) {
            startCamera()
        } else {
            requestPermissionLauncher.launch(arrayOf(Manifest.permission.CAMERA))
        }

        // UI Event listeners
        binding.btnCapture.setOnClickListener { 
            if (!isEnrolling) {
                startEnrollment()
            }
        }
        binding.btnSwitchCamera.setOnClickListener { switchCamera() }
        
        updateUI()
    }

    /**
     * Resets enrollment state and begins the step-by-step capture process.
     */
    private fun startEnrollment() {
        isEnrolling = true
        capturedEmbeddings.clear()
        currentStep = CaptureStep.STRAIGHT
        binding.btnCapture.isEnabled = false
        binding.btnCapture.text = "Enrolling..."
        updateUI()
        captureLoop()
    }

    /**
     * Synchronizes UI elements (instructions, progress bar) with the current state.
     */
    private fun updateUI() {
        binding.tvInstruction.text = currentStep.instruction
        binding.pbCaptureProgress.progress = (capturedEmbeddings.size * 100) / 5
        binding.tvInstruction.visibility = if (isEnrolling) View.VISIBLE else View.GONE
        binding.pbCaptureProgress.visibility = if (isEnrolling) View.VISIBLE else View.GONE
        
        // Hide face mesh if not actively enrolling
        if (!isEnrolling) {
            binding.faceOverlay.updateFace(null, 0, 0)
        }
    }

    /**
     * Recursive loop that triggers image capture at regular intervals during enrollment.
     */
    private fun captureLoop() {
        if (!isEnrolling) return

        val imageCapture = imageCapture ?: return
        
        // 150ms delay between captures for a smooth UI overlay and balanced CPU load
        mainHandler.postDelayed({
            if (!isEnrolling) return@postDelayed
            
            imageCapture.takePicture(
                cameraExecutor,
                object : ImageCapture.OnImageCapturedCallback() {
                    override fun onCaptureSuccess(image: ImageProxy) {
                        // Switch to Default dispatcher for heavy bitmap processing
                        lifecycleScope.launch(Dispatchers.Default) {
                            val rotation = image.imageInfo.rotationDegrees
                            // Correct dimensions based on sensor rotation
                            val width = if (rotation == 90 || rotation == 270) image.height else image.width
                            val height = if (rotation == 90 || rotation == 270) image.width else image.height
                            
                            val bitmap = imageProxyToBitmap(image)
                            image.close()
                            processFaceEnrollment(bitmap, width, height)
                        }
                    }

                    override fun onError(exception: ImageCaptureException) {
                        Log.e("MainActivity", "Photo capture failed", exception)
                        if (isEnrolling) captureLoop() // Retry on error
                    }
                }
            )
        }, 150) 
    }

    /**
     * Analyzes a captured frame to detect faces and validate the head angle.
     */
    private suspend fun processFaceEnrollment(bitmap: Bitmap, imgWidth: Int, imgHeight: Int) {
        withContext(Dispatchers.Default) {
            faceProcessor.detectFace(bitmap, onFaceDetected = { faceData ->
                // Update the high-tech overlay on the UI thread
                runOnUiThread {
                    binding.faceOverlay.updateFace(faceData.originalFace, imgWidth, imgHeight)
                }

                // Check if the current face orientation matches the required step
                if (isCorrectAngle(faceData)) {
                    // Generate unique mathematical representation (embedding) of the face
                    val embedding = faceNet.getEmbedding(faceData.bitmap)
                    capturedEmbeddings.add(embedding)
                    
                    moveToNextStep()
                    runOnUiThread { updateUI() }

                    // Check if enrollment is complete or move to next pose
                    if (capturedEmbeddings.size < 5) {
                        captureLoop()
                    } else {
                        finishEnrollment()
                    }
                } else {
                    // Angle not met yet, continue scanning
                    captureLoop()
                }
            }, onNoFace = {
                // Clear overlay and continue loop if no face is found
                runOnUiThread { binding.faceOverlay.updateFace(null, 0, 0) }
                captureLoop()
            })
        }
    }

    /**
     * Logic to determine if the user's head is tilted correctly for the current step.
     */
    private fun isCorrectAngle(faceData: FaceProcessor.FaceData): Boolean {
        return when (currentStep) {
            CaptureStep.STRAIGHT -> Math.abs(faceData.eulerY) < 12 && Math.abs(faceData.eulerX) < 12
            CaptureStep.LEFT -> faceData.eulerY > 15
            CaptureStep.RIGHT -> faceData.eulerY < -15
            CaptureStep.UP -> faceData.eulerX > 15
            CaptureStep.DOWN -> faceData.eulerX < -15
            else -> false
        }
    }

    /**
     * Increments the enrollment step.
     */
    private fun moveToNextStep() {
        currentStep = when (currentStep) {
            CaptureStep.STRAIGHT -> CaptureStep.LEFT
            CaptureStep.LEFT -> CaptureStep.RIGHT
            CaptureStep.RIGHT -> CaptureStep.UP
            CaptureStep.UP -> CaptureStep.DOWN
            CaptureStep.DOWN -> CaptureStep.DONE
            CaptureStep.DONE -> CaptureStep.DONE
        }
    }

    /**
     * Finalizes enrollment by averaging all captured embeddings and saving to DB.
     */
    private fun finishEnrollment() {
        isEnrolling = false
        val averageEmbedding = averageEmbeddings(capturedEmbeddings)
        
        // Save to Room database via ViewModel
        viewModel.saveUser("EMP001", "Aziz", averageEmbedding)
        
        runOnUiThread {
            updateUI()
            binding.btnCapture.isEnabled = true
            binding.btnCapture.text = "Start Enrollment"
            Toast.makeText(this, "Enrollment Successful!", Toast.LENGTH_LONG).show()
        }
    }

    /**
     * Calculates the mean embedding from multiple captures to increase recognition reliability.
     */
    private fun averageEmbeddings(embeddings: List<FloatArray>): FloatArray {
        if (embeddings.isEmpty()) return FloatArray(192)
        val size = embeddings[0].size
        val avg = FloatArray(size)
        for (emb in embeddings) {
            for (i in 0 until size) {
                avg[i] += emb[i]
            }
        }
        for (i in 0 until size) {
            avg[i] /= embeddings.size
        }
        return avg
    }

    private fun allPermissionsGranted() = ContextCompat.checkSelfPermission(
        baseContext, Manifest.permission.CAMERA
    ) == PackageManager.PERMISSION_GRANTED

    /**
     * Initializes CameraX and binds the Preview and ImageCapture use cases to the lifecycle.
     */
    private fun startCamera() {
        val cameraProviderFuture = ProcessCameraProvider.getInstance(this)

        cameraProviderFuture.addListener({
            val cameraProvider: ProcessCameraProvider = cameraProviderFuture.get()

            val preview = Preview.Builder().build().also {
                it.surfaceProvider = binding.previewView.surfaceProvider
            }

            imageCapture = ImageCapture.Builder()
                .setCaptureMode(ImageCapture.CAPTURE_MODE_MINIMIZE_LATENCY)
                .build()

            val cameraSelector = CameraSelector.Builder()
                .requireLensFacing(lensFacing)
                .build()

            try {
                cameraProvider.unbindAll()
                cameraProvider.bindToLifecycle(
                    this, cameraSelector, preview, imageCapture
                )
            } catch (exc: Exception) {
                Log.e("MainActivity", "Use case binding failed", exc)
            }

        }, ContextCompat.getMainExecutor(this))
    }

    /**
     * Flips between Front and Back camera.
     */
    private fun switchCamera() {
        lensFacing = if (lensFacing == CameraSelector.LENS_FACING_FRONT) {
            CameraSelector.LENS_FACING_BACK
        } else {
            CameraSelector.LENS_FACING_FRONT
        }
        startCamera()
    }

    /**
     * Converts an ImageX ImageProxy to a Bitmap while handling rotation and mirroring.
     */
    private fun imageProxyToBitmap(image: ImageProxy): Bitmap {
        val planeProxy = image.planes[0]
        val buffer = planeProxy.buffer
        val bytes = ByteArray(buffer.remaining())
        buffer.get(bytes)
        val bitmap = BitmapFactory.decodeByteArray(bytes, 0, bytes.size)
        
        val matrix = Matrix()
        matrix.postRotate(image.imageInfo.rotationDegrees.toFloat())
        
        // Mirror horizontally if using front-facing camera
        if (lensFacing == CameraSelector.LENS_FACING_FRONT) {
            matrix.postScale(-1f, 1f, bitmap.width / 2f, bitmap.height / 2f)
        }
        
        return Bitmap.createBitmap(bitmap, 0, 0, bitmap.width, bitmap.height, matrix, true)
    }

    override fun onDestroy() {
        super.onDestroy()
        cameraExecutor.shutdown()
        mainHandler.removeCallbacksAndMessages(null)
    }
}
