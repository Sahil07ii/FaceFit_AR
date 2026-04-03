package com.example.facefitar.viewmodel

import android.os.SystemClock
import androidx.lifecycle.ViewModel
import com.example.facefitar.camera.FaceOverlayState
import com.example.facefitar.camera.FilterType
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import com.google.mlkit.vision.face.Face

class CameraViewModel : ViewModel() {

    private val _selectedFilter = MutableStateFlow(FilterType.NONE)
    val selectedFilter: StateFlow<FilterType> = _selectedFilter.asStateFlow()

    private val _faceState = MutableStateFlow(FaceOverlayState())
    val faceState: StateFlow<FaceOverlayState> = _faceState.asStateFlow()

    /** Caps UI updates (~30 fps) so Compose is not flooded with ML Kit callbacks every frame. */
    private var lastFaceEmitMs = 0L

    fun setFilter(filterType: FilterType) {
        _selectedFilter.value = filterType
    }

    fun onFacesDetected(faces: List<Face>, width: Int, height: Int) {
        val now = SystemClock.elapsedRealtime()
        if (now - lastFaceEmitMs < 33L) return
        lastFaceEmitMs = now
        _faceState.value = FaceOverlayState(faces, width, height)
    }
}
