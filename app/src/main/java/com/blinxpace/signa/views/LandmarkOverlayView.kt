package com.blinxpace.signa.views


import android.content.Context
import android.graphics.Canvas
import android.graphics.Color
import android.graphics.Paint
import android.graphics.PointF
import android.util.AttributeSet
import android.view.View

class LandmarkOverlayView @JvmOverloads constructor(
    context: Context,
    attrs: AttributeSet? = null,
    defStyleAttr: Int = 0
) : View(context, attrs, defStyleAttr) {

    private val pointPaint = Paint().apply {
        style = Paint.Style.FILL
        strokeWidth = 8f
        isAntiAlias = true
    }

    private val linePaint = Paint().apply {
        style = Paint.Style.STROKE
        strokeWidth = 4f
        isAntiAlias = true
    }

    private var faceLandmarks: List<PointF> = emptyList()
    private var leftHandLandmarks: List<PointF> = emptyList()
    private var rightHandLandmarks: List<PointF> = emptyList()
    private var poseLandmarks: List<PointF> = emptyList()

    fun updateFaceLandmarks(landmarks: List<PointF>) {
        faceLandmarks = landmarks
        postInvalidate()
    }

    fun updateLeftHandLandmarks(landmarks: List<PointF>) {
        leftHandLandmarks = landmarks
        postInvalidate()
    }

    fun updateRightHandLandmarks(landmarks: List<PointF>) {
        rightHandLandmarks = landmarks
        postInvalidate()
    }

    fun updatePoseLandmarks(landmarks: List<PointF>) {
        poseLandmarks = landmarks
        postInvalidate()
    }

    override fun onDraw(canvas: Canvas) {
        super.onDraw(canvas)

        // Draw connections first (lines)
        drawConnections(canvas)

        // Then draw points (so they appear on top)
        drawLandmarks(canvas)
    }

    private fun drawConnections(canvas: Canvas) {
        // Face connections
        linePaint.color = Color.CYAN
        drawConnectionLines(canvas, faceLandmarks, FACE_CONNECTIONS)

        // Left hand connections
        linePaint.color = Color.GREEN
        drawConnectionLines(canvas, leftHandLandmarks, HAND_CONNECTIONS)

        // Right hand connections
        linePaint.color = Color.RED
        drawConnectionLines(canvas, rightHandLandmarks, HAND_CONNECTIONS)

        // Pose connections
        linePaint.color = Color.BLUE
        drawConnectionLines(canvas, poseLandmarks, POSE_CONNECTIONS)
    }

    private fun drawLandmarks(canvas: Canvas) {
        // Face points
        pointPaint.color = Color.CYAN
        drawLandmarkPoints(canvas, faceLandmarks, 6f)

        // Left hand points
        pointPaint.color = Color.GREEN
        drawLandmarkPoints(canvas, leftHandLandmarks, 8f)

        // Right hand points
        pointPaint.color = Color.RED
        drawLandmarkPoints(canvas, rightHandLandmarks, 8f)

        // Pose points
        pointPaint.color = Color.BLUE
        drawLandmarkPoints(canvas, poseLandmarks, 10f)
    }

    private fun drawConnectionLines(canvas: Canvas, landmarks: List<PointF>, connections: List<Pair<Int, Int>>) {
        if (landmarks.size < 2) return

        connections.forEach { (start, end) ->
            if (start < landmarks.size && end < landmarks.size) {
                canvas.drawLine(
                    landmarks[start].x, landmarks[start].y,
                    landmarks[end].x, landmarks[end].y,
                    linePaint
                )
            }
        }
    }

    private fun drawLandmarkPoints(canvas: Canvas, landmarks: List<PointF>, radius: Float) {
        landmarks.forEach { point ->
            canvas.drawCircle(point.x, point.y, radius, pointPaint)
        }
    }

    companion object {
        val FACE_CONNECTIONS = listOf(
            // Basic face outline connections
            10 to 338, 338 to 297, 297 to 332, 332 to 284, 284 to 251, 251 to 389,
            389 to 356, 356 to 454, 454 to 323, 323 to 361, 361 to 288, 288 to 397
        )

        val HAND_CONNECTIONS = listOf(
            // Thumb
            0 to 1, 1 to 2, 2 to 3, 3 to 4,
            // Index finger
            0 to 5, 5 to 6, 6 to 7, 7 to 8,
            // Middle finger
            5 to 9, 9 to 10, 10 to 11, 11 to 12,
            // Ring finger
            9 to 13, 13 to 14, 14 to 15, 15 to 16,
            // Pinky
            13 to 17, 17 to 18, 18 to 19, 19 to 20,
            // Palm
            0 to 17
        )

        val POSE_CONNECTIONS = listOf(
            // Right arm
            11 to 13, 13 to 15,
            // Left arm
            12 to 14, 14 to 16,
            // Shoulders to hips
            11 to 12, 12 to 24, 24 to 23, 23 to 11,
            // Legs
            23 to 25, 25 to 27, 24 to 26, 26 to 28
        )
    }
}