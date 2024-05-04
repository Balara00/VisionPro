package com.example.visionpro.ml

import android.content.Context
import android.graphics.Bitmap
import android.util.Log
import com.example.visionpro.data.Person
import org.tensorflow.lite.Interpreter
import org.tensorflow.lite.support.common.FileUtil
import java.util.*

class PoseClassifier (
        private val interpreter: Interpreter,
        private val labels: List<String>
        ) {
    private val input = interpreter.getInputTensor(0).shape()
    private val output = interpreter.getOutputTensor(0).shape()


    companion object {
       private const val MODEL_FILENAME = "keypoints_to_pose_classifier_v4_21_poses.tflite"
//        private const val MODEL_FILENAME = "classifier.tflite"
        private const val LABELS_FILENAME = "labels.txt"
        private const val CPU_NUM_THREADS = 4

        fun create(context: Context): PoseClassifier {
            val options = Interpreter.Options().apply {
                setNumThreads(CPU_NUM_THREADS)
            }

            return PoseClassifier(
                    Interpreter(
                            FileUtil.loadMappedFile(
                                    context, MODEL_FILENAME
                            ), options
                    ),
                    FileUtil.loadLabels(context, LABELS_FILENAME),
            )
        }


    }

    fun classify(person: Person?): List<Pair<String, Float>> {
        Log.i("PersonClassify: ", ""+person)

        // Preprocess the pose estimation result to a flat array
        val inputVector = FloatArray(input[1])
        person?.keyPoints?.forEachIndexed { index, keyPoint ->
//            val x = keyPoint.coordinate.x * widthRatio
//            val y = keyPoint.coordinate.y * heightRatio

            inputVector[index * 3] = keyPoint.coordinate.y / (256 * keyPoint.heightRatio)
            inputVector[index * 3 + 1] = keyPoint.coordinate.x / (256 * keyPoint.widthRatio)
            inputVector[index * 3 + 2] = keyPoint.score

        }

        Log.i("InputVector: ", Arrays.toString(inputVector))

//      Postprocess the model output to human readable class names
        val outputTensor = FloatArray(output[1])

        val startTime = System.nanoTime()

        interpreter.run(arrayOf(inputVector), arrayOf(outputTensor))

        val endTime = System.nanoTime()
        val classificationInferenceTimeMs = (endTime - startTime) / 1000000.0
//        val formattedString = String.format("%.3f", classificationInferenceTimeMs)

        Log.i("Classification Inference Time : ", "$classificationInferenceTimeMs ms")

        Log.i("OutputTensorAfter : ", Arrays.toString(outputTensor))

        val output = mutableListOf<Pair<String, Float>>()

        outputTensor.forEachIndexed { index, score ->
            output.add(Pair(labels[index], score))
            Log.i("index", ""+index+""+labels[index])
        }

        // Log the output string
        Log.d("OutputLog", output.toString())
        // ------------------------------------------------ //

        return output
    }


    fun close() {
        interpreter.close()
    }
}