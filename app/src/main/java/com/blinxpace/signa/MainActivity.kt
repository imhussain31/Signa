package com.blinxpace.signa

import android.Manifest
import android.content.pm.PackageManager
import android.graphics.*
import android.os.Bundle
import android.view.Surface
import android.widget.TextView
import android.widget.Toast
import androidx.appcompat.app.AppCompatActivity
import androidx.camera.core.*
import androidx.camera.lifecycle.ProcessCameraProvider
import androidx.camera.view.PreviewView
import androidx.core.app.ActivityCompat
import androidx.core.content.ContextCompat
import com.blinxpace.signa.views.LandmarkOverlayView
import com.google.android.material.floatingactionbutton.FloatingActionButton
import com.google.mediapipe.formats.proto.LandmarkProto.NormalizedLandmark
import com.google.mediapipe.framework.image.BitmapImageBuilder
import com.google.mediapipe.tasks.core.BaseOptions
import com.google.mediapipe.tasks.vision.core.RunningMode
import com.google.mediapipe.tasks.vision.facelandmarker.FaceLandmarker
import com.google.mediapipe.tasks.vision.facelandmarker.FaceLandmarkerResult
import com.google.mediapipe.tasks.vision.handlandmarker.HandLandmarker
import com.google.mediapipe.tasks.vision.handlandmarker.HandLandmarkerResult
import com.google.mediapipe.tasks.vision.poselandmarker.PoseLandmarker
import com.google.mediapipe.tasks.vision.poselandmarker.PoseLandmarkerResult
import java.io.ByteArrayOutputStream
import java.util.LinkedList
import java.util.Queue
import java.util.concurrent.Executors

class MainActivity : AppCompatActivity() {
    private lateinit var previewView: PreviewView
    private lateinit var overlayView: LandmarkOverlayView
    private lateinit var fabSwitchCamera: FloatingActionButton
    private lateinit var signResultTv: TextView

    private lateinit var faceDetector: FaceLandmarker
    private lateinit var handDetector: HandLandmarker
    private lateinit var poseDetector: PoseLandmarker

    private val cameraExecutor = Executors.newSingleThreadExecutor()
    private val permissionRequestCode = 101
    private var lensFacing = CameraSelector.LENS_FACING_BACK

    private val rotationQueue: Queue<FrameMeta> = LinkedList()

    private val isFrontCamera: Boolean
        get() = lensFacing == CameraSelector.LENS_FACING_FRONT

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_main)
        supportActionBar?.hide()

        previewView = findViewById(R.id.previewView)
        overlayView = findViewById(R.id.overlayView)
        fabSwitchCamera = findViewById(R.id.fabSwitchCamera)
        signResultTv = findViewById(R.id.tvSignResult)

        fabSwitchCamera.setOnClickListener {
            lensFacing = if (lensFacing == CameraSelector.LENS_FACING_BACK)
                CameraSelector.LENS_FACING_FRONT
            else
                CameraSelector.LENS_FACING_BACK
            startCamera()
        }

        setupDetectors()
        requestCameraPermission()
    }

    private fun requestCameraPermission() {
        if (ContextCompat.checkSelfPermission(this, Manifest.permission.CAMERA)
            != PackageManager.PERMISSION_GRANTED
        ) {
            ActivityCompat.requestPermissions(
                this,
                arrayOf(Manifest.permission.CAMERA),
                permissionRequestCode
            )
        } else {
            startCamera()
        }
    }

    private fun setupDetectors() {
        val faceOptions = FaceLandmarker.FaceLandmarkerOptions.builder()
            .setBaseOptions(BaseOptions.builder().setModelAssetPath("face_landmarker.task").build())
            .setRunningMode(RunningMode.LIVE_STREAM)
            .setResultListener { result, _ -> processFaceResult(result) }
            .build()
        faceDetector = FaceLandmarker.createFromOptions(this, faceOptions)

        val handOptions = HandLandmarker.HandLandmarkerOptions.builder()
            .setBaseOptions(BaseOptions.builder().setModelAssetPath("hand_landmarker.task").build())
            .setRunningMode(RunningMode.LIVE_STREAM)
            .setResultListener { result, _ -> processHandResult(result) }
            .build()
        handDetector = HandLandmarker.createFromOptions(this, handOptions)

        val poseOptions = PoseLandmarker.PoseLandmarkerOptions.builder()
            .setBaseOptions(BaseOptions.builder().setModelAssetPath("pose_landmarker.task").build())
            .setRunningMode(RunningMode.LIVE_STREAM)
            .setResultListener { result, _ -> processPoseResult(result) }
            .build()
        poseDetector = PoseLandmarker.createFromOptions(this, poseOptions)
    }

    private fun startCamera() {
        val cameraProviderFuture = ProcessCameraProvider.getInstance(this)
        cameraProviderFuture.addListener({
            val cameraProvider = cameraProviderFuture.get()

            val preview = Preview.Builder().build().also {
                it.setSurfaceProvider(previewView.surfaceProvider)
            }

            val imageAnalysis = ImageAnalysis.Builder()
                .setBackpressureStrategy(ImageAnalysis.STRATEGY_KEEP_ONLY_LATEST)
                .build()
                .also {
                    it.setAnalyzer(cameraExecutor) { imageProxy ->
                        processCameraFrame(imageProxy)
                    }
                }

            try {
                cameraProvider.unbindAll()
                cameraProvider.bindToLifecycle(
                    this,
                    CameraSelector.Builder().requireLensFacing(lensFacing).build(),
                    preview,
                    imageAnalysis
                )
            } catch (e: Exception) {
                e.printStackTrace()
                Toast.makeText(this, "Camera start failed", Toast.LENGTH_SHORT).show()
            }
        }, ContextCompat.getMainExecutor(this))
    }

    private fun processCameraFrame(image: ImageProxy) {
        val bitmap = image.toBitmap()
        val mpImage = BitmapImageBuilder(bitmap).build()
        val rotationDegrees = image.imageInfo.rotationDegrees

        synchronized(rotationQueue) {
            rotationQueue.add(FrameMeta(rotationDegrees))
        }

        faceDetector.detectAsync(mpImage, image.imageInfo.timestamp)
        handDetector.detectAsync(mpImage, image.imageInfo.timestamp)
        poseDetector.detectAsync(mpImage, image.imageInfo.timestamp)

        image.close()
    }

    private fun processFaceResult(result: FaceLandmarkerResult) {
        val rotation = synchronized(rotationQueue) {
            rotationQueue.poll()?.rotationDegrees ?: 0
        }

        val landmarks = result.faceLandmarks().flatMap { faceLandmarks ->
            faceLandmarks.map { NormalizedLandmark.newBuilder().setX(it.x()).setY(it.y()).build() }
        }

        val screenPoints = transformLandmarks(landmarks, overlayView.width, overlayView.height, rotation, isFrontCamera)
        runOnUiThread { overlayView.updateFaceLandmarks(screenPoints) }
    }

    private fun processHandResult(result: HandLandmarkerResult) {
        val rotation = synchronized(rotationQueue) {
            rotationQueue.poll()?.rotationDegrees ?: 0
        }

        val leftPoints = result.landmarks().getOrNull(0)?.map {
            NormalizedLandmark.newBuilder().setX(it.x()).setY(it.y()).build()
        }?.let {
            transformLandmarks(it, overlayView.width, overlayView.height, rotation, isFrontCamera)
        } ?: emptyList()

        val rightPoints = result.landmarks().getOrNull(1)?.map {
            NormalizedLandmark.newBuilder().setX(it.x()).setY(it.y()).build()
        }?.let {
            transformLandmarks(it, overlayView.width, overlayView.height, rotation, isFrontCamera)
        } ?: emptyList()

        runOnUiThread {
            overlayView.updateLeftHandLandmarks(leftPoints)
            overlayView.updateRightHandLandmarks(rightPoints)
        }
    }

    private fun processPoseResult(result: PoseLandmarkerResult) {
        val rotation = synchronized(rotationQueue) {
            rotationQueue.poll()?.rotationDegrees ?: 0
        }

        val landmarks = result.landmarks().flatMap { poseLandmarks ->
            poseLandmarks.map { NormalizedLandmark.newBuilder().setX(it.x()).setY(it.y()).build() }
        }

        val screenPoints = transformLandmarks(landmarks, overlayView.width, overlayView.height, rotation, isFrontCamera)
        runOnUiThread { overlayView.updatePoseLandmarks(screenPoints) }
    }

    private fun transformLandmarks(
        landmarks: List<NormalizedLandmark>,
        width: Int,
        height: Int,
        rotationDegrees: Int,
        isFrontCamera: Boolean
    ): List<PointF> {
        return landmarks.map {
            val x = it.x
            val y = it.y

            val point = when (rotationDegrees) {
                0 -> PointF(x * width, y * height)
                90 -> PointF((1 - y) * width, x * height)
                180 -> PointF((1 - x) * width, (1 - y) * height)
                270 -> PointF(y * width, (1 - x) * height)
                else -> PointF(x * width, y * height)
            }

            if (isFrontCamera) {
                point.x = width - point.x
            }

            point
        }
    }

    override fun onRequestPermissionsResult(requestCode: Int, permissions: Array<out String>, grantResults: IntArray) {
        super.onRequestPermissionsResult(requestCode, permissions, grantResults)
        if (requestCode == permissionRequestCode &&
            grantResults.isNotEmpty() &&
            grantResults[0] == PackageManager.PERMISSION_GRANTED
        ) {
            startCamera()
        }
    }

    override fun onDestroy() {
        super.onDestroy()
        cameraExecutor.shutdown()
        faceDetector.close()
        handDetector.close()
        poseDetector.close()
    }
}

fun ImageProxy.toBitmap(): Bitmap {
    val yBuffer = planes[0].buffer
    val uBuffer = planes[1].buffer
    val vBuffer = planes[2].buffer

    val ySize = yBuffer.remaining()
    val uSize = uBuffer.remaining()
    val vSize = vBuffer.remaining()

    val nv21 = ByteArray(ySize + uSize + vSize)
    yBuffer.get(nv21, 0, ySize)
    vBuffer.get(nv21, ySize, vSize)
    uBuffer.get(nv21, ySize + vSize, uSize)

    val yuvImage = YuvImage(nv21, ImageFormat.NV21, width, height, null)
    val out = ByteArrayOutputStream()
    yuvImage.compressToJpeg(Rect(0, 0, width, height), 100, out)
    val yuv = out.toByteArray()
    val bitmap = BitmapFactory.decodeByteArray(yuv, 0, yuv.size)

    val matrix = Matrix()
    matrix.postRotate(imageInfo.rotationDegrees.toFloat())

    return Bitmap.createBitmap(bitmap, 0, 0, bitmap.width, bitmap.height, matrix, true)
}

// Data class
data class FrameMeta(val rotationDegrees: Int)




