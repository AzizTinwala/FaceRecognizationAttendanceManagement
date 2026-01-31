package com.example.facerecognizationattendancemanagement

import android.animation.ValueAnimator
import android.content.Context
import android.graphics.Canvas
import android.graphics.Color
import android.graphics.LinearGradient
import android.graphics.Paint
import android.graphics.Path
import android.graphics.Shader
import android.util.AttributeSet
import android.view.View
import android.view.animation.LinearInterpolator
import com.google.mlkit.vision.face.Face

/**
 * FaceOverlayView provides a high-tech biometric scanning interface.
 * It features a full-screen laser sweep, detailed face mesh tracking, 
 * and a pulsating "lock-on" ring around the detected face.
 */
class FaceOverlayView @JvmOverloads constructor(
    context: Context,
    attrs: AttributeSet? = null,
    defStyleAttr: Int = 0
) : View(context, attrs, defStyleAttr) {

    private var face: Face? = null
    private var imageWidth: Int = 0
    private var imageHeight: Int = 0

    /* ---------- UI Configuration & Paints ---------- */

    // Electric Cyan color used for all biometric elements
    private val themeColor = Color.parseColor("#00E5FF")

    // Paint for the small dots on facial contours
    private val dotPaint = Paint().apply {
        color = themeColor
        style = Paint.Style.FILL
        isAntiAlias = true
    }
    
    // Paint for the lines connecting facial dots (the mesh)
    private val meshPaint = Paint().apply {
        color = themeColor
        style = Paint.Style.STROKE
        strokeWidth = 1.5f
        isAntiAlias = true
        alpha = 60 // Semi-transparent for a subtle digital look
    }

    // Paint for the full-screen scanning gradient
    private val scanLinePaint = Paint().apply {
        style = Paint.Style.FILL
        isAntiAlias = true
    }

    // Paint for the pulsating lock-on ring
    private val ringPaint = Paint().apply {
        color = themeColor
        style = Paint.Style.STROKE
        strokeWidth = 4f
        isAntiAlias = true
        alpha = 150
    }

    /* ---------- Animators ---------- */

    private var scanLineY = 0f
    private val scanAnimator = ValueAnimator.ofFloat(0f, 1f).apply {
        duration = 3000
        repeatCount = ValueAnimator.INFINITE
        repeatMode = ValueAnimator.REVERSE
        interpolator = LinearInterpolator()
        addUpdateListener {
            scanLineY = it.animatedValue as Float
            postInvalidate()
        }
    }

    private var pulseScale = 1f
    private val pulseAnimator = ValueAnimator.ofFloat(0.95f, 1.05f).apply {
        duration = 800
        repeatCount = ValueAnimator.INFINITE
        repeatMode = ValueAnimator.REVERSE
        interpolator = LinearInterpolator()
        addUpdateListener {
            pulseScale = it.animatedValue as Float
            postInvalidate()
        }
    }

    init {
        // Start scanning effect immediately on init
        scanAnimator.start()
    }

    /**
     * Updates the view with the latest face data from the detector.
     * @param face The detected Face object containing landmarks and contours.
     * @param width The original width of the image processed.
     * @param height The original height of the image processed.
     */
    fun updateFace(face: Face?, width: Int, height: Int) {
        this.face = face
        this.imageWidth = width
        this.imageHeight = height
        
        if (face != null && !pulseAnimator.isStarted) {
            pulseAnimator.start()
        } else if (face == null) {
            pulseAnimator.cancel()
        }
        
        postInvalidate()
    }

    override fun onDraw(canvas: Canvas) {
        super.onDraw(canvas)
        
        // 1. Draw Full Screen Scanning effect (always visible)
        drawFullScreenScanningEffect(canvas)

        // If no face is detected, we don't draw the biometric details
        if (face == null || imageWidth == 0 || imageHeight == 0) return

        // Calculate scaling factors to map camera coordinates to view coordinates
        val scaleX = width.toFloat() / imageWidth
        val scaleY = height.toFloat() / imageHeight

        val currentFace = face!!
        
        // 2. Draw the biometric mesh (Contours)
        drawFaceMesh(canvas, currentFace, scaleX, scaleY)
        
        // 3. Draw the lock-on ring
        drawLockRing(canvas, currentFace, scaleX, scaleY)
    }

    /**
     * Draws a sweeping laser line across the entire screen with a trailing glow.
     */
    private fun drawFullScreenScanningEffect(canvas: Canvas) {
        val viewWidth = width.toFloat()
        val viewHeight = height.toFloat()
        val currentY = viewHeight * scanLineY
        
        // Horizontal scan bar - Gradient Glow
        val scanBarHeight = 150f
        val gradient = LinearGradient(
            0f, currentY - scanBarHeight, 0f, currentY,
            intArrayOf(Color.TRANSPARENT, Color.argb(40, 0, 229, 255), Color.argb(180, 0, 229, 255)),
            null, Shader.TileMode.CLAMP
        )
        scanLinePaint.shader = gradient
        canvas.drawRect(0f, currentY - scanBarHeight, viewWidth, currentY, scanLinePaint)
        
        // Sharp leading laser line
        val linePaint = Paint().apply {
            color = themeColor
            strokeWidth = 5f
            style = Paint.Style.STROKE
            isAntiAlias = true
        }
        canvas.drawLine(0f, currentY, viewWidth, currentY, linePaint)
    }

    /**
     * Draws detailed biometric contours (dots and connecting lines) on the face.
     */
    private fun drawFaceMesh(canvas: Canvas, face: Face, scaleX: Float, scaleY: Float) {
        val allContours = face.allContours
        for (contour in allContours) {
            val points = contour.points
            val path = Path()
            for (i in points.indices) {
                val px = points[i].x * scaleX
                val py = points[i].y * scaleY
                
                // Draw digital data point
                canvas.drawCircle(px, py, 3f, dotPaint)
                
                if (i == 0) path.moveTo(px, py)
                else path.lineTo(px, py)
            }
            // Draw mesh connection line
            canvas.drawPath(path, meshPaint)
        }
    }

    /**
     * Draws a pulsating biometric ring around the detected face.
     */
    private fun drawLockRing(canvas: Canvas, face: Face, scaleX: Float, scaleY: Float) {
        val rect = face.boundingBox
        val cx = (rect.left + rect.right) / 2f * scaleX
        val cy = (rect.top + rect.bottom) / 2f * scaleY
        
        // Use the larger dimension of the bounding box for the radius
        val radius = kotlin.math.max(rect.width(), rect.height()) / 2f * scaleX * pulseScale
        
        canvas.drawCircle(cx, cy, radius, ringPaint)
    }
}
