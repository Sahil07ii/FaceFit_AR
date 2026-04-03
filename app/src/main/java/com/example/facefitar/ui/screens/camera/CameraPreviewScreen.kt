package com.example.facefitar.ui.screens.camera

import android.Manifest
import android.graphics.Bitmap
import android.util.Size
import android.view.ViewGroup
import androidx.camera.core.CameraSelector
import androidx.camera.core.ImageAnalysis
import androidx.camera.core.Preview
import androidx.camera.core.resolutionselector.ResolutionSelector
import androidx.camera.core.resolutionselector.ResolutionStrategy
import androidx.camera.lifecycle.ProcessCameraProvider
import androidx.camera.view.PreviewView
import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.Spring
import androidx.compose.animation.core.spring
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.AutoAwesome
import androidx.compose.material.icons.filled.CameraAlt
import androidx.compose.material.icons.filled.EmojiEmotions
import androidx.compose.material.icons.filled.LocalFlorist
import androidx.compose.material.icons.filled.Masks
import androidx.compose.material.icons.filled.Pets
import androidx.compose.material.icons.filled.Person
import androidx.compose.material.icons.filled.RemoveRedEye
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.drawscope.withTransform
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalLifecycleOwner
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.viewinterop.AndroidView
import androidx.core.content.ContextCompat
import androidx.lifecycle.viewmodel.compose.viewModel
import com.example.facefitar.camera.FaceAnalyzer
import com.example.facefitar.camera.FilterType
import com.example.facefitar.camera.saveBitmapWithFilter
import com.example.facefitar.viewmodel.CameraViewModel
import com.google.accompanist.permissions.ExperimentalPermissionsApi
import com.google.accompanist.permissions.isGranted
import com.google.accompanist.permissions.rememberPermissionState
import com.google.mlkit.vision.face.FaceLandmark
import java.util.concurrent.Executors
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import kotlin.math.*

@OptIn(ExperimentalPermissionsApi::class)
@Composable
fun CameraPreviewScreen(
    onNavigateToProfile: () -> Unit,
    viewModel: CameraViewModel = viewModel()
) {
    val cameraPermissionState = rememberPermissionState(Manifest.permission.CAMERA)
    
    LaunchedEffect(Unit) {
        if (!cameraPermissionState.status.isGranted) {
            cameraPermissionState.launchPermissionRequest()
        }
    }

    if (cameraPermissionState.status.isGranted) {
        CameraPreviewContent(viewModel, onNavigateToProfile)
    } else {
        Box(modifier = Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
            Column(horizontalAlignment = Alignment.CenterHorizontally) {
                Text("Camera permission is required to use filters.")
                Spacer(modifier = Modifier.height(16.dp))
                Button(onClick = { cameraPermissionState.launchPermissionRequest() }) {
                    Text("Request Permission")
                }
            }
        }
    }
}

@Composable
fun CameraPreviewContent(
    viewModel: CameraViewModel,
    onNavigateToProfile: () -> Unit
) {
    val context = LocalContext.current
    val lifecycleOwner = LocalLifecycleOwner.current
    val uiScope = rememberCoroutineScope()
    val faceState by viewModel.faceState.collectAsState()
    val selectedFilter by viewModel.selectedFilter.collectAsState()
    var isSaving by remember { mutableStateOf(false) }
    var showSavedSuccess by remember { mutableStateOf(false) }

    val filterAlphaAnim = remember { Animatable(1f) }
    var isFirstFilterFrame by remember { mutableStateOf(true) }
    LaunchedEffect(selectedFilter) {
        if (isFirstFilterFrame) {
            isFirstFilterFrame = false
            filterAlphaAnim.snapTo(1f)
        } else {
            filterAlphaAnim.snapTo(0f)
            filterAlphaAnim.animateTo(
                targetValue = 1f,
                animationSpec = spring(
                    dampingRatio = Spring.DampingRatioNoBouncy,
                    stiffness = Spring.StiffnessMediumLow
                )
            )
        }
    }
    val filterAlpha = filterAlphaAnim.value

    var previewView by remember { mutableStateOf<PreviewView?>(null) }

    DisposableEffect(lifecycleOwner, previewView) {
        val pv = previewView ?: return@DisposableEffect onDispose { }
        val cameraExecutor = Executors.newSingleThreadExecutor()
        val future = ProcessCameraProvider.getInstance(context)
        val mainExecutor = ContextCompat.getMainExecutor(context)
        val analysisResolution = ResolutionSelector.Builder()
            .setResolutionStrategy(
                ResolutionStrategy(
                    Size(640, 480),
                    ResolutionStrategy.FALLBACK_RULE_CLOSEST_HIGHER_THEN_LOWER
                )
            )
            .build()
        val bindRunnable = Runnable {
            val cameraProvider = future.get()
            val preview = Preview.Builder().build().also {
                it.setSurfaceProvider(pv.surfaceProvider)
            }
            val imageAnalysis = ImageAnalysis.Builder()
                .setBackpressureStrategy(ImageAnalysis.STRATEGY_KEEP_ONLY_LATEST)
                .setOutputImageFormat(ImageAnalysis.OUTPUT_IMAGE_FORMAT_YUV_420_888)
                .setResolutionSelector(analysisResolution)
                .build()
                .also {
                    it.setAnalyzer(cameraExecutor, FaceAnalyzer { faces, w, h ->
                        viewModel.onFacesDetected(faces, w, h)
                    })
                }
            try {
                cameraProvider.unbindAll()
                cameraProvider.bindToLifecycle(
                    lifecycleOwner,
                    CameraSelector.DEFAULT_FRONT_CAMERA,
                    preview,
                    imageAnalysis
                )
            } catch (e: Exception) {
                e.printStackTrace()
            }
        }
        future.addListener(bindRunnable, mainExecutor)
        onDispose {
            future.addListener({
                try {
                    future.get().unbindAll()
                } catch (_: Exception) {
                }
                cameraExecutor.shutdown()
            }, mainExecutor)
        }
    }

    Box(modifier = Modifier.fillMaxSize().background(Color.Black)) {
        AndroidView(
            modifier = Modifier.fillMaxSize(),
            factory = { ctx ->
                PreviewView(ctx).apply {
                    layoutParams = ViewGroup.LayoutParams(
                        ViewGroup.LayoutParams.MATCH_PARENT,
                        ViewGroup.LayoutParams.MATCH_PARENT
                    )
                    scaleType = PreviewView.ScaleType.FILL_CENTER
                    implementationMode = PreviewView.ImplementationMode.PERFORMANCE
                    previewView = this
                }
            },
            update = { }
        )

        // Draw AR face filters on top of the preview using Jetpack Compose Canvas (separate layer for GPU compositing)
        Canvas(
            modifier = Modifier
                .fillMaxSize()
                .graphicsLayer()
        ) {
            val canvasWidth = size.width
            val canvasHeight = size.height

            if (faceState.imageWidth > 0 && faceState.imageHeight > 0) {
                val imageW = faceState.imageWidth.toFloat()
                val imageH = faceState.imageHeight.toFloat()

                // PreviewView uses FILL_CENTER, so we must preserve aspect ratio and apply crop offsets.
                val scale = max(canvasWidth / imageW, canvasHeight / imageH)
                val scaleX = scale
                val scaleY = scale
                val offsetX = (canvasWidth - imageW * scale) / 2f
                val offsetY = (canvasHeight - imageH * scale) / 2f

                fun mirrorX(xInImage: Float): Float = offsetX + (imageW - xInImage) * scaleX
                fun mapY(yInImage: Float): Float = offsetY + yInImage * scaleY
                
                faceState.faces.forEach { face ->
                    val faceAlpha = filterAlpha
                    
                    when (selectedFilter) {
                        FilterType.ROSE_CROWN -> {
                            val leftEye = face.getLandmark(FaceLandmark.LEFT_EYE)
                            val rightEye = face.getLandmark(FaceLandmark.RIGHT_EYE)
                            if (leftEye != null && rightEye != null) {
                                val lx = mirrorX(leftEye.position.x)
                                val rx = mirrorX(rightEye.position.x)
                                val ly = mapY(leftEye.position.y)
                                val ry = mapY(rightEye.position.y)
                                val eyeDist = hypot(rx - lx, ry - ly).coerceAtLeast(1f)
                                val centerX = (lx + rx) / 2f
                                val foreheadY = min(ly, ry) - eyeDist * 0.7f
                                val crownRadius = eyeDist * 0.9f

                                drawArc(
                                    color = Color(0xFF2E7D32).copy(alpha = faceAlpha * 0.85f),
                                    startAngle = 185f,
                                    sweepAngle = 170f,
                                    useCenter = false,
                                    topLeft = Offset(centerX - crownRadius, foreheadY - crownRadius * 0.25f),
                                    size = androidx.compose.ui.geometry.Size(crownRadius * 2f, crownRadius * 0.75f),
                                    style = androidx.compose.ui.graphics.drawscope.Stroke(width = eyeDist * 0.1f, cap = androidx.compose.ui.graphics.StrokeCap.Round)
                                )

                                for (i in -3..3) {
                                    val t = i / 3f
                                    val x = centerX + t * crownRadius * 0.9f
                                    val arch = 1f - t * t
                                    val y = foreheadY - arch * crownRadius * 0.12f
                                    val flowerRadius = if (i == 0) eyeDist * 0.2f else eyeDist * 0.14f
                                    val flowerBrush = androidx.compose.ui.graphics.Brush.radialGradient(
                                        colors = listOf(
                                            Color(0xFFFFF176).copy(alpha = faceAlpha),
                                            Color(0xFFE91E63).copy(alpha = faceAlpha)
                                        ),
                                        center = Offset(x, y),
                                        radius = flowerRadius
                                    )
                                    drawCircle(brush = flowerBrush, radius = flowerRadius, center = Offset(x, y))
                                    drawCircle(
                                        color = Color(0xFFAD1457).copy(alpha = faceAlpha * 0.55f),
                                        radius = flowerRadius * 0.42f,
                                        center = Offset(x, y)
                                    )
                                }
                            }
                        }
                        FilterType.ANIMAL_EARS -> {
                            val leftEye = face.getLandmark(FaceLandmark.LEFT_EYE)
                            val rightEye = face.getLandmark(FaceLandmark.RIGHT_EYE)
                            val leftEar = face.getLandmark(FaceLandmark.LEFT_EAR)
                            val rightEar = face.getLandmark(FaceLandmark.RIGHT_EAR)
                            val noseBase = face.getLandmark(FaceLandmark.NOSE_BASE)

                            if (leftEye != null && rightEye != null) {
                                val lx = mirrorX(leftEye.position.x)
                                val rx = mirrorX(rightEye.position.x)
                                val ly = mapY(leftEye.position.y)
                                val ry = mapY(rightEye.position.y)
                                val eyeDist = hypot(rx - lx, ry - ly).coerceAtLeast(1f)

                                // Negative sign so ear tip points upward (not downward).
                                val angleRad = -atan2(ry - ly, rx - lx)
                                val cosA = cos(angleRad)
                                val sinA = sin(angleRad)

                                fun rotOff(dx: Float, dy: Float): Offset {
                                    return Offset(
                                        x = dx * cosA - dy * sinA,
                                        y = dx * sinA + dy * cosA
                                    )
                                }

                                val earWidth = eyeDist * 0.52f
                                val earHeight = eyeDist * 0.98f
                                val innerWidth = earWidth * 0.4f
                                val innerHeight = earHeight * 0.56f

                                fun drawEar(anchorX: Float, anchorY: Float) {
                                    // Build a local ear shape using rotated offsets, then translate to the ear landmark anchor.
                                    val baseY = earHeight * 0.28f
                                    val tipY = -earHeight * 0.72f

                                    val pBaseL = rotOff(-earWidth / 2f, baseY)
                                    val pCtrlL = rotOff(-earWidth * 0.8f, -earHeight * 0.25f)
                                    val pTip = rotOff(0f, tipY)
                                    val pCtrlR = rotOff(earWidth * 0.8f, -earHeight * 0.25f)
                                    val pBaseR = rotOff(earWidth / 2f, baseY)

                                    val earPath = androidx.compose.ui.graphics.Path().apply {
                                        moveTo(anchorX + pBaseL.x, anchorY + pBaseL.y)
                                        quadraticTo(
                                            anchorX + pCtrlL.x, anchorY + pCtrlL.y,
                                            anchorX + pTip.x, anchorY + pTip.y
                                        )
                                        quadraticTo(
                                            anchorX + pCtrlR.x, anchorY + pCtrlR.y,
                                            anchorX + pBaseR.x, anchorY + pBaseR.y
                                        )
                                        close()
                                    }

                                    val topY = anchorY + pTip.y
                                    val bottomY = anchorY + pBaseL.y

                                    drawPath(
                                        path = earPath,
                                        brush = androidx.compose.ui.graphics.Brush.linearGradient(
                                            colors = listOf(
                                                Color(0xFF6D4C41).copy(alpha = faceAlpha),
                                                Color(0xFF8D6E63).copy(alpha = faceAlpha)
                                            ),
                                            start = Offset(anchorX, topY),
                                            end = Offset(anchorX, bottomY)
                                        )
                                    )
                                    drawOval(
                                        color = Color(0xFFF8BBD0).copy(alpha = faceAlpha * 0.95f),
                                        topLeft = Offset(anchorX - innerWidth / 2f, anchorY - innerHeight * 0.5f),
                                        size = androidx.compose.ui.geometry.Size(innerWidth, innerHeight)
                                    )
                                }

                                // Anchor ears to ML Kit landmarks (more accurate than bounding-box math).
                                val fallbackY = min(ly, ry) - earHeight * 0.72f
                                val fallbackXLeft = (lx + rx) / 2f - eyeDist * 0.55f
                                val fallbackXRight = (lx + rx) / 2f + eyeDist * 0.55f

                                val earAnchorYOffset = earHeight * 0.14f
                                val leftAnchorX = if (leftEar != null) mirrorX(leftEar.position.x) else fallbackXLeft
                                val leftAnchorY = if (leftEar != null) mapY(leftEar.position.y) - earAnchorYOffset else fallbackY - earAnchorYOffset
                                val rightAnchorX = if (rightEar != null) mirrorX(rightEar.position.x) else fallbackXRight
                                val rightAnchorY = if (rightEar != null) mapY(rightEar.position.y) - earAnchorYOffset else fallbackY - earAnchorYOffset

                                drawEar(leftAnchorX, leftAnchorY)
                                drawEar(rightAnchorX, rightAnchorY)

                                // Snout (smaller + proportional, no extra scale multipliers)
                                if (noseBase != null) {
                                    val noseX = mirrorX(noseBase.position.x)
                                    val noseY = mapY(noseBase.position.y)
                                    val snoutW = eyeDist * 0.25f
                                    val snoutH = eyeDist * 0.16f
                                    drawOval(
                                        color = Color(0xFF4E342E).copy(alpha = faceAlpha),
                                        topLeft = Offset(noseX - snoutW / 2f, noseY - snoutH / 2f),
                                        size = androidx.compose.ui.geometry.Size(snoutW, snoutH)
                                    )
                                    drawOval(
                                        color = Color.White.copy(alpha = faceAlpha * 0.6f),
                                        topLeft = Offset(noseX - snoutW * 0.18f, noseY - snoutH * 0.25f),
                                        size = androidx.compose.ui.geometry.Size(snoutW * 0.36f, snoutH * 0.28f)
                                    )
                                }
                            }
                        }
                        FilterType.GLASSES -> {
                            val leftEye = face.getLandmark(FaceLandmark.LEFT_EYE)
                            val rightEye = face.getLandmark(FaceLandmark.RIGHT_EYE)
                            if (leftEye != null && rightEye != null) {
                                val lx = mirrorX(leftEye.position.x)
                                val rx = mirrorX(rightEye.position.x)
                                val ly = mapY(leftEye.position.y)
                                val ry = mapY(rightEye.position.y)
                                
                                val dx = rx - lx
                                val dy = ry - ly
                                val angle = Math.toDegrees(atan2(dy.toDouble(), dx.toDouble())).toFloat()
                                val eyeDist = hypot(dx.toDouble(), dy.toDouble()).toFloat().coerceAtLeast(1f)
                                
                                val centerX = (lx + rx) / 2f
                                val centerY = (ly + ry) / 2f

                                // Slightly smaller, more natural rectangular lenses
                                val frameWidth = eyeDist * 0.82f
                                val frameHeight = eyeDist * 0.53f
                                
                                withTransform({
                                    rotate(angle, Offset(centerX, centerY))
                                }) {
                                    // Lenses sit a bit above eye center
                                    val eyeOffsetY = frameHeight * 0.05f
                                    val leftLensTopLeft = Offset(centerX - eyeDist / 2f - frameWidth / 2f, centerY - frameHeight / 2f - eyeOffsetY)
                                    val rightLensTopLeft = Offset(centerX + eyeDist / 2f - frameWidth / 2f, centerY - frameHeight / 2f - eyeOffsetY)
                                    val lensSize = androidx.compose.ui.geometry.Size(frameWidth, frameHeight)
                                    val cornerRadius = (frameHeight * 0.22f).coerceAtLeast(8f * scaleX)
                                    val corner = androidx.compose.ui.geometry.CornerRadius(cornerRadius, cornerRadius)
                                    
                                    val glassBrush = androidx.compose.ui.graphics.Brush.linearGradient(
                                        colors = listOf(
                                            Color(0xFF1A237E).copy(alpha = faceAlpha * 0.85f),
                                            Color(0xFF64B5F6).copy(alpha = faceAlpha * 0.35f),
                                            Color(0xFF1A237E).copy(alpha = faceAlpha * 0.85f)
                                        ),
                                        start = Offset(leftLensTopLeft.x, leftLensTopLeft.y),
                                        end = Offset(rightLensTopLeft.x + frameWidth, rightLensTopLeft.y + frameHeight)
                                    )

                                    // Lenses
                                    drawRoundRect(
                                        brush = glassBrush,
                                        topLeft = leftLensTopLeft,
                                        size = lensSize,
                                        cornerRadius = corner
                                    )
                                    drawRoundRect(
                                        brush = glassBrush,
                                        topLeft = rightLensTopLeft,
                                        size = lensSize,
                                        cornerRadius = corner
                                    )

                                    // Outline
                                    val borderWidth = (eyeDist * 0.04f).coerceAtLeast(2f * scaleX)
                                    val borderStroke = androidx.compose.ui.graphics.drawscope.Stroke(width = borderWidth)
                                    drawRoundRect(
                                        color = Color.Black.copy(alpha = faceAlpha),
                                        topLeft = leftLensTopLeft,
                                        size = lensSize,
                                        cornerRadius = corner,
                                        style = borderStroke
                                    )
                                    drawRoundRect(
                                        color = Color.Black.copy(alpha = faceAlpha),
                                        topLeft = rightLensTopLeft,
                                        size = lensSize,
                                        cornerRadius = corner,
                                        style = borderStroke
                                    )
                                    
                                    // Bridge
                                    // Bridge (short and centered between lenses)
                                    drawLine(
                                        color = Color.Black.copy(alpha = faceAlpha),
                                        start = Offset(
                                            x = centerX - eyeDist / 2f + frameWidth / 2f,
                                            y = centerY - frameHeight * 0.15f
                                        ),
                                        end = Offset(
                                            x = centerX + eyeDist / 2f - frameWidth / 2f,
                                            y = centerY - frameHeight * 0.15f
                                        ),
                                        strokeWidth = borderWidth * 0.9f
                                    )

                                    // Temples (arms) extending slightly backwards
                                    val armOffsetY = frameHeight * 0.05f
                                    drawLine(
                                        color = Color.Black.copy(alpha = faceAlpha),
                                        start = Offset(
                                            x = leftLensTopLeft.x,
                                            y = leftLensTopLeft.y + frameHeight * 0.4f + armOffsetY
                                        ),
                                        end = Offset(
                                            x = leftLensTopLeft.x - frameWidth * 0.25f,
                                            y = leftLensTopLeft.y + frameHeight * 0.5f + armOffsetY
                                        ),
                                        strokeWidth = borderWidth * 0.9f
                                    )
                                    drawLine(
                                        color = Color.Black.copy(alpha = faceAlpha),
                                        start = Offset(
                                            x = rightLensTopLeft.x + frameWidth,
                                            y = rightLensTopLeft.y + frameHeight * 0.4f + armOffsetY
                                        ),
                                        end = Offset(
                                            x = rightLensTopLeft.x + frameWidth * 1.25f,
                                            y = rightLensTopLeft.y + frameHeight * 0.5f + armOffsetY
                                        ),
                                        strokeWidth = borderWidth * 0.9f
                                    )

                                    // Subtle top highlights
                                    drawLine(
                                        color = Color.White.copy(alpha = faceAlpha * 0.45f),
                                        start = Offset(leftLensTopLeft.x + 10f * scaleX, leftLensTopLeft.y + 8f * scaleY),
                                        end = Offset(leftLensTopLeft.x + frameWidth - 10f * scaleX, leftLensTopLeft.y + 8f * scaleY),
                                        strokeWidth = borderWidth * 0.55f
                                    )
                                    drawLine(
                                        color = Color.White.copy(alpha = faceAlpha * 0.45f),
                                        start = Offset(rightLensTopLeft.x + 10f * scaleX, rightLensTopLeft.y + 8f * scaleY),
                                        end = Offset(rightLensTopLeft.x + frameWidth - 10f * scaleX, rightLensTopLeft.y + 8f * scaleY),
                                        strokeWidth = borderWidth * 0.55f
                                    )
                                }
                            }
                        }
                        FilterType.MASK -> {
                            val noseBase = face.getLandmark(FaceLandmark.NOSE_BASE)
                            val mouthBottom = face.getLandmark(FaceLandmark.MOUTH_BOTTOM)
                            val leftCheek = face.getLandmark(FaceLandmark.LEFT_CHEEK)
                            val rightCheek = face.getLandmark(FaceLandmark.RIGHT_CHEEK)
                            val leftEar = face.getLandmark(FaceLandmark.LEFT_EAR)
                            val rightEar = face.getLandmark(FaceLandmark.RIGHT_EAR)
                            val leftEye = face.getLandmark(FaceLandmark.LEFT_EYE)
                            val rightEye = face.getLandmark(FaceLandmark.RIGHT_EYE)
                            
                            if(noseBase != null && mouthBottom != null && leftCheek != null && rightCheek != null) {
                                val nx = mirrorX(noseBase.position.x)
                                val ny = mapY(noseBase.position.y)
                                val mouthX = mirrorX(mouthBottom.position.x)
                                val myRaw = mapY(mouthBottom.position.y)

                                val lcx = mirrorX(leftCheek.position.x)
                                val rcx = mirrorX(rightCheek.position.x)
                                val maskWidth = abs(rcx - lcx).coerceAtLeast(1f)

                                val eyeDist = if (leftEye != null && rightEye != null) {
                                    val exL = mirrorX(leftEye.position.x)
                                    val exR = mirrorX(rightEye.position.x)
                                    val eyL = mapY(leftEye.position.y)
                                    val eyR = mapY(rightEye.position.y)
                                    hypot(exR - exL, eyR - eyL).coerceAtLeast(1f)
                                } else {
                                    maskWidth * 0.35f
                                }

                                // "Surgical mask" geometry
                                val maskTopY = ny - eyeDist * 0.22f
                                val maskBottomY = myRaw + eyeDist * 0.42f
                                val topInsetX = eyeDist * 0.18f
                                val bottomInsetX = eyeDist * 0.1f

                                val topLeftX = lcx + topInsetX
                                val topRightX = rcx - topInsetX
                                val bottomLeftX = lcx + bottomInsetX
                                val bottomRightX = rcx - bottomInsetX

                                // Straps behind the mask.
                                val strapStroke = (eyeDist * 0.03f).coerceAtLeast(3f)

                                if (leftEar != null) {
                                    val sx = mirrorX(leftEar.position.x)
                                    val sy = mapY(leftEar.position.y) - eyeDist * 0.04f
                                    val attachX = bottomLeftX + eyeDist * 0.06f
                                    val attachY = maskTopY + (maskBottomY - maskTopY) * 0.55f
                                    val midX = (sx + attachX) / 2f - eyeDist * 0.08f
                                    val midY = (sy + attachY) / 2f - eyeDist * 0.18f
                                    val strapPath = androidx.compose.ui.graphics.Path().apply {
                                        moveTo(sx, sy)
                                        quadraticTo(midX, midY, attachX, attachY)
                                    }
                                    drawPath(
                                        path = strapPath,
                                        color = Color(0xFF90A4AE).copy(alpha = faceAlpha * 0.85f),
                                        style = androidx.compose.ui.graphics.drawscope.Stroke(width = strapStroke, cap = androidx.compose.ui.graphics.StrokeCap.Round)
                                    )
                                }
                                if (rightEar != null) {
                                    val sx = mirrorX(rightEar.position.x)
                                    val sy = mapY(rightEar.position.y) - eyeDist * 0.04f
                                    val attachX = bottomRightX - eyeDist * 0.06f
                                    val attachY = maskTopY + (maskBottomY - maskTopY) * 0.55f
                                    val midX = (sx + attachX) / 2f + eyeDist * 0.08f
                                    val midY = (sy + attachY) / 2f - eyeDist * 0.18f
                                    val strapPath = androidx.compose.ui.graphics.Path().apply {
                                        moveTo(sx, sy)
                                        quadraticTo(midX, midY, attachX, attachY)
                                    }
                                    drawPath(
                                        path = strapPath,
                                        color = Color(0xFF90A4AE).copy(alpha = faceAlpha * 0.85f),
                                        style = androidx.compose.ui.graphics.drawscope.Stroke(width = strapStroke, cap = androidx.compose.ui.graphics.StrokeCap.Round)
                                    )
                                }

                                val maskPath = androidx.compose.ui.graphics.Path().apply {
                                    moveTo(bottomLeftX, maskBottomY)
                                    quadraticTo(topLeftX, maskTopY, nx, maskTopY - eyeDist * 0.05f)
                                    quadraticTo(topRightX, maskTopY, bottomRightX, maskBottomY)
                                    quadraticTo(nx, maskBottomY + eyeDist * 0.02f, bottomLeftX, maskBottomY)
                                    close()
                                }

                                val maskBrush = androidx.compose.ui.graphics.Brush.verticalGradient(
                                    colors = listOf(
                                        Color(0xFFEAF8FF).copy(alpha = faceAlpha * 0.98f),
                                        Color(0xFFBFE9FF).copy(alpha = faceAlpha * 0.98f),
                                        Color(0xFF81D4FA).copy(alpha = faceAlpha * 0.98f)
                                    ),
                                    startY = maskTopY,
                                    endY = maskBottomY
                                )
                                drawPath(path = maskPath, brush = maskBrush)

                                // Outline.
                                drawPath(
                                    path = maskPath,
                                    color = Color(0xFF4FC3F7).copy(alpha = faceAlpha * 0.35f),
                                    style = androidx.compose.ui.graphics.drawscope.Stroke(width = (eyeDist * 0.012f).coerceAtLeast(2f), cap = androidx.compose.ui.graphics.StrokeCap.Round)
                                )

                                // Vertical pleats.
                                val pleatAlpha = faceAlpha * 0.45f
                                val pleatStroke = (eyeDist * 0.007f).coerceAtLeast(1.5f)
                                val pleatLeft = topLeftX + eyeDist * 0.04f
                                val pleatRight = topRightX - eyeDist * 0.04f
                                val pleatTop = maskTopY + eyeDist * 0.06f
                                val pleatBottom = maskBottomY - eyeDist * 0.08f
                                for (i in 0..4) {
                                    val t = i / 4f
                                    val x = pleatLeft + (pleatRight - pleatLeft) * t
                                    val dx = (t - 0.5f) * eyeDist * 0.03f
                                    drawLine(
                                        color = Color(0xFF64B5F6).copy(alpha = pleatAlpha),
                                        start = Offset(x, pleatTop),
                                        end = Offset(x + dx, pleatBottom),
                                        strokeWidth = pleatStroke
                                    )
                                }

                                // Nose bridge shading highlight.
                                drawLine(
                                    color = Color.White.copy(alpha = faceAlpha * 0.35f),
                                    start = Offset(nx - eyeDist * 0.08f, maskTopY + eyeDist * 0.25f),
                                    end = Offset(nx + eyeDist * 0.08f, maskTopY + eyeDist * 0.25f),
                                    strokeWidth = (eyeDist * 0.02f).coerceAtLeast(2f)
                                )
                            }
                        }
                        FilterType.DECORATIVE -> {
                            val random = kotlin.random.Random(face.trackingId?.toLong() ?: 42L)
                            for (j in 1..15) {
                                val xInImage = face.boundingBox.left + random.nextFloat() * face.boundingBox.width() * 1.5f - face.boundingBox.width() * 0.25f
                                val yInImage = face.boundingBox.top + random.nextFloat() * face.boundingBox.height() * 1.5f - face.boundingBox.height() * 0.25f
                                val x = mirrorX(xInImage * 1f)
                                val y = offsetY + yInImage.toFloat() * scaleY
                                
                                val size = 10f * scaleX + random.nextFloat() * 15f * scaleX
                                
                                // Star path
                                val starPath = androidx.compose.ui.graphics.Path().apply {
                                    moveTo(x, y - size)
                                    quadraticTo(x, y, x + size, y)
                                    quadraticTo(x, y, x, y + size)
                                    quadraticTo(x, y, x - size, y)
                                    quadraticTo(x, y, x, y - size)
                                    close()
                                }
                                drawPath(
                                    path = starPath,
                                    color = Color.Yellow.copy(alpha = faceAlpha * 0.8f)
                                )
                                // Glow
                                drawCircle(
                                    brush = androidx.compose.ui.graphics.Brush.radialGradient(
                                        colors = listOf(Color.White.copy(alpha = faceAlpha * 0.8f), Color.Transparent),
                                        center = Offset(x, y),
                                        radius = size * 1.5f
                                    ),
                                    radius = size * 1.5f,
                                    center = Offset(x, y)
                                )
                            }
                        }
                        FilterType.NONE -> {}
                    }
                }
            }
        }
        
        // Profile Button
        IconButton(
            onClick = { onNavigateToProfile() },
            modifier = Modifier.align(Alignment.TopEnd).padding(16.dp).background(Color.Black.copy(alpha = 0.3f), CircleShape)
        ) {
            Icon(Icons.Default.Person, contentDescription = "Profile", tint = Color.White)
        }

        // Camera state feedback (as per UI states wireframe)
        if (selectedFilter != FilterType.NONE && faceState.faces.isEmpty()) {
            Surface(
                modifier = Modifier
                    .align(Alignment.TopCenter)
                    .padding(top = 18.dp),
                color = Color(0xFF1976D2).copy(alpha = 0.92f),
                shape = androidx.compose.foundation.shape.RoundedCornerShape(14.dp),
                shadowElevation = 6.dp
            ) {
                Text(
                    text = "No face detected - move into frame",
                    color = Color.White,
                    fontSize = 12.sp,
                    modifier = Modifier.padding(horizontal = 12.dp, vertical = 8.dp)
                )
            }
        }

        if (isSaving) {
            Surface(
                modifier = Modifier
                    .align(Alignment.TopCenter)
                    .padding(top = 54.dp),
                color = Color(0xFF7E57C2).copy(alpha = 0.92f),
                shape = androidx.compose.foundation.shape.RoundedCornerShape(14.dp),
                shadowElevation = 6.dp
            ) {
                Row(
                    modifier = Modifier.padding(horizontal = 12.dp, vertical = 8.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    CircularProgressIndicator(
                        modifier = Modifier.size(14.dp),
                        color = Color.White,
                        strokeWidth = 2.dp
                    )
                    Spacer(modifier = Modifier.width(8.dp))
                    Text("Saving...", color = Color.White, fontSize = 12.sp)
                }
            }
        } else if (showSavedSuccess) {
            Surface(
                modifier = Modifier
                    .align(Alignment.TopCenter)
                    .padding(top = 54.dp),
                color = Color(0xFF2E7D32).copy(alpha = 0.95f),
                shape = androidx.compose.foundation.shape.RoundedCornerShape(14.dp),
                shadowElevation = 6.dp
            ) {
                Text(
                    text = "Saved to gallery",
                    color = Color.White,
                    fontSize = 12.sp,
                    modifier = Modifier.padding(horizontal = 12.dp, vertical = 8.dp)
                )
            }
        }

        // Bottom Carousel and Controls
        Column(
            modifier = Modifier
                .align(Alignment.BottomCenter)
                .fillMaxWidth()
                .padding(bottom = 24.dp),
            horizontalAlignment = Alignment.CenterHorizontally
        ) {
            Surface(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 16.dp),
                color = Color.Black.copy(alpha = 0.35f),
                shape = androidx.compose.foundation.shape.RoundedCornerShape(28.dp),
                shadowElevation = 8.dp
            ) {
                LazyRow(
                    modifier = Modifier.padding(vertical = 16.dp),
                    horizontalArrangement = Arrangement.Center,
                    contentPadding = PaddingValues(horizontal = 16.dp)
                ) {
                    items(FilterType.entries) { filter ->
                        val isSelected = filter == selectedFilter
                        val circleSize = if (isSelected) 70.dp else 64.dp
                        val circleBg = if (isSelected) {
                            MaterialTheme.colorScheme.primary.copy(alpha = 0.95f)
                        } else {
                            Color.White.copy(alpha = 0.14f)
                        }
                        Column(
                            horizontalAlignment = Alignment.CenterHorizontally,
                            modifier = Modifier.padding(horizontal = 12.dp)
                        ) {
                            Box(
                                modifier = Modifier
                                    .size(circleSize)
                                    .clip(CircleShape)
                                    .background(circleBg)
                                    .clickable { viewModel.setFilter(filter) },
                                contentAlignment = Alignment.Center
                            ) {
                                val iconTint = Color.White
                                val iconSize = if (isSelected) 30.dp else 26.dp
                                when (filter) {
                                    FilterType.ROSE_CROWN -> Icon(
                                        imageVector = Icons.Default.LocalFlorist,
                                        contentDescription = "Rose Crown",
                                        tint = iconTint,
                                        modifier = Modifier.size(iconSize)
                                    )
                                    FilterType.ANIMAL_EARS -> Icon(
                                        imageVector = Icons.Default.Pets,
                                        contentDescription = "Animal Ears",
                                        tint = iconTint,
                                        modifier = Modifier.size(iconSize)
                                    )
                                    FilterType.GLASSES -> Icon(
                                        imageVector = Icons.Default.RemoveRedEye,
                                        contentDescription = "Cool Glasses",
                                        tint = iconTint,
                                        modifier = Modifier.size(iconSize)
                                    )
                                    FilterType.MASK -> Icon(
                                        imageVector = Icons.Default.Masks,
                                        contentDescription = "Safety Mask",
                                        tint = iconTint,
                                        modifier = Modifier.size(iconSize)
                                    )
                                    FilterType.DECORATIVE -> Icon(
                                        imageVector = Icons.Default.AutoAwesome,
                                        contentDescription = "Sparkles",
                                        tint = iconTint,
                                        modifier = Modifier.size(iconSize)
                                    )
                                    FilterType.NONE -> Icon(
                                        imageVector = Icons.Default.EmojiEmotions,
                                        contentDescription = "Normal",
                                        tint = iconTint,
                                        modifier = Modifier.size(iconSize)
                                    )
                                }
                            }
                            Spacer(modifier = Modifier.height(4.dp))
                            Text(
                                text = filter.displayName,
                                color = Color.White,
                                fontSize = 10.sp,
                                fontWeight = if (isSelected) FontWeight.Bold else FontWeight.Normal
                            )
                        }
                    }
                }
            }
            
            Spacer(modifier = Modifier.height(8.dp))

            // Capture Button
            Surface(
                modifier = Modifier
                    .size(80.dp)
                    .clickable {
                        if (isSaving) return@clickable
                        previewView?.bitmap?.let { bmp ->
                            isSaving = true
                            showSavedSuccess = false
                            val saved = saveBitmapWithFilter(
                                context = context,
                                originalBitmap = bmp,
                                faces = faceState.faces,
                                filterType = selectedFilter,
                                imageWidth = faceState.imageWidth,
                                imageHeight = faceState.imageHeight
                            )
                            isSaving = false
                            if (saved) {
                                showSavedSuccess = true
                                uiScope.launch {
                                    delay(1600)
                                    showSavedSuccess = false
                                }
                            }
                        }
                    },
                shape = CircleShape,
                color = Color.White,
                border = androidx.compose.foundation.BorderStroke(4.dp, Color.Gray.copy(alpha = 0.5f)),
                shadowElevation = 8.dp
            ) {
                Box(contentAlignment = Alignment.Center) {
                    Icon(
                        Icons.Default.CameraAlt,
                        contentDescription = "Capture",
                        modifier = Modifier.size(36.dp),
                        tint = Color.Black
                    )
                }
            }
        }
    }
}
