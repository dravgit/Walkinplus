package com.example.walkinplus

import android.Manifest
import android.annotation.SuppressLint
import android.content.Context
import android.content.pm.PackageManager
import android.graphics.*
import android.os.Bundle
import android.os.Handler
import android.util.Base64
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
import com.example.walkinplus.utils.NetworkUtil
import com.example.walkinplus.models.FaceResponseModel
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
            val mediaImage = imageProxy.image
            if (mediaImage != null) {
                checkFace(imageProxy, object : CheckFaceListener {
                    override fun onSuccess(draw: Draw) {
                        val face = toBitmap(imageProxy)
                        Log.e("checkFace", "onSuccess")
                        imageProxy.close()
                    }

                    override fun onFail() {
                        imageProxy.close()
                    }
                })
            }
        }

        interface CheckFaceListener {
            fun onSuccess(draw: Draw)
            fun onFail()
        }

        @SuppressLint("UnsafeExperimentalUsageError")
        fun checkFace(imageProxy: ImageProxy, listener: CheckFaceListener) {
            val bitmap = toBitmap(imageProxy)
            val resize = bitmap?.let { Bitmap.createScaledBitmap(it, 480, 880, false) }
            val image = InputImage.fromBitmap(resize, 0)
            faceDetector.process(image)
                .addOnSuccessListener(OnSuccessListener { faces ->
                    if (faces.size > 0 && sending == false) {
                        sending = true
                        val base64 = resize?.toBase64String()
                        if(sending == true){
                            base64?.let {
                                NetworkUtil.CheckFace(it, "edc_id", object : NetworkUtil.Companion.NetworkLisener<FaceResponseModel> {
                                    override fun onResponse(response: FaceResponseModel) {
                                        Toast.makeText(ctx, "Success", Toast.LENGTH_LONG).show()
                                        Handler().postDelayed({
                                            sending = false
                                        }, 3000)
                                    }

                                    override fun onError(errorModel: WalkInPlusErrorModel) {
                                        Toast.makeText(ctx, errorModel.msg, Toast.LENGTH_LONG).show()
                                        Log.e("Error",errorModel.msg)
                                        Handler().postDelayed({
                                            sending = false
                                        }, 3000)
                                    }

                                    override fun onExpired() {
                                        Toast.makeText(ctx, "Expired", Toast.LENGTH_LONG).show()
                                    }
                                }, FaceResponseModel::class.java)
                            }
                        }
                        val face = faces[0]
                        val faceWidth = face.boundingBox.width()
                        Log.e("Status",faces.size.toString())
                        Log.e("panya", "faceWidth : " + faceWidth)
                        val element = Draw(ctx, face.boundingBox, face.trackingId?.toString() ?: "Undefined")
                        Log.e("Status","Found face")
                        listener.onSuccess(element)
                    } else {
                        Log.e("Status","Not found face")
                        Log.e("Status",faces.size.toString())
                        listener.onFail()
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

        fun Bitmap.toBase64String():String{
            ByteArrayOutputStream().apply {
                compress(Bitmap.CompressFormat.JPEG,10,this)
                return Base64.encodeToString(toByteArray(),Base64.DEFAULT)
            }
        }

        private var sending: Boolean = false

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
