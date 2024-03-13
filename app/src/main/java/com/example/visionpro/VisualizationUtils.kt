package com.example.visionpro

import android.content.Context
import android.graphics.Bitmap
import android.graphics.Canvas
import android.graphics.Color
import android.graphics.Paint
import android.util.Log
import com.example.visionpro.data.BodyPart
import com.example.visionpro.data.Person
import com.example.visionpro.data.YogaPose
import kotlin.math.*
import com.google.gson.Gson
import java.io.BufferedReader
import java.io.File
import java.io.InputStreamReader

object VisualizationUtils {
    /** Radius of circle used to draw keypoints.  */
    private const val CIRCLE_RADIUS = 6f

    private const val ANGLE_CIRCLE_RADIUS = 15f

    /** Width of line used to connected two keypoints.  */
    private const val LINE_WIDTH = 4f

    /** The text size of the   */
    private const val ANGLE_TEXT_SIZE = 25f

    private const val ERROR_TEXT_SIZE = 25f

    /** Pair of keypoints to draw lines between.  */
    private val bodyJoints = listOf(
        Pair(BodyPart.NOSE, BodyPart.LEFT_EYE),
        Pair(BodyPart.NOSE, BodyPart.RIGHT_EYE),
        Pair(BodyPart.LEFT_EYE, BodyPart.LEFT_EAR),
        Pair(BodyPart.RIGHT_EYE, BodyPart.RIGHT_EAR),
        Pair(BodyPart.NOSE, BodyPart.LEFT_SHOULDER),
        Pair(BodyPart.NOSE, BodyPart.RIGHT_SHOULDER),
        Pair(BodyPart.LEFT_SHOULDER, BodyPart.LEFT_ELBOW),
        Pair(BodyPart.LEFT_ELBOW, BodyPart.LEFT_WRIST),
        Pair(BodyPart.RIGHT_SHOULDER, BodyPart.RIGHT_ELBOW),
        Pair(BodyPart.RIGHT_ELBOW, BodyPart.RIGHT_WRIST),
        Pair(BodyPart.LEFT_SHOULDER, BodyPart.RIGHT_SHOULDER),
        Pair(BodyPart.LEFT_SHOULDER, BodyPart.LEFT_HIP),
        Pair(BodyPart.RIGHT_SHOULDER, BodyPart.RIGHT_HIP),
        Pair(BodyPart.LEFT_HIP, BodyPart.RIGHT_HIP),
        Pair(BodyPart.LEFT_HIP, BodyPart.LEFT_KNEE),
        Pair(BodyPart.LEFT_KNEE, BodyPart.LEFT_ANKLE),
        Pair(BodyPart.RIGHT_HIP, BodyPart.RIGHT_KNEE),
        Pair(BodyPart.RIGHT_KNEE, BodyPart.RIGHT_ANKLE)
    )

    // Draw line and point indicate body pose
    @ExperimentalStdlibApi
    fun drawBodyKeypoints(
        input: Bitmap,
        persons: List<Person>,
        pose: String,
        context: Context
    ): Bitmap {
        val json = readJSONFromAssets(context, "angles.json")

        val data = Gson().fromJson(json, YogaPose::class.java)

        val camelPose = pose.replace(" ","").decapitalize()
        // Use reflection to access the pose dynamically
        val poseObject = data::class.members
                .firstOrNull { it.name ==  camelPose}
                ?.call(data)

        Log.i("poseObject", poseObject.toString())

//        val x = poseObject?::class.members
//            .firstOrNull { it.name ==  "left_shoulder"}
//            ?.call(poseObject)



        val paintCircle = Paint().apply {
            strokeWidth = CIRCLE_RADIUS
            color = Color.YELLOW
            style = Paint.Style.FILL
        }
        val paintLine = Paint().apply {
            strokeWidth = LINE_WIDTH
            color = Color.GRAY
            style = Paint.Style.STROKE
        }
        val paintAngle = Paint().apply {
            textSize = ANGLE_TEXT_SIZE
            color = Color.YELLOW
            textAlign = Paint.Align.LEFT
        }
        val paintRightAngle = Paint().apply {
            strokeWidth = ANGLE_CIRCLE_RADIUS
            color = Color.GREEN
            style = Paint.Style.FILL
        }
        val paintErrorAngle = Paint().apply {
            strokeWidth = ANGLE_CIRCLE_RADIUS
            color = Color.RED
            style = Paint.Style.FILL
        }
        val paintError = Paint().apply {
            textSize = ERROR_TEXT_SIZE
            color = Color.BLACK
            textAlign = Paint.Align.LEFT
            isFakeBoldText = true
        }

        val output = input.copy(Bitmap.Config.ARGB_8888, true)
        val originalSizeCanvas = Canvas(output)

        persons.forEach { person ->
            bodyJoints.forEach {
                val pointA = person.keyPoints[it.first.position].coordinate
                val pointB = person.keyPoints[it.second.position].coordinate
                originalSizeCanvas.drawLine(
                        pointA.x, pointA.y,
                        pointB.x, pointB.y,
                        paintLine
                )
            }

            person.keyPoints.forEach { point ->
                originalSizeCanvas.drawCircle(
                    point.coordinate.x,
                    point.coordinate.y,
                    CIRCLE_RADIUS,
                    paintCircle
                )
            }

            var error = 0F
            var maxError = 0F
            var maxErrorAngleJoint = ""
            var maxErrorAngle = 0F
            var neededAngle = 0


            bodyAngles.forEach {
                val pointA = person.keyPoints[it.first.position].coordinate
                val pointB = person.keyPoints[it.second.position].coordinate
                val pointC = person.keyPoints[it.third.position].coordinate

                val lineA = euclideanDistance(pointA.x, pointA.y, pointB.x, pointB.y)
                val lineB = euclideanDistance(pointB.x, pointB.y, pointC.x, pointC.y)
                val lineC = euclideanDistance(pointC.x, pointC.y, pointA.x, pointA.y)

                val angle = cosineLaw(lineA, lineB, lineC)

                val jointAngle = BodyPart.fromInt(it.second.position).toString().lowercase()

                val angleValue = poseObject?.let { x ->
                    // Access the property value dynamically using reflection
                    val propertyValue = x as? YogaPose.BodyAngle
                    val angleValue = propertyValue?.let { bodyAngle ->
                        // Use reflection to get the value of the specified joint angle
                        val propertyField = YogaPose.BodyAngle::class.java.getDeclaredField(jointAngle)
                        propertyField.isAccessible = true
                        val value = propertyField.get(bodyAngle)
                        value
                    }
                    angleValue
                }
//                Log.i("angleValue", angleValue.toString())



                if (angleValue != null) {

                        val threshold = (angleValue.toString().toInt() * 15) / 100

                        val lowerBound = angleValue.toString().toInt() - threshold
                        val upperBound = angleValue.toString().toInt() + threshold
                        if (lowerBound <= angle && angle <= upperBound) {
                            originalSizeCanvas.drawCircle(
                                pointB.x,
                                pointB.y,
                                ANGLE_CIRCLE_RADIUS,
                                paintRightAngle
                            )
                        }   else {
                            val angleError = abs(angleValue.toString().toInt() - angle) / angleValue.toString().toInt()

                            error += angleError


                            if (angleError > maxError) {
                                maxError = angleError
                                maxErrorAngle = angle
                                maxErrorAngleJoint = jointAngle.replace("_", " ").capitalize()
                                neededAngle = angleValue.toString().toInt()
                            }

                            originalSizeCanvas.drawCircle(
                                pointB.x,
                                pointB.y,
                                ANGLE_CIRCLE_RADIUS,
                                paintErrorAngle
                            )
                        }
                    } else {
                        originalSizeCanvas.drawText(
                                String.format("%.2f", angle),
                            pointB.x,
                            pointB.y,
                            paintAngle
                        )
                    }

            }
            error = (error / 8) * 100

            val roundedErrorValue = String.format("%.2f", error).toFloat()
            val roundedMaxErrorValue = String.format("%.2f", maxErrorAngle).toFloat()

            var textLines = if (error != 0F) {
                listOf("Pose: $pose", "Error: $roundedErrorValue%", "Max error in joint: $maxErrorAngleJoint", "Error angle: $roundedMaxErrorValue", "Needed angle: $neededAngle")

            } else {
                listOf("Pose: $pose", "Error: $roundedErrorValue%")

            }


            if (poseObject != null) {
                var yError = 30F
                textLines.forEach { text ->
                    originalSizeCanvas.drawText(
                            text,
                            10F,
                            yError,
                            paintError
                    )

                    // Increment y-coordinate for the next row
                    yError += paintError.textSize + 5 // Adjust lineSpacing as needed
                }
            }
            
            Log.i("error", "$error $maxErrorAngleJoint $maxError")
        }
        return output
    }

    private val bodyAngles = listOf(
            Triple(BodyPart.LEFT_SHOULDER, BodyPart.LEFT_ELBOW, BodyPart.LEFT_WRIST),
            Triple(BodyPart.RIGHT_SHOULDER, BodyPart.RIGHT_ELBOW, BodyPart.RIGHT_WRIST),
            Triple(BodyPart.LEFT_HIP, BodyPart.LEFT_SHOULDER, BodyPart.LEFT_ELBOW),
            Triple(BodyPart.RIGHT_HIP, BodyPart.RIGHT_SHOULDER, BodyPart.RIGHT_ELBOW),
            Triple(BodyPart.LEFT_SHOULDER, BodyPart.LEFT_HIP, BodyPart.LEFT_KNEE),
            Triple(BodyPart.RIGHT_SHOULDER, BodyPart.RIGHT_HIP, BodyPart.RIGHT_KNEE),
            Triple(BodyPart.LEFT_HIP, BodyPart.LEFT_KNEE, BodyPart.LEFT_ANKLE),
            Triple(BodyPart.RIGHT_HIP, BodyPart.RIGHT_KNEE, BodyPart.RIGHT_ANKLE)
    )

    fun calculateAngle(persons: List<Person>) {

        persons.forEach { person ->
            bodyAngles.forEach {
                val pointA = person.keyPoints[it.first.position].coordinate
                val pointB = person.keyPoints[it.second.position].coordinate
                val pointC = person.keyPoints[it.third.position].coordinate

                val lineA = euclideanDistance(pointA.x, pointA.y, pointB.x, pointB.y)
                val lineB = euclideanDistance(pointB.x, pointB.y, pointC.x, pointC.y)
                val lineC = euclideanDistance(pointC.x, pointC.y, pointA.x, pointA.y)

                val angle = cosineLaw(lineA, lineB, lineC)

            }
        }

    }

    private fun euclideanDistance(x1: Float, y1: Float, x2: Float, y2: Float): Float {
        val deltaX = x2 - x1
        val deltaY = y2 - y1

        return sqrt((deltaX * deltaX) + (deltaY * deltaY))
    }

    private fun cosineLaw(lineA: Float, lineB: Float, lineC: Float): Float {
        val numerator = (lineA * lineA) + (lineB * lineB) - (lineC * lineC)
        val denominator = 2 * lineA * lineB

        val angleRadian = acos(numerator / denominator)
        val angleDegree = Math.toDegrees(angleRadian.toDouble())
//        val formattedAngle = String.format("%.2f", angleDegree)

        return angleDegree.toFloat()
    }

    private fun readJSONFromAssets(context: Context, path: String): String {
        val identifier = "[ReadJSON]"
        try {
            val file = context.assets.open("$path")

            val bufferedReader = BufferedReader(InputStreamReader(file))
            val stringBuilder = StringBuilder()
            bufferedReader.useLines { lines ->
                lines.forEach {
                    stringBuilder.append(it)
                }
            }

            val jsonString = stringBuilder.toString()
//            Log.i("JSONString", "JSON as String: $jsonString.", )
            return jsonString
        } catch (e: Exception) {
            Log.e("JSONError", "Error reading JSON: $e.", )
            e.printStackTrace()
            return ""
        }
    }
}

