package com.adsbdroid.ui.sky

import android.graphics.Paint
import android.graphics.Typeface
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.drawscope.DrawScope
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.drawscope.rotate
import androidx.compose.ui.graphics.nativeCanvas
import androidx.compose.ui.graphics.toArgb
import com.adsbdroid.domain.PlaneSize
import com.adsbdroid.domain.SkyMath
import com.adsbdroid.sensors.DeviceOrientation
import com.adsbdroid.settings.Formatters
import com.adsbdroid.settings.UserPrefs
import com.adsbdroid.ui.TrackedPlane
import kotlin.math.atan2
import kotlin.math.hypot
import kotlin.math.max
import kotlin.math.min

private const val H_FOV = 60.0
private const val V_FOV = 50.0

// Distance-based icon scaling: nearer planes render larger.
private const val NEAR_M = 4_000.0
private const val FAR_M = 70_000.0
private const val MIN_SCALE = 1.1f
private const val MAX_SCALE = 2.8f

private val PlaneStroke = Color(0xFF0B1622)
private val TagBg = Color(0xE60B1622)
private val TitleColor = Color(0xFFFFFFFF)
private val SubColor = Color(0xFFB9C6D2)
private val HintColor = Color(0xFFFFC857)

private class PlaneHit(val hex: String, val pos: Offset, val radius: Float)

@Composable
fun SkyOverlay(
    planes: List<TrackedPlane>,
    orientation: DeviceOrientation,
    prefs: UserPrefs,
    selectedHex: String?,
    onSelect: (String?) -> Unit,
    modifier: Modifier = Modifier,
) {
    // Populated during draw, read by the tap handler for hit testing.
    val hitTargets = remember { mutableListOf<PlaneHit>() }
    val titlePaint = remember {
        Paint().apply {
            isAntiAlias = true
            color = TitleColor.toArgb()
            textSize = 32f
            typeface = Typeface.create(Typeface.DEFAULT, Typeface.BOLD)
        }
    }
    val subPaint = remember {
        Paint().apply {
            isAntiAlias = true
            color = SubColor.toArgb()
            textSize = 26f
        }
    }

    Canvas(
        modifier = modifier.pointerInput(Unit) {
            detectTapGestures { tap ->
                val hit = hitTargets.minByOrNull { (it.pos - tap).getDistance() }
                if (hit != null && (hit.pos - tap).getDistance() <= hit.radius) {
                    onSelect(hit.hex)
                } else {
                    onSelect(null)
                }
            }
        },
    ) {
        val w = size.width
        val h = size.height
        val center = Offset(w / 2f, h / 2f)

        drawReticle(center)
        hitTargets.clear()

        var hintsDrawn = 0
        planes.take(60).forEach { tracked ->
            val proj = SkyMath.project(
                planeAzimuthDeg = tracked.sky.azimuthDeg,
                planeElevationDeg = tracked.sky.elevationDeg,
                deviceAzimuthDeg = orientation.azimuthDeg.toDouble(),
                deviceElevationDeg = orientation.elevationDeg.toDouble(),
                deviceRollDeg = orientation.rollDeg.toDouble(),
                widthPx = w,
                heightPx = h,
                hFovDeg = H_FOV,
                vFovDeg = V_FOV,
            )

            if (proj.onScreen) {
                val pos = Offset(proj.x, proj.y)
                val color = Color(PlaneSize.classify(tracked.aircraft.category).colorArgb)
                val scale = distanceScale(tracked.sky.slantDistanceMeters)
                hitTargets.add(PlaneHit(tracked.aircraft.hex, pos, (22f * scale).coerceAtLeast(40f)))

                if (tracked.aircraft.hex == selectedHex) {
                    drawCircle(Color.White, radius = 26f * scale, center = pos, style = Stroke(3f))
                }
                // Orient the icon along its actual on-screen motion: project a point
                // slightly ahead along the heading and aim the nose at it. This
                // accounts for FOV scaling, elevation, and device roll automatically.
                val ahead = tracked.skyAhead
                val screenRot = ahead?.let {
                    val pa = SkyMath.project(
                        planeAzimuthDeg = it.azimuthDeg,
                        planeElevationDeg = it.elevationDeg,
                        deviceAzimuthDeg = orientation.azimuthDeg.toDouble(),
                        deviceElevationDeg = orientation.elevationDeg.toDouble(),
                        deviceRollDeg = orientation.rollDeg.toDouble(),
                        widthPx = w,
                        heightPx = h,
                        hFovDeg = H_FOV,
                        vFovDeg = V_FOV,
                    )
                    val dx = pa.x - proj.x
                    val dy = pa.y - proj.y
                    if (hypot(dx, dy) > 0.5f) {
                        Math.toDegrees(atan2(dx.toDouble(), -dy.toDouble())).toFloat()
                    } else null
                }
                if (screenRot != null) {
                    drawPlaneIcon(pos, screenRot, color, scale)
                } else {
                    drawCircle(color, radius = 9f * scale, center = pos)
                }

                val flag = tracked.country?.flag?.let { "$it " } ?: ""
                val title = "$flag${tracked.aircraft.callsign}"
                val sub = Formatters.altitude(tracked.aircraft.altitudeMeters, prefs.altitudeUnit) +
                    "  \u00B7  " + Formatters.speed(tracked.aircraft.groundSpeedKnots, prefs.speedUnit)
                drawTag(pos, title, sub, color, titlePaint, subPaint)
            } else if (hintsDrawn < 6) {
                drawOffScreenHint(center, proj.bearingFromCenterDeg, min(w, h))
                hintsDrawn++
            }
        }
    }
}

private fun DrawScope.drawReticle(center: Offset) {
    val r = 26f
    drawCircle(Color.White.copy(alpha = 0.45f), radius = r, center = center, style = Stroke(2f))
    drawLine(Color.White.copy(alpha = 0.35f), Offset(center.x - r - 14f, center.y), Offset(center.x - r, center.y), 2f)
    drawLine(Color.White.copy(alpha = 0.35f), Offset(center.x + r, center.y), Offset(center.x + r + 14f, center.y), 2f)
}

/** Maps slant distance to an icon scale: nearer is bigger, clamped to [MIN_SCALE, MAX_SCALE]. */
private fun distanceScale(slantMeters: Double): Float {
    val t = ((slantMeters - NEAR_M) / (FAR_M - NEAR_M)).coerceIn(0.0, 1.0)
    return (MAX_SCALE + (MIN_SCALE - MAX_SCALE) * t).toFloat()
}

/** A small top-down jet silhouette, rotated so the nose points along [rotationDeg]. */
private fun DrawScope.drawPlaneIcon(pos: Offset, rotationDeg: Float, color: Color, scale: Float = 1.1f) {
    val pts = listOf(
        0f to -16f, 2f to -6f, 2f to 0f, 15f to 8f, 15f to 11f, 2f to 7f, 2f to 12f,
        7f to 16f, 7f to 18f, 0f to 15f, -7f to 18f, -7f to 16f, -2f to 12f, -2f to 7f,
        -15f to 11f, -15f to 8f, -2f to 0f, -2f to -6f,
    )
    val path = Path().apply {
        pts.forEachIndexed { i, (px, py) ->
            val x = pos.x + px * scale
            val y = pos.y + py * scale
            if (i == 0) moveTo(x, y) else lineTo(x, y)
        }
        close()
    }
    rotate(degrees = rotationDeg, pivot = pos) {
        drawPath(path, color)
        drawPath(path, PlaneStroke, style = Stroke(2f))
    }
}

private fun DrawScope.drawTag(
    iconPos: Offset,
    title: String,
    sub: String,
    accent: Color,
    titlePaint: Paint,
    subPaint: Paint,
) {
    val padX = 16f
    val padY = 12f
    val gap = 8f
    val titleH = titlePaint.textSize
    val subH = subPaint.textSize
    val textW = max(titlePaint.measureText(title), subPaint.measureText(sub))
    val boxW = textW + padX * 2
    val boxH = titleH + subH + gap + padY * 2

    val left = iconPos.x + 22f
    val top = iconPos.y - boxH / 2f

    drawRoundRect(
        color = TagBg,
        topLeft = Offset(left, top),
        size = androidx.compose.ui.geometry.Size(boxW, boxH),
        cornerRadius = androidx.compose.ui.geometry.CornerRadius(14f, 14f),
    )
    drawRoundRect(
        color = accent.copy(alpha = 0.6f),
        topLeft = Offset(left, top),
        size = androidx.compose.ui.geometry.Size(boxW, boxH),
        cornerRadius = androidx.compose.ui.geometry.CornerRadius(14f, 14f),
        style = Stroke(1.5f),
    )

    drawIntoText { canvas ->
        val textX = left + padX
        canvas.drawText(title, textX, top + padY + titleH - 4f, titlePaint)
        canvas.drawText(sub, textX, top + padY + titleH + gap + subH - 2f, subPaint)
    }
}

private fun DrawScope.drawOffScreenHint(center: Offset, bearingDeg: Float, span: Float) {
    val radius = span * 0.42f
    rotate(degrees = bearingDeg, pivot = center) {
        val tip = Offset(center.x, center.y - radius)
        val left = Offset(center.x - 14f, center.y - radius + 26f)
        val right = Offset(center.x + 14f, center.y - radius + 26f)
        val path = Path().apply {
            moveTo(tip.x, tip.y)
            lineTo(left.x, left.y)
            lineTo(right.x, right.y)
            close()
        }
        drawPath(path, HintColor.copy(alpha = 0.85f))
    }
}

private inline fun DrawScope.drawIntoText(block: (android.graphics.Canvas) -> Unit) {
    drawContext.canvas.nativeCanvas.let(block)
}
