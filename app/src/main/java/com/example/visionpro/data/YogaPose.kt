package com.example.visionpro.data

data class YogaPose(
        val bow: BodyAngle,
        val bridge: BodyAngle,
        val camel: BodyAngle,
        val cat: BodyAngle,
        val cow: BodyAngle,
        val crow: BodyAngle,
        val extendedHandToToe: BodyAngle,
        val extendedSideAngle: BodyAngle,
        val halfMoon: BodyAngle,
        val lowLunge: BodyAngle,
        val plank: BodyAngle,
        val shoulderStand: BodyAngle,
        val sidePlank: BodyAngle,
        val sphinx: BodyAngle,
        val tree: BodyAngle,
        val upwardFacingDog: BodyAngle,
        val warriorOne: BodyAngle,
        val warriorThree: BodyAngle,
        val warriorTwo: BodyAngle,
        val wheel: BodyAngle
) {
    data class BodyAngle(
            val left_shoulder: Int,
            val right_shoulder: Int,
            val left_elbow: Int,
            val right_elbow: Int,
            val left_hip: Int,
            val right_hip: Int,
            val left_knee: Int,
            val right_knee: Int
    )

}

