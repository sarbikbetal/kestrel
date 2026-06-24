package com.sarbikbetal.kestrel.ui.sky

import android.graphics.Paint
import android.graphics.Typeface
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.drawscope.DrawScope
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.drawscope.rotate
import androidx.compose.ui.graphics.drawscope.translate
import androidx.compose.ui.graphics.nativeCanvas
import androidx.compose.ui.graphics.toArgb
import androidx.compose.ui.input.pointer.pointerInput
import com.sarbikbetal.kestrel.domain.PlaneSize
import com.sarbikbetal.kestrel.domain.SkyMath
import com.sarbikbetal.kestrel.sensors.DeviceOrientation
import com.sarbikbetal.kestrel.settings.Formatters
import com.sarbikbetal.kestrel.settings.UserPrefs
import com.sarbikbetal.kestrel.ui.TrackedPlane
import kotlin.math.atan2
import kotlin.math.hypot
import kotlin.math.max
import kotlin.math.min

// Plane silhouette centered at (0,0), scale 1.1f baked in — allocated once.
private val PLANE_PATH: Path = Path().apply {
    val s = 1.1f
    moveTo(  0f * s, -16f * s)
    lineTo(  2f * s,  -6f * s); lineTo(  2f * s,   0f * s)
    lineTo( 15f * s,   8f * s); lineTo( 15f * s,  11f * s)
    lineTo(  2f * s,   7f * s); lineTo(  2f * s,  12f * s)
    lineTo(  7f * s,  16f * s); lineTo(  7f * s,  18f * s)
    lineTo(  0f * s,  15f * s)
    lineTo( -7f * s,  18f * s); lineTo( -7f * s,  16f * s)
    lineTo( -2f * s,  12f * s); lineTo( -2f * s,   7f * s)
    lineTo(-15f * s,  11f * s); lineTo(-15f * s,   8f * s)
    lineTo( -2f * s,   0f * s); lineTo( -2f * s,  -6f * s)
    close()
}

// Off-screen hint arrow: tip at (0,0) pointing up, base 26 px below — allocated once.
private val HINT_ARROW_PATH: Path = Path().apply {
    moveTo(0f, 0f); lineTo(-14f, 26f); lineTo(14f, 26f); close()
}

private val ICON_STROKE = Stroke(2f)

private fun formatDistKm(meters: Double): String {
    val km = meters / 1_000.0
    return if (km < 100.0) "%.1f km".format(km) else "${km.toInt()} km"
}



/** Axis-aligned rect used for label tap tests. */
private data class TagRect(val l: Float, val t: Float, val r: Float, val b: Float) {
    fun contains(x: Float, y: Float) = x in l..r && y in t..b
}

/** Hit target covering both the plane icon and its label tag. */
private class PlaneHit(
    val hex: String,
    val iconPos: Offset,
    val iconRadius: Float,
    val tag: TagRect,
) {
    fun contains(tap: Offset) =
        (iconPos - tap).getDistance() <= iconRadius || tag.contains(tap.x, tap.y)
}

@Composable
fun SkyOverlay(
    planes: List<TrackedPlane>,
    orientation: DeviceOrientation,
    prefs: UserPrefs,
    selectedHex: String?,
    onSelect: (String?) -> Unit,
    modifier: Modifier = Modifier,
) {
    val colorScheme = MaterialTheme.colorScheme
    val planeStroke = colorScheme.surface
    val tagBg = colorScheme.surfaceContainerHigh.copy(alpha = 0.94f)
    val titleColor = colorScheme.onSurface
    val subColor = colorScheme.onSurfaceVariant
    val hintColor = colorScheme.tertiary
    val selectionRing = colorScheme.primary

    val hitTargets = remember { ArrayList<PlaneHit>(64) }
    val titlePaint = remember(titleColor) {
        Paint().apply {
            isAntiAlias = true
            color = titleColor.toArgb()
            textSize = 30f
            typeface = Typeface.DEFAULT_BOLD
        }
    }
    val subPaint = remember(subColor) {
        Paint().apply {
            isAntiAlias = true
            color = subColor.toArgb()
            textSize = 24f
            typeface = Typeface.DEFAULT
        }
    }
    val distPaint = remember(subColor) {
        Paint().apply {
            isAntiAlias = true
            color = subColor.copy(alpha = 0.75f).toArgb()
            textSize = 21f
            typeface = Typeface.DEFAULT
        }
    }

    Canvas(
        modifier = modifier.pointerInput(Unit) {
            detectTapGestures { tap ->
                // Planes are sorted nearest-first, so first hit is the closest plane.
                val hit = hitTargets.firstOrNull { it.contains(tap) }
                onSelect(hit?.hex)
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
                deviceAzimuthDeg = orientation.compassBearingDeg.toDouble(),
                deviceElevationDeg = orientation.elevationDeg.toDouble(),
                deviceRollDeg = orientation.rollDeg.toDouble(),
                widthPx = w,
                heightPx = h,
            )

            if (proj.onScreen) {
                val pos = Offset(proj.x, proj.y)
                val color = Color(PlaneSize.classify(tracked.aircraft.category).colorArgb)

                if (tracked.aircraft.hex == selectedHex) {
                    drawCircle(selectionRing, radius = 28f, center = pos, style = Stroke(3f))
                }

                val ahead = tracked.skyAhead
                val screenRot = ahead?.let {
                    val pa = SkyMath.project(
                        planeAzimuthDeg = it.azimuthDeg,
                        planeElevationDeg = it.elevationDeg,
                        deviceAzimuthDeg = orientation.compassBearingDeg.toDouble(),
                        deviceElevationDeg = orientation.elevationDeg.toDouble(),
                        deviceRollDeg = orientation.rollDeg.toDouble(),
                        widthPx = w,
                        heightPx = h,
                    )
                    val dx = pa.x - proj.x
                    val dy = pa.y - proj.y
                    if (hypot(dx, dy) > 0.5f) {
                        Math.toDegrees(atan2(dx.toDouble(), -dy.toDouble())).toFloat()
                    } else null
                }
                if (screenRot != null) {
                    drawPlaneIcon(pos, screenRot, color, stroke = planeStroke)
                } else {
                    drawCircle(color, radius = 9f, center = pos)
                }

                val flag = tracked.country?.flag?.let { "$it " } ?: ""
                val title = "$flag${tracked.aircraft.callsign}"
                val sub = Formatters.altitude(tracked.aircraft.altitudeMeters, prefs.altitudeUnit) +
                    "  " + Formatters.speed(tracked.aircraft.groundSpeedKnots, prefs.speedUnit)
                val dist = formatDistKm(tracked.sky.slantDistanceMeters)
                val tag = drawTag(pos, title, sub, dist, color, tagBg, titlePaint, subPaint, distPaint)
                hitTargets.add(
                    PlaneHit(
                        hex = tracked.aircraft.hex,
                        iconPos = pos,
                        iconRadius = 40f,
                        tag = tag,
                    )
                )
            } else if (hintsDrawn < 6) {
                drawOffScreenHint(center, proj.bearingFromCenterDeg, min(w, h), hintColor)
                hintsDrawn++
            }
        }
    }
}

private fun DrawScope.drawReticle(center: Offset) {
    val r = 26f
    drawCircle(Color.White.copy(alpha = 0.35f), radius = r, center = center, style = Stroke(1.5f))
    drawLine(Color.White.copy(alpha = 0.25f), Offset(center.x - r - 14f, center.y), Offset(center.x - r, center.y), 1.5f)
    drawLine(Color.White.copy(alpha = 0.25f), Offset(center.x + r, center.y), Offset(center.x + r + 14f, center.y), 1.5f)
}


// ponytail: PLANE_PATH is a module-level val; translate+rotate avoids per-frame Path allocs.
// pivot = Offset.Zero rotates around the translated origin (= pos on screen).
private fun DrawScope.drawPlaneIcon(pos: Offset, rotationDeg: Float, color: Color, stroke: Color) {
    translate(left = pos.x, top = pos.y) {
        rotate(degrees = rotationDeg, pivot = Offset.Zero) {
            drawPath(PLANE_PATH, color)
            drawPath(PLANE_PATH, stroke, style = ICON_STROKE)
        }
    }
}

private fun DrawScope.drawTag(
    iconPos: Offset,
    title: String,
    sub: String,
    dist: String,
    accent: Color,
    background: Color,
    titlePaint: Paint,
    subPaint: Paint,
    distPaint: Paint,
): TagRect {
    val padX = 14f
    val padY = 10f
    val gap = 5f
    val titleH = titlePaint.textSize
    val subH = subPaint.textSize
    val distH = distPaint.textSize
    val textW = maxOf(titlePaint.measureText(title), subPaint.measureText(sub), distPaint.measureText(dist))
    val boxW = textW + padX * 2
    val boxH = titleH + subH + distH + gap * 2 + padY * 2

    val left = iconPos.x + 24f
    val top = iconPos.y - boxH / 2f

    drawRoundRect(
        color = background,
        topLeft = Offset(left, top),
        size = Size(boxW, boxH),
        cornerRadius = CornerRadius(16f, 16f),
    )

    drawLine(
        accent.copy(alpha = 0.85f),
        Offset(left, top + 4f),
        Offset(left, top + boxH - 4f),
        3f,
    )

    drawContext.canvas.nativeCanvas.let { canvas ->
        val textX = left + padX
        canvas.drawText(title, textX, top + padY + titleH - 4f, titlePaint)
        canvas.drawText(sub, textX, top + padY + titleH + gap + subH - 2f, subPaint)
        canvas.drawText(dist, textX, top + padY + titleH + gap + subH + gap + distH - 2f, distPaint)
    }

    return TagRect(left, top, left + boxW, top + boxH)
}

// ponytail: HINT_ARROW_PATH is a module-level val; translate avoids per-frame Path+Offset allocs.
private fun DrawScope.drawOffScreenHint(center: Offset, bearingDeg: Float, span: Float, color: Color) {
    val radius = span * 0.42f
    rotate(degrees = bearingDeg, pivot = center) {
        translate(left = center.x, top = center.y - radius) {
            drawPath(HINT_ARROW_PATH, color.copy(alpha = 0.9f))
        }
    }
}
