package com.example.facefitar.camera

import android.content.ContentValues
import android.content.Context
import android.graphics.Bitmap
import android.graphics.Canvas
import android.graphics.Color
import android.graphics.Paint
import android.graphics.Path
import android.graphics.RectF
import android.os.Environment
import android.provider.MediaStore
import android.widget.Toast
import com.google.mlkit.vision.face.Face
import com.google.mlkit.vision.face.FaceLandmark
import java.io.OutputStream
import kotlin.math.atan2
import kotlin.math.cos
import kotlin.math.max
import kotlin.math.hypot
import kotlin.math.min
import kotlin.math.sin
import kotlin.random.Random

fun saveBitmapWithFilter(
    context: Context,
    originalBitmap: Bitmap,
    faces: List<Face>,
    filterType: FilterType,
    imageWidth: Int,
    imageHeight: Int
): Boolean {
    // Create a mutable copy of the bitmap to draw on
    val resultBitmap = originalBitmap.copy(Bitmap.Config.ARGB_8888, true)
    val canvas = Canvas(resultBitmap)
    val paint = Paint().apply {
        isAntiAlias = true
        style = Paint.Style.FILL
    }

    val bitmapW = originalBitmap.width.toFloat()
    val bitmapH = originalBitmap.height.toFloat()
    val imgW = imageWidth.toFloat()
    val imgH = imageHeight.toFloat()

    // preview overlay uses PreviewView.FIL_CENTER, so we must do the same mapping for capture.
    val scale = max(bitmapW / imgW, bitmapH / imgH)
    val scaleX = scale
    val scaleY = scale
    val offsetX = (bitmapW - imgW * scale) / 2f
    val offsetY = (bitmapH - imgH * scale) / 2f

    // We mirror the bitmap in CameraPreviewScreen, so landmark X must be mirrored too.
    fun mirrorX(xInImage: Float): Float = offsetX + (imgW - xInImage) * scaleX
    fun mapY(yInImage: Float): Float = offsetY + yInImage * scaleY

    faces.forEach { face ->
        when (filterType) {
            FilterType.ROSE_CROWN -> {
                val leftEye = face.getLandmark(FaceLandmark.LEFT_EYE)
                val rightEye = face.getLandmark(FaceLandmark.RIGHT_EYE)
                if (leftEye != null && rightEye != null) {
                    val lx = mirrorX(leftEye.position.x)
                    val rx = mirrorX(rightEye.position.x)
                    val ly = mapY(leftEye.position.y)
                    val ry = mapY(rightEye.position.y)
                    val eyeDist = hypot((rx - lx).toDouble(), (ry - ly).toDouble()).toFloat().coerceAtLeast(1f)
                    val centerX = (lx + rx) / 2f
                    val foreheadY = min(ly, ry) - eyeDist * 0.7f
                    val crownRadius = eyeDist * 0.9f

                    paint.style = Paint.Style.STROKE
                    paint.strokeCap = Paint.Cap.ROUND
                    paint.strokeWidth = eyeDist * 0.1f
                    paint.color = Color.parseColor("#2E7D32")
                    val bandRect = RectF(
                        centerX - crownRadius,
                        foreheadY - crownRadius * 0.25f,
                        centerX + crownRadius,
                        foreheadY + crownRadius * 0.5f
                    )
                    canvas.drawArc(bandRect, 185f, 170f, false, paint)

                    paint.style = Paint.Style.FILL
                    for (i in -3..3) {
                        val t = i / 3f
                        val x = centerX + t * crownRadius * 0.9f
                        val arch = 1f - t * t
                        val y = foreheadY - arch * crownRadius * 0.12f
                        val flowerRadius = if (i == 0) eyeDist * 0.2f else eyeDist * 0.14f
                        paint.color = Color.parseColor("#E91E63")
                        canvas.drawCircle(x, y, flowerRadius, paint)
                        paint.color = Color.parseColor("#FFF176")
                        canvas.drawCircle(x, y, flowerRadius * 0.45f, paint)
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
                    val eyeDist = hypot((rx - lx).toDouble(), (ry - ly).toDouble()).toFloat().coerceAtLeast(1f)

                    // Negative sign so ear tip points upward (not downward).
                    val angleRad = -atan2(ry - ly, rx - lx)
                    val cosA = cos(angleRad)
                    val sinA = sin(angleRad)
                    fun rotX(dx: Float, dy: Float): Float = dx * cosA - dy * sinA
                    fun rotY(dx: Float, dy: Float): Float = dx * sinA + dy * cosA

                    val earWidth = eyeDist * 0.52f
                    val earHeight = eyeDist * 0.98f
                    val innerWidth = earWidth * 0.4f
                    val innerHeight = earHeight * 0.56f

                    fun drawEar(anchorX: Float, anchorY: Float) {
                        val baseY = earHeight * 0.28f
                        val tipY = -earHeight * 0.72f

                        val pBaseLx = rotX(-earWidth / 2f, baseY)
                        val pBaseLy = rotY(-earWidth / 2f, baseY)
                        val pCtrlLx = rotX(-earWidth * 0.8f, -earHeight * 0.25f)
                        val pCtrlLy = rotY(-earWidth * 0.8f, -earHeight * 0.25f)
                        val pTipX = rotX(0f, tipY)
                        val pTipY = rotY(0f, tipY)
                        val pCtrlRx = rotX(earWidth * 0.8f, -earHeight * 0.25f)
                        val pCtrlRy = rotY(earWidth * 0.8f, -earHeight * 0.25f)
                        val pBaseRx = rotX(earWidth / 2f, baseY)
                        val pBaseRy = rotY(earWidth / 2f, baseY)

                        val earPath = Path().apply {
                            moveTo(anchorX + pBaseLx, anchorY + pBaseLy)
                            quadTo(
                                anchorX + pCtrlLx, anchorY + pCtrlLy,
                                anchorX + pTipX, anchorY + pTipY
                            )
                            quadTo(
                                anchorX + pCtrlRx, anchorY + pCtrlRy,
                                anchorX + pBaseRx, anchorY + pBaseRy
                            )
                            close()
                        }

                        paint.color = Color.parseColor("#6D4C41")
                        paint.style = Paint.Style.FILL
                        canvas.drawPath(earPath, paint)

                        // Inner ear.
                        paint.color = Color.parseColor("#F8BBD0")
                        canvas.drawOval(
                            RectF(
                                anchorX - innerWidth / 2f,
                                anchorY - innerHeight * 0.5f,
                                anchorX + innerWidth / 2f,
                                anchorY + innerHeight * 0.5f
                            ),
                            paint
                        )
                    }

                    val fallbackY = min(ly, ry) - earHeight * 1.1f
                    val fallbackXLeft = (lx + rx) / 2f - eyeDist * 0.55f
                    val fallbackXRight = (lx + rx) / 2f + eyeDist * 0.55f

                    val earAnchorYOffset = earHeight * 0f
                    val leftAnchorX = if (leftEar != null) mirrorX(leftEar.position.x) else fallbackXLeft
                    val leftAnchorY = if (leftEar != null) mapY(leftEar.position.y) - earAnchorYOffset else fallbackY - earAnchorYOffset
                    val rightAnchorX = if (rightEar != null) mirrorX(rightEar.position.x) else fallbackXRight
                    val rightAnchorY = if (rightEar != null) mapY(rightEar.position.y) - earAnchorYOffset else fallbackY - earAnchorYOffset

                    drawEar(leftAnchorX, leftAnchorY)
                    drawEar(rightAnchorX, rightAnchorY)

                    // Snout (match CameraPreviewScreen)
                    if (noseBase != null) {
                        val noseX = mirrorX(noseBase.position.x)
                        val noseY = mapY(noseBase.position.y)
                        val snoutW = eyeDist * 0.25f
                        val snoutH = eyeDist * 0.16f
                        paint.style = Paint.Style.FILL
                        paint.color = Color.parseColor("#4E342E")
                        paint.alpha = 255
                        canvas.drawOval(
                            RectF(
                                noseX - snoutW / 2f,
                                noseY - snoutH / 2f,
                                noseX + snoutW / 2f,
                                noseY + snoutH / 2f
                            ),
                            paint
                        )
                        paint.color = Color.WHITE
                        paint.alpha = 150
                        canvas.drawOval(
                            RectF(
                                noseX - snoutW * 0.18f,
                                noseY - snoutH * 0.25f,
                                noseX + snoutW * 0.18f,
                                noseY + snoutH * 0.03f
                            ),
                            paint
                        )
                        paint.alpha = 255
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
                    val frameWidth = eyeDist * 0.82f
                    val frameHeight = eyeDist * 0.53f
                    val eyeOffsetY = frameHeight * 0.05f
                    val cornerRadius = (frameHeight * 0.22f).coerceAtLeast(8f)

                    // Prepare paint
                    paint.style = Paint.Style.FILL
                    paint.strokeCap = Paint.Cap.ROUND

                    canvas.save()
                    canvas.rotate(angle, centerX, centerY)

                    val leftLensRect = RectF(
                        centerX - eyeDist / 2f - frameWidth / 2f,
                        centerY - frameHeight / 2f - eyeOffsetY,
                        centerX - eyeDist / 2f + frameWidth / 2f,
                        centerY + frameHeight / 2f - eyeOffsetY
                    )
                    val rightLensRect = RectF(
                        centerX + eyeDist / 2f - frameWidth / 2f,
                        centerY - frameHeight / 2f - eyeOffsetY,
                        centerX + eyeDist / 2f + frameWidth / 2f,
                        centerY + frameHeight / 2f - eyeOffsetY
                    )

                    // Lenses gradient
                    val shader = android.graphics.LinearGradient(
                        leftLensRect.left,
                        leftLensRect.top,
                        rightLensRect.right,
                        rightLensRect.bottom,
                        intArrayOf(
                            Color.parseColor("#1A237E"),
                            Color.parseColor("#64B5F6"),
                            Color.parseColor("#1A237E")
                        ),
                        floatArrayOf(0f, 0.5f, 1f),
                        android.graphics.Shader.TileMode.CLAMP
                    )
                    paint.shader = shader
                    canvas.drawRoundRect(leftLensRect, cornerRadius, cornerRadius, paint)
                    canvas.drawRoundRect(rightLensRect, cornerRadius, cornerRadius, paint)

                    // Outline
                    paint.shader = null
                    paint.style = Paint.Style.STROKE
                    paint.strokeWidth = eyeDist * 0.05f
                    paint.color = Color.BLACK
                    canvas.drawRoundRect(leftLensRect, cornerRadius, cornerRadius, paint)
                    canvas.drawRoundRect(rightLensRect, cornerRadius, cornerRadius, paint)

                    // Bridge
                    paint.strokeWidth = eyeDist * 0.03f
                    val bridgeY = centerY - frameHeight * 0.15f
                    canvas.drawLine(
                        centerX - eyeDist / 2f + frameWidth / 2f,
                        bridgeY,
                        centerX + eyeDist / 2f - frameWidth / 2f,
                        bridgeY,
                        paint
                    )

                    // Temples
                    val armOffsetY = frameHeight * 0.05f
                    canvas.drawLine(
                        leftLensRect.left,
                        leftLensRect.centerY() + armOffsetY,
                        leftLensRect.left - frameWidth * 0.25f,
                        leftLensRect.centerY() + armOffsetY + frameHeight * 0.1f,
                        paint
                    )
                    canvas.drawLine(
                        rightLensRect.right,
                        rightLensRect.centerY() + armOffsetY,
                        rightLensRect.right + frameWidth * 0.25f,
                        rightLensRect.centerY() + armOffsetY + frameHeight * 0.1f,
                        paint
                    )

                    // Top highlights
                    paint.style = Paint.Style.STROKE
                    paint.color = Color.WHITE
                    paint.alpha = 150
                    paint.strokeWidth = eyeDist * 0.015f
                    canvas.drawLine(
                        leftLensRect.left + frameWidth * 0.15f,
                        leftLensRect.top + frameHeight * 0.2f,
                        leftLensRect.right - frameWidth * 0.15f,
                        leftLensRect.top + frameHeight * 0.2f,
                        paint
                    )
                    canvas.drawLine(
                        rightLensRect.left + frameWidth * 0.15f,
                        rightLensRect.top + frameHeight * 0.2f,
                        rightLensRect.right - frameWidth * 0.15f,
                        rightLensRect.top + frameHeight * 0.2f,
                        paint
                    )

                    canvas.restore()
                    paint.alpha = 255
                }
            }
            FilterType.MASK -> {
                val noseBase = face.getLandmark(FaceLandmark.NOSE_BASE)
                val mouthBottom = face.getLandmark(FaceLandmark.MOUTH_BOTTOM)
                val leftCheek = face.getLandmark(FaceLandmark.LEFT_CHEEK)
                val rightCheek = face.getLandmark(FaceLandmark.RIGHT_CHEEK)
                val leftEar = face.getLandmark(FaceLandmark.LEFT_EAR)
                val rightEar = face.getLandmark(FaceLandmark.RIGHT_EAR)
                if (noseBase != null && mouthBottom != null && leftCheek != null && rightCheek != null) {
                    val nx = mirrorX(noseBase.position.x)
                    val ny = mapY(noseBase.position.y)
                    val myRaw = mapY(mouthBottom.position.y)
                    val lcx = mirrorX(leftCheek.position.x)
                    val rcx = mirrorX(rightCheek.position.x)
                    val maskWidth = kotlin.math.abs(rcx - lcx).coerceAtLeast(1f)

                    val leftEye = face.getLandmark(FaceLandmark.LEFT_EYE)
                    val rightEye = face.getLandmark(FaceLandmark.RIGHT_EYE)
                    val eyeDist = if (leftEye != null && rightEye != null) {
                        val exL = mirrorX(leftEye.position.x)
                        val exR = mirrorX(rightEye.position.x)
                        val eyL = mapY(leftEye.position.y)
                        val eyR = mapY(rightEye.position.y)
                        hypot((exR - exL).toDouble(), (eyR - eyL).toDouble()).toFloat().coerceAtLeast(1f)
                    } else {
                        maskWidth * 0.35f
                    }

                    // Surgical mask geometry (more realistic)
                    val maskTopY = ny - eyeDist * 0.22f
                    val maskBottomY = myRaw + eyeDist * 0.42f
                    val topInsetX = eyeDist * 0.18f
                    val bottomInsetX = eyeDist * 0.1f

                    val topLeftX = lcx + topInsetX
                    val topRightX = rcx - topInsetX
                    val bottomLeftX = lcx + bottomInsetX
                    val bottomRightX = rcx - bottomInsetX

                    // Straps behind the mask.
                    val strapStroke = max(eyeDist * 0.03f, 3f)
                    paint.style = Paint.Style.STROKE
                    paint.strokeCap = Paint.Cap.ROUND
                    paint.strokeWidth = strapStroke
                    paint.color = Color.parseColor("#90A4AE")
                    paint.alpha = 220

                    if (leftEar != null) {
                        val sx = mirrorX(leftEar.position.x)
                        val sy = mapY(leftEar.position.y) - eyeDist * 0.04f
                        val attachX = bottomLeftX + eyeDist * 0.06f
                        val attachY = maskTopY + (maskBottomY - maskTopY) * 0.55f
                        val midX = (sx + attachX) / 2f - eyeDist * 0.08f
                        val midY = (sy + attachY) / 2f - eyeDist * 0.18f
                        val strapPath = Path().apply {
                            moveTo(sx, sy)
                            quadTo(midX, midY, attachX, attachY)
                        }
                        canvas.drawPath(strapPath, paint)
                    }
                    if (rightEar != null) {
                        val sx = mirrorX(rightEar.position.x)
                        val sy = mapY(rightEar.position.y) - eyeDist * 0.04f
                        val attachX = bottomRightX - eyeDist * 0.06f
                        val attachY = maskTopY + (maskBottomY - maskTopY) * 0.55f
                        val midX = (sx + attachX) / 2f + eyeDist * 0.08f
                        val midY = (sy + attachY) / 2f - eyeDist * 0.18f
                        val strapPath = Path().apply {
                            moveTo(sx, sy)
                            quadTo(midX, midY, attachX, attachY)
                        }
                        canvas.drawPath(strapPath, paint)
                    }

                    val maskPath = Path().apply {
                        moveTo(bottomLeftX, maskBottomY)
                        quadTo(topLeftX, maskTopY, nx, maskTopY - eyeDist * 0.05f)
                        quadTo(topRightX, maskTopY, bottomRightX, maskBottomY)
                        quadTo(nx, maskBottomY + eyeDist * 0.02f, bottomLeftX, maskBottomY)
                        close()
                    }

                    // Mask fill.
                    paint.style = Paint.Style.FILL
                    paint.alpha = 255
                    paint.strokeWidth = 1f
                    val maskCenterX = (lcx + rcx) / 2f
                    paint.shader = android.graphics.LinearGradient(
                        maskCenterX,
                        maskTopY,
                        maskCenterX,
                        maskBottomY,
                        intArrayOf(
                            Color.parseColor("#EAF8FF"),
                            Color.parseColor("#BFE9FF"),
                            Color.parseColor("#81D4FA")
                        ),
                        floatArrayOf(0f, 0.5f, 1f),
                        android.graphics.Shader.TileMode.CLAMP
                    )
                    canvas.drawPath(maskPath, paint)
                    paint.shader = null

                    // Outline.
                    paint.style = Paint.Style.STROKE
                    paint.alpha = 120
                    paint.color = Color.parseColor("#4FC3F7")
                    paint.strokeWidth = max(eyeDist * 0.012f, 2f)
                    paint.strokeCap = Paint.Cap.ROUND
                    canvas.drawPath(maskPath, paint)

                    // Vertical pleats.
                    paint.alpha = 130
                    paint.color = Color.parseColor("#64B5F6")
                    paint.strokeWidth = max(eyeDist * 0.007f, 1.5f)
                    val pleatTop = maskTopY + eyeDist * 0.06f
                    val pleatBottom = maskBottomY - eyeDist * 0.08f
                    val pleatInset = eyeDist * 0.04f
                    for (i in 0..4) {
                        val t = i / 4f
                        val x = (topLeftX + pleatInset) + (topRightX - pleatInset - (topLeftX + pleatInset)) * t
                        val dx = (t - 0.5f) * eyeDist * 0.03f
                        canvas.drawLine(x, pleatTop, x + dx, pleatBottom, paint)
                    }

                    // Nose bridge highlight.
                    paint.alpha = 100
                    paint.color = Color.WHITE
                    paint.strokeWidth = max(eyeDist * 0.02f, 2f)
                    val highlightY = maskTopY + eyeDist * 0.25f
                    canvas.drawLine(nx - eyeDist * 0.08f, highlightY, nx + eyeDist * 0.08f, highlightY, paint)
                }
                paint.alpha = 255
            }
            FilterType.DECORATIVE -> {
                paint.color = Color.YELLOW
                val random = Random(42)
                for (i in 1..20) {
                    val xInImage = face.boundingBox.left + random.nextFloat() * face.boundingBox.width()
                    val yInImage = face.boundingBox.top + random.nextFloat() * face.boundingBox.height()
                    val x = mirrorX(xInImage.toFloat())
                    val y = mapY(yInImage.toFloat())
                    canvas.drawCircle(x, y, 10f * scaleX, paint)
                }
            }
            FilterType.NONE -> {}
        }
    }

    return saveBitmapToGallery(context, resultBitmap)
}

fun saveBitmapToGallery(context: Context, bitmap: Bitmap): Boolean {
    val filename = "FaceFit_AR_${System.currentTimeMillis()}.jpg"
    var fos: OutputStream? = null
    var imageUri: android.net.Uri? = null
    
    try {
        val resolver = context.contentResolver
        val contentValues = ContentValues().apply {
            put(MediaStore.MediaColumns.DISPLAY_NAME, filename)
            put(MediaStore.MediaColumns.MIME_TYPE, "image/jpeg")
            put(MediaStore.MediaColumns.RELATIVE_PATH, Environment.DIRECTORY_PICTURES + "/FaceFitAR")
        }

        imageUri = resolver.insert(MediaStore.Images.Media.EXTERNAL_CONTENT_URI, contentValues)
        
        if (imageUri != null) {
            fos = resolver.openOutputStream(imageUri)
        }

        fos?.use {
            bitmap.compress(Bitmap.CompressFormat.JPEG, 100, it)
            Toast.makeText(context, "Saved to Gallery", Toast.LENGTH_SHORT).show()
            return true
        }
        return false
    } catch (e: Exception) {
        e.printStackTrace()
        Toast.makeText(context, "Failed to save image", Toast.LENGTH_SHORT).show()
        if (imageUri != null) {
            context.contentResolver.delete(imageUri, null, null)
        }
        return false
    } finally {
        if (!bitmap.isRecycled) {
            bitmap.recycle()
        }
    }
}
