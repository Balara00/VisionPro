package com.example.visionpro

import android.Manifest
import android.app.AlertDialog
import android.app.Dialog
import android.content.Context
import android.content.pm.PackageManager
import android.graphics.Color
import android.os.Bundle
import android.os.Process
import android.util.Log
import android.view.SurfaceView
import android.view.View
import android.view.WindowManager
import android.widget.*
import androidx.activity.result.contract.ActivityResultContracts
import androidx.appcompat.app.AppCompatActivity
import androidx.appcompat.widget.SwitchCompat
import androidx.core.content.ContextCompat
import androidx.fragment.app.DialogFragment
import androidx.lifecycle.lifecycleScope
import com.example.visionpro.camera.CameraSource
import com.example.visionpro.data.Device
import com.example.visionpro.ml.ModelType
import com.example.visionpro.ml.MoveNet
import com.example.visionpro.ml.PoseClassifier
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch


class MainActivity : AppCompatActivity() {
    private var cameraSource: CameraSource? = null

    companion object {
        private const val FRAGMENT_DIALOG = "dialog"
    }

    /** A [SurfaceView] for camera preview.   */
    private lateinit var surfaceView: SurfaceView

    /** Default pose estimation model is 0 (MoveNet Thunder)
     * 0 == MoveNet Thunder model
     * 1 == MoveNet Lightning model
     **/
    private var modelPos = 0

    private lateinit var spnModel: Spinner

    /** Default device is CPU */
    private var device = Device.CPU

    private lateinit var tvFPS: TextView
    private lateinit var tvScore: TextView
    private lateinit var tvClassificationValue: TextView
    private lateinit var swClassification: SwitchCompat
    private lateinit var vClassificationOption: View

    private var isClassifyPose = false

//    private lateinit var tvAngle0: TextView
//    private lateinit var tvAngle1: TextView
//    private lateinit var tvAngle2: TextView
//    private lateinit var tvAngle3: TextView
//    private lateinit var tvAngle4: TextView
//    private lateinit var tvAngle5: TextView
//    private lateinit var tvAngle6: TextView
//    private lateinit var tvAngle7: TextView

    @ExperimentalStdlibApi
    private val requestPermissionLauncher =
            registerForActivityResult(
                    ActivityResultContracts.RequestPermission()
            ) { isGranted: Boolean ->
                if (isGranted) {
                    // Permission is granted. Continue the action or workflow in your
                    // app.
                    openCamera(this)
                } else {
                    // Explain to the user that the feature is unavailable because the
                    // features requires a permission that the user has denied. At the
                    // same time, respect the user's decision. Don't link to system
                    // settings in an effort to convince the user to change their
                    // decision.
                    ErrorDialog.newInstance(getString(R.string.vp_request_permission))
                            .show(supportFragmentManager, FRAGMENT_DIALOG)
                }
            }

    private var changeModelListener = object : AdapterView.OnItemSelectedListener {
        override fun onNothingSelected(parent: AdapterView<*>?) {
            //
        }

        override fun onItemSelected(parent: AdapterView<*>?, view: View?, position: Int, id: Long) {
            (parent!!.getChildAt(0) as TextView).setTextColor(Color.parseColor("#0E3D75"))
            changeModel(position)
        }
    }

    private var setClassificationListener =
            CompoundButton.OnCheckedChangeListener { _, isChecked ->
                showClassificationResult(isChecked)
                isClassifyPose = isChecked
                isPoseClassifier()
            }

    @ExperimentalStdlibApi
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_main)

        window.addFlags(WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON)

        tvScore = findViewById(R.id.tvScore)
        tvFPS = findViewById(R.id.tvFps)
        surfaceView = findViewById(R.id.surfaceView)
        spnModel = findViewById(R.id.spnModel)
        surfaceView = findViewById(R.id.surfaceView)
        tvClassificationValue = findViewById(R.id.tvClassificationValue)
        swClassification = findViewById(R.id.swPoseClassification)
        vClassificationOption = findViewById(R.id.vClassificationOption)

//        tvAngle0 = findViewById(R.id.tvAngle0)
//        tvAngle1 = findViewById(R.id.tvAngle1)
//        tvAngle2 = findViewById(R.id.tvAngle2)
//        tvAngle3 = findViewById(R.id.tvAngle3)
//        tvAngle4 = findViewById(R.id.tvAngle4)
//        tvAngle5 = findViewById(R.id.tvAngle5)
//        tvAngle6 = findViewById(R.id.tvAngle6)
//        tvAngle7 = findViewById(R.id.tvAngle7)

        initSpinner()
        spnModel.setSelection(modelPos)
        swClassification.setOnCheckedChangeListener(setClassificationListener)
        if (!isCameraPermissionGranted()) {
            requestPermission()
        }

    }

    @ExperimentalStdlibApi
    override fun onStart() {
        super.onStart()
        openCamera(this)
    }

    override fun onResume() {
        if (cameraSource == null) {
            Log.e("CameraSource", "Camera is null in onResume. Make sure it is initialized properly.")
        } else {
            cameraSource?.resume()
        }
        super.onResume()
    }

    override fun onPause() {
        cameraSource?.close()
        cameraSource = null
        super.onPause()
    }

    // check if permission is granted or not.
    private fun isCameraPermissionGranted(): Boolean {
        return checkPermission(
                Manifest.permission.CAMERA,
                Process.myPid(),
                Process.myUid()
        ) == PackageManager.PERMISSION_GRANTED
    }

    // open camera
    @ExperimentalStdlibApi
    private fun openCamera(context: Context) {
        if (isCameraPermissionGranted()) {
            if (cameraSource == null) {
                cameraSource = CameraSource(surfaceView, object : CameraSource.CameraSourceListener {
                    override fun onFPSListener(fps: Int) {
                        tvFPS.text = getString(R.string.vp_tv_fps, fps)
                    }

                    override fun onDetectedInfo(
                            personScore: Float?,
                            poseLabels: List<Pair<String, Float>>?
                    ) {
                        tvScore.text = getString(R.string.vp_tv_score, personScore ?: 0f)
                        poseLabels?.sortedByDescending { it.second }?.let {
                            tvClassificationValue.text = getString(
                                    R.string.vp_tv_classification_value,
                                    convertPoseLabels(if (it.isNotEmpty()) it[0] else null)
                            )
                        }
                    }
                }).apply {
                    prepareCamera()
                }
                isPoseClassifier()
                lifecycleScope.launch(Dispatchers.Main) {
                    try {
                        cameraSource?.initCamera(context)
                    } catch (e: Exception) {
                        e.printStackTrace()
                        // Log the exception or show a Toast with the error message
                    }
                }
            }
            createPoseEstimator()
        }
    }

    private fun convertPoseLabels(pair: Pair<String, Float>?): String {
        if (pair == null) return "empty"
        return "${pair.first} (${String.format("%.2f", pair.second)})"
    }

    private fun isPoseClassifier() {
        cameraSource?.setClassifier(if (isClassifyPose) PoseClassifier.create(this) else null)
    }

    // Initialize spinners to let user select model
    private fun initSpinner() {
        ArrayAdapter.createFromResource(
                this,
                R.array.vp_models_array,
                android.R.layout.simple_spinner_item
        ).also { adapter ->
            adapter.setDropDownViewResource(android.R.layout.simple_spinner_dropdown_item)

            spnModel.adapter = adapter
            spnModel.onItemSelectedListener = changeModelListener
        }
    }

    //Change model when app is running
    private fun changeModel(position: Int) {
        if (modelPos == position) return
        modelPos = position
        createPoseEstimator()
    }

    private fun createPoseEstimator() {
        val poseDetector = when (modelPos) {
            0 -> {
                // For MoveNet Thunder (SinglePose)
                showPoseClassifier(true)
                showDetectionScore(true)
//                showAngle(true)
                MoveNet.create(this, device, ModelType.Thunder)

            }
            1 -> {
                // MoveNet Lightning (SinglePose)
                showPoseClassifier(true)
                showDetectionScore(true)
//                showAngle(true)
                MoveNet.create(this, device, ModelType.Lightning)
            }
            else -> {
                null
            }
        }
        poseDetector?.let { detector ->
            cameraSource?.setDetector(detector)
        }
    }

    // Show/hide the pose classification option.
    private fun showPoseClassifier(isVisible: Boolean) {
        vClassificationOption.visibility = if (isVisible) View.VISIBLE else View.GONE
        if (!isVisible) {
            swClassification.isChecked = false
        }
    }

    // Show/hide the detection score.
    private fun showDetectionScore(isVisible: Boolean) {
        tvScore.visibility = if (isVisible) View.VISIBLE else View.GONE
    }

    // Show/hide classification result.
    private fun showClassificationResult(isVisible: Boolean) {
        val visibility = if (isVisible) View.VISIBLE else View.GONE
        tvClassificationValue.visibility = visibility
    }

//    private fun showAngle(isVisible: Boolean) {
//        val visibility = if (isVisible) View.VISIBLE else View.GONE
////        tvAngle.visibility = visibility
////        tvAngle.text = getString
//    }

    @ExperimentalStdlibApi
    private fun requestPermission() {
        when (PackageManager.PERMISSION_GRANTED) {
            ContextCompat.checkSelfPermission(
                    this,
                    Manifest.permission.CAMERA
            ) -> {
                // You can use the API that requires the permission.
                openCamera(this)
            }
            else -> {
                // You can directly ask for the permission.
                // The registered ActivityResultCallback gets the result of this request.
                requestPermissionLauncher.launch(
                        Manifest.permission.CAMERA
                )
            }
        }
    }

    private fun showToast(message: String) {
        Toast.makeText(this, message, Toast.LENGTH_LONG).show()
    }

    /**
     * Shows an error message dialog.
     */
    class ErrorDialog : DialogFragment() {

        override fun onCreateDialog(savedInstanceState: Bundle?): Dialog =
            AlertDialog.Builder(activity)
                .setMessage(requireArguments().getString(ARG_MESSAGE))
                .setPositiveButton(android.R.string.ok) { _, _ ->
                    // do nothing
                }
                .create()

        companion object {

            @JvmStatic
            private val ARG_MESSAGE = "message"

            @JvmStatic
            fun newInstance(message: String): ErrorDialog = ErrorDialog().apply {
                arguments = Bundle().apply { putString(ARG_MESSAGE, message) }
            }
        }
    }
}