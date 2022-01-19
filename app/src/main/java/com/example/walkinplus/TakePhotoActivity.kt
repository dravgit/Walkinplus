package com.example.walkinplus

import android.Manifest
import android.annotation.SuppressLint
import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import android.content.IntentFilter
import android.content.pm.PackageManager
import android.graphics.*
import android.nfc.NfcAdapter
import android.nfc.NfcManager
import android.nfc.Tag
import android.os.Build
import android.os.Bundle
import android.os.Handler
import android.speech.tts.TextToSpeech
import android.util.Base64
import android.util.Log
import android.view.View
import android.widget.ImageView
import android.widget.LinearLayout
import android.widget.TextView
import android.widget.Toast
import androidx.appcompat.app.AppCompatActivity
import androidx.camera.core.*
import androidx.camera.core.Camera
import androidx.camera.lifecycle.ProcessCameraProvider
import androidx.camera.view.PreviewView
import androidx.core.app.ActivityCompat
import androidx.core.content.ContextCompat
import com.common.pos.api.util.PosUtil
import com.common.thermalimage.HotImageCallback
import com.common.thermalimage.TemperatureBitmapData
import com.common.thermalimage.TemperatureData
import com.common.thermalimage.ThermalImageUtil
import com.example.walkinplus.models.FaceResponseModel
import com.example.walkinplus.utils.NetworkUtil
import com.google.android.gms.tasks.OnSuccessListener
import com.google.mlkit.vision.common.InputImage
import com.google.mlkit.vision.face.FaceDetection
import com.google.mlkit.vision.face.FaceDetectorOptions
import kotlinx.android.synthetic.main.activity_main.*
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
    val ctx: Context = this
    // CameraX variables
    private lateinit var preview: Preview // Preview use case, fast, responsive view of the camera
    private lateinit var imageAnalyzer: ImageAnalysis // Analysis use case, for running ML code
    private lateinit var camera: Camera
    private val cameraExecutor = Executors.newSingleThreadExecutor()
    private val tv_show_nfc: TextView? = null
    private var mNfcAdapter: NfcAdapter? = null
    private var mPendingIntent: PendingIntent? = null
    private val textToSpeech: TextToSpeech? = null

    // Views attachment
    private val viewFinder by lazy {
        findViewById<PreviewView>(R.id.viewFinder) // Display the preview image from Camera
    }

    // Contains the recognition result. Since  it is a viewModel, it will survive screen rotations
//    private val recogViewModel: RecognitionListViewModel by viewModels()

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_main)
        Log.e("Serial", Build.SERIAL)
        val mNfcManager = getSystemService(NFC_SERVICE) as NfcManager
        mNfcAdapter = mNfcManager.defaultAdapter
        mPendingIntent = PendingIntent.getActivity(this, 0, Intent(this, javaClass), 0)
        init_NFC()
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
    override fun onRequestPermissionsResult(
            requestCode: Int,
            permissions: Array<String>,
            grantResults: IntArray
    ) {
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
                        analysisUseCase.setAnalyzer(cameraExecutor, ImageAnalyzer(this,this))
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
                camera = cameraProvider.bindToLifecycle(
                        this,
                        cameraSelector,
                        preview,
                        imageAnalyzer
                )
                // Attach the preview to preview view, aka View Finder
                preview.setSurfaceProvider(viewFinder.surfaceProvider)
            } catch (exc: Exception) {
                Log.e(TAG, "Use case binding failed", exc)
            }
        }, ContextCompat.getMainExecutor(this))
    }


    private class ImageAnalyzer(val ctx: Context, val activity: TakePhotoActivity) :
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
            val temperatureUtil = ThermalImageUtil(ctx)
            val distance = 50f
            val distances: Float = distance
            val temperatureData: TemperatureData? = temperatureUtil?.getDataAndBitmap(distances, true, object : HotImageCallback.Stub() {
                override fun onTemperatureFail(e: String) {
                    Log.e("temp", "1")
                }

                override fun getTemperatureBimapData(data: TemperatureBitmapData) {
                    Log.e("temp", "2")
                }
            })
            if (temperatureData != null) {
                var text = ""
                if (temperatureData?.isUnusualTem) {
                    Log.e("temp", "3")
                } else {
                    Log.e("temp", "4")
                }
            }
            faceDetector.process(image)
                .addOnSuccessListener(OnSuccessListener { faces ->
                    if (faces.size > 0 && sending == false && temperatureData != null) {
                        Log.e("temp", temperatureData.getTemperature().toString())
                        sending = true
                        val base64 = resize?.toBase64String()
                        val temp = temperatureData.getTemperature()
                        val temperature = temperatureData.getTemperature().toString()
//                        Toast(ctx).showCustomToastTempPass(activity, temperature)
                        if (sending == true && temp <= 37.5) {
                            base64?.let {
                                NetworkUtil.CheckFace(it, ctx, temperature, object : NetworkUtil.Companion.NetworkLisener<FaceResponseModel> {
                                            override fun onResponse(response: FaceResponseModel) {
                                                Toast(ctx).showCustomToastFacePass(activity, temperature)
                                                PosUtil.setRelayPower(1)
                                                Handler().postDelayed({
                                                    PosUtil.setRelayPower(0)
                                                    sending = false
                                                }, 3000)
                                            }

                                            override fun onError(errorModel: WalkInPlusErrorModel) {
                                                Toast(ctx).showCustomToastWarning(activity, errorModel.msg)
//                                                Toast.makeText(ctx, errorModel.msg, Toast.LENGTH_LONG)
//                                                        .show()
//                                                Log.e("Error", errorModel.msg)
                                                Handler().postDelayed({
                                                    sending = false
                                                }, 3000)
                                            }

                                            override fun onExpired() {
                                                Toast.makeText(ctx, "Expired", Toast.LENGTH_LONG).show()
                                            }
                                        },
                                        FaceResponseModel::class.java
                                )
                            }
                        }else{
                            Handler().postDelayed({
                                sending = false
                            }, 3000)
                            Toast(ctx).showCustomToastFaceNotPass(activity, temperature)
                        }
                        val face = faces[0]
                        val faceWidth = face.boundingBox.width()
                        Log.e("Status", faces.size.toString())
                        Log.e("panya", "faceWidth : " + faceWidth)
                        val element = Draw(
                                ctx,
                                face.boundingBox,
                                face.trackingId?.toString() ?: "Undefined"
                        )
                        Log.e("Status", "Found face")
                        listener.onSuccess(element)
                    } else {
                        Log.e("Status", "Not found face")
                        Log.e("Status", faces.size.toString())
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
                compress(Bitmap.CompressFormat.JPEG, 10, this)
                return Base64.encodeToString(toByteArray(), Base64.DEFAULT)
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
                bitmapBuffer = Bitmap.createBitmap(
                        imageProxy.width,
                        imageProxy.height,
                        Bitmap.Config.ARGB_8888
                )
            }
            // Pass image to an image analyser
            yuvToRgbConverter.yuvToRgb(image, bitmapBuffer)
            // Create the Bitmap in the correct orientation
            return Bitmap.createBitmap(
                    bitmapBuffer,
                    0,
                    0,
                    bitmapBuffer.width,
                    bitmapBuffer.height,
                    rotationMatrix,
                    false
            )
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
            canvas.drawText(
                    text,
                    rect.centerX()
                            .toFloat(),
                    rect.centerY()
                            .toFloat(),
                    textPaint
            )
            canvas.drawRect(rect, paint)//แก้เป็นวงรี แล้วสุ่มหน้า size ใบหน้า เพื่อป้องกันการปลอมแปลง
        }
    }

    override fun onResume() {
        super.onResume()
        if (mNfcAdapter != null) {
            mNfcAdapter!!.enableForegroundDispatch(this, mPendingIntent, null, null)
            if (NfcAdapter.ACTION_TECH_DISCOVERED == this.intent.action) {
                processIntent(this.intent)
            }
        }
    }

    override fun onNewIntent(intent: Intent) {
        super.onNewIntent(intent)
        processIntent(intent)
    }

    fun processIntent(intent: Intent) {
        var data: String? = null
        val tag = intent.getParcelableExtra<Tag>(NfcAdapter.EXTRA_TAG)
        val techList = tag!!.techList
        var ID: ByteArray? = ByteArray(20)
        Log.e("test", ID.toString())
        data = tag.toString()
        ID = tag.id
        val code = ByteArrayToHexString(ID)
        if (code != null) {
            NetworkUtil.CheckNfc(code, ctx, object : NetworkUtil.Companion.NetworkLisener<FaceResponseModel> {
                override fun onResponse(response: FaceResponseModel) {
                    PosUtil.setRelayPower(1)
                    Toast(ctx).showCustomToastPass(this@TakePhotoActivity)
                    Handler().postDelayed({
                        PosUtil.setRelayPower(0)
                    }, 3000)
                }

                override fun onError(errorModel: WalkInPlusErrorModel) {
                    Toast(ctx).showCustomToastNotPass(this@TakePhotoActivity)
                }

                override fun onExpired() {
                    Log.e("Expire", "Expire")
                }
            },
                    FaceResponseModel::class.java
            )
        }
//        data += """
//
//
//             UID:
//             ${ByteArrayToHexString(ID)}
//             """.trimIndent()
//        data += "\nData format:"
//        for (tech in techList) {
//            data += """
//
//            $tech
//            """.trimIndent()
//        }
        Log.e("data", data)
    }

    override fun onPause() {
        super.onPause()
        if (mNfcAdapter != null) {
            stopNFC_Listener()
        }
    }

    private fun ByteArrayToHexString(inarray: ByteArray): String? {
        Log.e("byte", inarray.toString())
        var i: Int
        var j: Int
        var `in`: Int
        val hex =
            arrayOf("0", "1", "2", "3", "4", "5", "6", "7", "8", "9", "a", "b", "c", "d", "e", "f")
        var out = ""
        j = 0
        while (j < inarray.size) {
            `in` = inarray[j].toInt() and 0xff
            i = `in` shr 4 and 0x0f
            out += hex[i]
            i = `in` and 0x0f
            out += hex[i]
            ++j
        }
        return "0x" + out
    }

    private fun init_NFC() {
        val tagDetected = IntentFilter(NfcAdapter.ACTION_TECH_DISCOVERED)
        tagDetected.addCategory(Intent.CATEGORY_DEFAULT)
    }

    private fun stopNFC_Listener() {
        mNfcAdapter!!.disableForegroundDispatch(this)
    }
}
