package com.example.facefitar.camera

import com.google.mlkit.vision.face.Face

data class FaceOverlayState(
    val faces: List<Face> = emptyList(),
    val imageWidth: Int = 0,
    val imageHeight: Int = 0
)
