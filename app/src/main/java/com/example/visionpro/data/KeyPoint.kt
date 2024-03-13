package com.example.visionpro.data

import android.graphics.PointF

data class KeyPoint(val bodyPart: BodyPart, var coordinate: PointF, val score: Float, val widthRatio: Float, val heightRatio: Float)
