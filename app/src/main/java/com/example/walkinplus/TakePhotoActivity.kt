package com.example.walkinplus

import android.Manifest
import android.annotation.SuppressLint
import android.content.Context
import android.content.pm.PackageManager
import android.graphics.*
import android.os.Bundle
import android.util.Log
import android.view.View
import android.widget.Toast
import androidx.appcompat.app.AppCompatActivity
import androidx.camera.core.*
import androidx.camera.core.Camera
import androidx.camera.lifecycle.ProcessCameraProvider
import androidx.camera.view.PreviewView
import androidx.core.app.ActivityCompat
import androidx.core.content.ContextCompat
import com.google.android.gms.tasks.OnSuccessListener
import com.google.mlkit.vision.common.InputImage
import com.google.mlkit.vision.face.FaceDetection
import com.google.mlkit.vision.face.FaceDetectorOptions
import java.io.ByteArrayOutputStream
import java.util.concurrent.Executors

private const val MAX_RESULT_DISPLAY = 2 // Maximum number of results displayed
private const val TAG = "TFL Classify" // Name for logging
private const val REQUEST_CODE_PERMISSIONS = 999 // Return code after asking for permission
private val REQUIRED_PERMISSIONS = arrayOf(Manifest.permission.CAMERA) // permission needed
lateinit var faceDetector: com.google.mlkit.vision.face.FaceDetector


val spoofRate = 0.75
var livenessDone = false
/**
 * Main entry point into TensorFlow Lite Classifier
 */
class TakePhotoActivity : AppCompatActivity() {
    // CameraX variables
    private lateinit var preview: Preview // Preview use case, fast, responsive view of the camera
    private lateinit var imageAnalyzer: ImageAnalysis // Analysis use case, for running ML code
    private lateinit var camera: Camera
    private val cameraExecutor = Executors.newSingleThreadExecutor()

    // Views attachment
    private val viewFinder by lazy {
        findViewById<PreviewView>(R.id.viewFinder) // Display the preview image from Camera
    }

    // Contains the recognition result. Since  it is a viewModel, it will survive screen rotations
//    private val recogViewModel: RecognitionListViewModel by viewModels()

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_main)
        val options = FaceDetectorOptions.Builder()
            .setContourMode(FaceDetectorOptions.CONTOUR_MODE_ALL)
            .setMinFaceSize(0.1f)
            .build()

        faceDetector = FaceDetection.getClient(options)
        // Request camera permissions
        if (allPermissionsGranted()) {
            startCamera()
        } else {
            ActivityCompat.requestPermissions(this, REQUIRED_PERMISSIONS, REQUEST_CODE_PERMISSIONS)
        }
    }

    /**
     * Check all permissions are granted - use for Camera permission in this example.
     */
    private fun allPermissionsGranted(): Boolean = REQUIRED_PERMISSIONS.all {
        ContextCompat.checkSelfPermission(baseContext, it) == PackageManager.PERMISSION_GRANTED
    }

    /**
     * This gets called after the Camera permission pop up is shown.
     */
    override fun onRequestPermissionsResult(requestCode: Int, permissions: Array<String>, grantResults: IntArray) {
        super.onRequestPermissionsResult(requestCode, permissions, grantResults)
        if (requestCode == REQUEST_CODE_PERMISSIONS) {
            if (allPermissionsGranted()) {
                startCamera()
            } else {
                // Exit the app if permission is not granted
                // Best practice is to explain and offer a chance to re-request but this is out of
                // scope in this sample. More details:
                // https://developer.android.com/training/permissions/usage-notes
                Toast.makeText(this, getString(R.string.permission_deny_text), Toast.LENGTH_SHORT)
                    .show()
                finish()
            }
        }
    }

    /**
     * Start the Camera which involves:
     *
     * 1. Initialising the preview use case
     * 2. Initialising the image analyser use case
     * 3. Attach both to the lifecycle of this activity
     * 4. Pipe the output of the preview object to the PreviewView on the screen
     */
    private fun startCamera() {
        val cameraProviderFuture = ProcessCameraProvider.getInstance(this)

        cameraProviderFuture.addListener(Runnable {
            // Used to bind the lifecycle of cameras to the lifecycle owner
            val cameraProvider: ProcessCameraProvider = cameraProviderFuture.get()

            preview = Preview.Builder()
                .build()

            imageAnalyzer = ImageAnalysis.Builder()
                .setBackpressureStrategy(ImageAnalysis.STRATEGY_KEEP_ONLY_LATEST)
                .build()
                .also { analysisUseCase: ImageAnalysis ->
                    analysisUseCase.setAnalyzer(cameraExecutor, ImageAnalyzer(this))
                }
            // Select camera, back is the default. If it is not available, choose front camera
            val cameraSelector = CameraSelector.DEFAULT_FRONT_CAMERA
//                if (cameraProvider.hasCamera(CameraSelector.DEFAULT_BACK_CAMERA))
//                    CameraSelector.DEFAULT_BACK_CAMERA else CameraSelector.DEFAULT_FRONT_CAMERA
            try {
                // Unbind use cases before rebinding
                cameraProvider.unbindAll()
                // Bind use cases to camera - try to bind everything at once and CameraX will find
                // the best combination.
                camera = cameraProvider.bindToLifecycle(this, cameraSelector, preview, imageAnalyzer)
                // Attach the preview to preview view, aka View Finder
                preview.setSurfaceProvider(viewFinder.surfaceProvider)
            } catch (exc: Exception) {
                Log.e(TAG, "Use case binding failed", exc)
            }
        }, ContextCompat.getMainExecutor(this))
    }


    private class ImageAnalyzer(val ctx: Context) :
        ImageAnalysis.Analyzer {
        // TODO 6. Optional GPU acceleration

        @SuppressLint("UnsafeExperimentalUsageError")
        override fun analyze(imageProxy: ImageProxy) {
            Log.e("PrintCount", "test")
//            val mediaImage = imageProxy.image
//            if (mediaImage != null) {
//                checkFace(imageProxy, object : CheckFaceListener {
//                    override fun onSuccess(draw: Draw) {
//                        imageProxy.close()
//                    }
//
//                    override fun onFail() {
//                        imageProxy.close()
//                    }
//                })
//            }
        }

        interface CheckFaceListener {
            fun onSuccess(draw: Draw)
            fun onFail()
        }

        @SuppressLint("UnsafeExperimentalUsageError", "UnsafeOptInUsageError")
        fun checkFace(imageProxy: ImageProxy, listener: CheckFaceListener) {
            val image = InputImage.fromMediaImage(imageProxy.image, imageProxy.imageInfo.rotationDegrees)

            faceDetector.process(image)
                .addOnSuccessListener(OnSuccessListener { faces ->
                    if (faces.size > 0) {
                        Log.e("Status","Found face")
                    } else {
                        Log.e("Status","Not found face")
                    }
                })
                .addOnFailureListener { e ->
                    e.printStackTrace()
                }
        }

        fun toByteArray(bitmap: Bitmap): ByteArray {
            val stream = ByteArrayOutputStream()
            bitmap.compress(Bitmap.CompressFormat.PNG, 90, stream)
            return stream.toByteArray()
        }

        /**
         * Convert Image Proxy to Bitmap
         */
        private val yuvToRgbConverter = YuvToRgbConverter(ctx)
        private lateinit var bitmapBuffer: Bitmap
        private lateinit var rotationMatrix: Matrix

        @SuppressLint("UnsafeExperimentalUsageError", "UnsafeOptInUsageError")
        private fun toBitmap(imageProxy: ImageProxy): Bitmap? {
            val image = imageProxy.image ?: return null
            // Initialise Buffer
            if (!::bitmapBuffer.isInitialized) {
                // The image rotation and RGB image buffer are initialized only once
                Log.d(TAG, "Initalise toBitmap()")
                rotationMatrix = Matrix()
                rotationMatrix.postRotate(imageProxy.imageInfo.rotationDegrees.toFloat())
                bitmapBuffer = Bitmap.createBitmap(imageProxy.width, imageProxy.height, Bitmap.Config.ARGB_8888)
            }
            // Pass image to an image analyser
            yuvToRgbConverter.yuvToRgb(image, bitmapBuffer)
            // Create the Bitmap in the correct orientation
            return Bitmap.createBitmap(bitmapBuffer, 0, 0, bitmapBuffer.width, bitmapBuffer.height, rotationMatrix, false)
        }
    }

    private class Draw(context: Context?, var rect: Rect, var text: String) : View(context) {
        lateinit var paint: Paint
        lateinit var textPaint: Paint

        init {
            init()
        }

        private fun init() {
            paint = Paint()
            paint.color = Color.RED
            paint.strokeWidth = 10f
            paint.style = Paint.Style.STROKE

            textPaint = Paint()
            textPaint.color = Color.RED
            textPaint.style = Paint.Style.FILL
            textPaint.textSize = 60f
        }

        override fun onDraw(canvas: Canvas) {
            super.onDraw(canvas)
            canvas.drawText(text,
                rect.centerX()
                    .toFloat(),
                rect.centerY()
                    .toFloat(),
                textPaint)
            canvas.drawRect(rect, paint)//แก้เป็นวงรี แล้วสุ่มหน้า size ใบหน้า เพื่อป้องกันการปลอมแปลง
        }
    }
}
