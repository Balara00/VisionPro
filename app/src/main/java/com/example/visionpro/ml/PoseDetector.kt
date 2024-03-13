package com.example.visionpro.ml

import android.graphics.Bitmap
import com.example.visionpro.data.Person

interface PoseDetector: AutoCloseable {
    fun estimatePoses(bitmap: Bitmap): List<Person>

    fun lastInferenceTimeNanos(): Long
}