package com.example.visionpro.camera

import android.annotation.SuppressLint
import android.content.Context
import android.graphics.Bitmap
import android.graphics.ImageFormat
import android.graphics.Matrix
import android.graphics.Rect
import android.hardware.camera2.CameraCaptureSession
import android.hardware.camera2.CameraCharacteristics
import android.hardware.camera2.CameraDevice
import android.hardware.camera2.CameraManager
import android.media.ImageReader
import android.os.Handler
import android.os.HandlerThread
import android.util.Log
import android.view.SurfaceView
import com.example.visionpro.YuvToRgbConverter
import com.example.visionpro.data.Person
import com.example.visionpro.ml.PoseDetector
import kotlinx.coroutines.suspendCancellableCoroutine
import kotlin.coroutines.resume
import kotlin.coroutines.resumeWithException
import android.view.Surface
import com.example.visionpro.R
import com.example.visionpro.VisualizationUtils
import com.example.visionpro.ml.PoseClassifier
import java.util.*

class CameraSource (
    private val surfaceView: SurfaceView,
    private val listener: CameraSourceListener? = null
) {
    companion object {
        private const val PREVIEW_WIDTH = 640
        private const val PREVIEW_HEIGHT = 480

        /** Threshold for confidence score. */
        private const val MIN_CONFIDENCE = .2f
        private const val TAG = "Camera Source"
    }

    /** Frame count that have been processed so far in an one second interval to calculate FPS. */
    private var fpsTimer: Timer? = null
    private var frameProcessedInOneSecondInterval = 0
    private var framesPerSecond = 0

    /** The [CameraDevice] that will be opened in this fragment */
    private var camera: CameraDevice? = null

    /** Readers used as buffers for camera still shots */
    private var imageReader: ImageReader? = null

    /** [HandlerThread] where all buffer reading operations run */
    private var imageReaderThread: HandlerThread? = null

    /** [Handler] corresponding to [imageReaderThread] */
    private var imageReaderHandler: Handler? = null

    private var cameraId: String = ""

    private lateinit var imageBitmap: Bitmap
    private var yuvConverter: YuvToRgbConverter = YuvToRgbConverter(surfaceView.context)
    private val lock = Any()
    private var detector: PoseDetector? = null
    private var classifier: PoseClassifier? = null

    /** Internal reference to the ongoing [CameraCaptureSession] configured with our parameters */
    private var session: CameraCaptureSession? = null

    /** Detects, characterizes, and connects to a CameraDevice (used for all camera operations) */
    private val cameraManager: CameraManager by lazy {
        val context = surfaceView.context
        context.getSystemService(Context.CAMERA_SERVICE) as CameraManager
    }

    @ExperimentalStdlibApi
    suspend fun initCamera(context: Context) {
        try{
            camera = openCamera(cameraManager, cameraId)
            imageReader = ImageReader.newInstance(PREVIEW_WIDTH, PREVIEW_HEIGHT, ImageFormat.YUV_420_888, 3)
            imageReader?.setOnImageAvailableListener({ reader ->
                val image = reader.acquireLatestImage()
                if (image != null) {
                    if (!::imageBitmap.isInitialized) {
                        imageBitmap = Bitmap.createBitmap(
                            PREVIEW_WIDTH,
                            PREVIEW_HEIGHT,
                            Bitmap.Config.ARGB_8888)
                    }
                    yuvConverter.yuvToRgb(image, imageBitmap)
                    // Create rotated version for portrait display
                    val rotateMatrix = Matrix()
//                    rotateMatrix.postRotate(90.0f)
                    rotateMatrix.postRotate(270.0f)

                    val rotatedBitmap = Bitmap.createBitmap(
                        imageBitmap,
                        0,
                        0,
                        PREVIEW_WIDTH,
                        PREVIEW_HEIGHT,
                        rotateMatrix,
                        false)
                    processImage(rotatedBitmap, context)
                    image.close()
                }
            }, imageReaderHandler)

            imageReader?.surface?.let { surface ->
                session = createSession(listOf(surface))
                val cameraRequest = camera?.createCaptureRequest(
                        CameraDevice.TEMPLATE_PREVIEW
                )?.apply {
                    addTarget(surface)
                }
                cameraRequest?.build()?.let {
                    session?.setRepeatingRequest(it, null, null)
                }

            }
            Log.i("CameraSource", "Initialized camera")
        } catch (e: Exception) {
            e.printStackTrace()
            Log.e("CameraSource", "Error initializing camera: ${e.message}")
            // Log the exception or show a Toast with the error message
        }

    }

    private suspend fun createSession(targets: List<Surface>) : CameraCaptureSession = suspendCancellableCoroutine { cont ->
        camera?.createCaptureSession(targets, object: CameraCaptureSession.StateCallback() {
            override fun onConfigured(captureSession: CameraCaptureSession) =
                    cont.resume(captureSession)

            override fun onConfigureFailed(session: CameraCaptureSession) {
                cont.resumeWithException(Exception("Session error"))
            }
        }, null)
    }

    @SuppressLint("MissingPermission")
    private suspend fun openCamera(manager: CameraManager, cameraId: String): CameraDevice =
            suspendCancellableCoroutine { cont ->
                manager.openCamera(cameraId, object : CameraDevice.StateCallback() {
                    override fun onOpened(camera: CameraDevice) = cont.resume(camera)

                    override fun onDisconnected(camera: CameraDevice) {
                        camera.close()
                    }

                    override fun onError(camera: CameraDevice, error: Int) {
                        if (cont.isActive) cont.resumeWithException(Exception("Camera error"))
                    }
                }, imageReaderHandler)
            }

//    fun prepareCamera() {
//        for (cameraId in cameraManager.cameraIdList) {
//            val characteristics = cameraManager.getCameraCharacteristics(cameraId)
//
//            val cameraDirection = characteristics.get(CameraCharacteristics.LENS_FACING)
//
//            if (cameraDirection != null && cameraDirection == CameraCharacteristics.LENS_FACING_FRONT) {
//                continue
//            }
//            this.cameraId = cameraId
//            Log.d("CameraSource", "Selected cameraId: $cameraId")
//        }
//    }

    fun prepareCamera() {
        for (cameraId in cameraManager.cameraIdList) {
            val characteristics = cameraManager.getCameraCharacteristics(cameraId)

            val cameraDirection = characteristics.get(CameraCharacteristics.LENS_FACING)
            if (cameraDirection != null && cameraDirection == CameraCharacteristics.LENS_FACING_FRONT) {
                // Use the first front-facing camera found
                this.cameraId = cameraId
                break
            }
        }
    }

    fun setDetector(detector: PoseDetector) {
        synchronized(lock) {
            if (this.detector != null) {
                this.detector?.close()
                this.detector = null
            }
            this.detector = detector
        }
    }

    fun setClassifier(classifier: PoseClassifier?) {
        synchronized(lock) {
            if (this.classifier != null) {
                this.classifier?.close()
                this.classifier = null
            }
            this.classifier = classifier
        }
    }

    fun resume() {
        imageReaderThread = HandlerThread("imageReaderThread").apply { start() }
        imageReaderHandler = Handler(imageReaderThread!!.looper)
        fpsTimer = Timer()
        fpsTimer?.scheduleAtFixedRate(
                object : TimerTask() {
                    override fun run() {
                        framesPerSecond = frameProcessedInOneSecondInterval
                        frameProcessedInOneSecondInterval = 0
                    }
                },
                0,
                1000
        )
    }

    fun close() {
        session?.close()
        session = null
        camera?.close()
        camera = null
        imageReader?.close()
        imageReader = null
        stopImageReaderThread()
        detector?.close()
        detector = null
        classifier?.close()
        classifier = null
        fpsTimer?.cancel()
        fpsTimer = null
        frameProcessedInOneSecondInterval = 0
        framesPerSecond = 0
    }

    @ExperimentalStdlibApi
    private fun processImage(bitmap: Bitmap, context: Context) {
        val persons = mutableListOf<Person>()
        var classificationResult: List<Pair<String, Float>>? = null

        synchronized(lock) {
            val startTimeDetector = System.nanoTime()

            detector?.estimatePoses(bitmap)?.let {
                val endTimeDetector = System.nanoTime()
                val detectorInferenceTimeMs = (endTimeDetector - startTimeDetector) / 1000000.0
                Log.i("Detector Inference Time (ms): ", "$detectorInferenceTimeMs ms")

                persons.addAll(it)
        // if the model only returns one item, allow running the Pose classifier.
                if (persons.isNotEmpty()) {
                    classifier?.run {
                        classificationResult = classify(persons[0])

                    }
                }
            }



        }
        frameProcessedInOneSecondInterval++

        if(frameProcessedInOneSecondInterval == 1) {
            // send fps to view
            listener?.onFPSListener(framesPerSecond)
        }

        // if the model returns only one item, show that item's score.
        if (persons.isNotEmpty()) {
            listener?.onDetectedInfo(persons[0].score, classificationResult)
        }

        var pose: String = ""
        classificationResult?.sortedByDescending { it.second }?.let {
            pose = if (it.isNotEmpty()) it[0].first else ""
        }
        visualize(persons, bitmap, pose, context)
    }


    @ExperimentalStdlibApi
    private fun visualize(persons: List<Person>, bitmap: Bitmap, pose: String, context: Context) {
        val outputBitmap = VisualizationUtils.drawBodyKeypoints(
            bitmap,
            persons.filter { it.score > MIN_CONFIDENCE },
                pose,
                context
        )

        val holder = surfaceView.holder
        val surfaceCanvas = holder.lockCanvas()
        surfaceCanvas?.let { canvas ->
            val screenWidth: Int
            val screenHeight: Int
            val left: Int
            val top: Int

            if (canvas.height > canvas.width) {
                val ratio = outputBitmap.height.toFloat() / outputBitmap.width
                screenWidth = canvas.width
                left = 0
                screenHeight = (canvas.width * ratio).toInt()
                top = (canvas.height - screenHeight) / 2
            } else {
                val ratio = outputBitmap.width.toFloat() / outputBitmap.height
                screenHeight = canvas.height
                top = 0
                screenWidth = (canvas.height * ratio).toInt()
                left = (canvas.width - screenWidth) / 2
            }
            val right: Int = left + screenWidth
            val bottom: Int = top + screenHeight

            canvas.drawBitmap(
                outputBitmap, Rect(0, 0, outputBitmap.width, outputBitmap.height),
                Rect(left, top, right, bottom), null
            )
            surfaceView.holder.unlockCanvasAndPost(canvas)
        }
    }

    private fun stopImageReaderThread() {
        imageReaderThread?.quitSafely()
        try {
            imageReaderThread?.join()
            imageReaderThread = null
            imageReaderHandler = null
        } catch (e: InterruptedException) {
            Log.d(TAG, e.message.toString())
        }
    }

    interface CameraSourceListener {
        fun onFPSListener(fps: Int)

        fun onDetectedInfo(personScore: Float?, poseLabels: List<Pair<String, Float>>?)
    }

}

