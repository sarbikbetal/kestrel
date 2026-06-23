package com.sarbikbetal.kestrel.ui.sky

import android.graphics.Paint
import android.graphics.Typeface
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.gestures.detectTapGestures
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
import androidx.compose.ui.graphics.nativeCanvas
import androidx.compose.ui.graphics.toArgb
import androidx.compose.ui.input.pointer.pointerInput
import com.sarbikbetal.kestrel.domain.PlaneSize
import com.sarbikbetal.kestrel.domain.SkyMath
import com.sarbikbetal.kestrel.sensors.DeviceOrientation
import com.sarbikbetal.kestrel.settings.Formatters
import com.sarbikbetal.kestrel.settings.UserPrefs
import com.sarbikbetal.kestrel.ui.TrackedPlane
import com.sarbikbetal.kestrel.ui.theme.HudCyan
import kotlin.math.atan2
import kotlin.math.hypot
import kotlin.math.max
import kotlin.math.min

private fun formatDistKm(meters: Double): String {
    val km = meters / 1_000.0
    return if (km < 100.0) "%.1f km".format(km) else "${km.toInt()} km"
}

private const val H_FOV = 60.0
private const val V_FOV = 50.0

private const val NEAR_M = 4_000.0
private const val FAR_M = 70_000.0
private const val MIN_SCALE = 1.1f
private const val MAX_SCALE = 2.8f

private val PlaneStroke = Color(0xFF0B1622)
private val TagBg = Color(0xE00D1B2A)
private val TitleColor = Color(0xFFFFFFFF)
private val SubColor = Color(0xFFB9C6D2)
private val HintColor = Color(0xFFFFC857)

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
    val hitTargets = remember { mutableListOf<PlaneHit>() }
    val titlePaint = remember {
        Paint().apply {
            isAntiAlias = true
            color = TitleColor.toArgb()
            textSize = 30f
            typeface = Typeface.MONOSPACE
        }
    }
    val subPaint = remember {
        Paint().apply {
            isAntiAlias = true
            color = SubColor.toArgb()
            textSize = 24f
            typeface = Typeface.MONOSPACE
        }
    }
    val distPaint = remember {
        Paint().apply {
            isAntiAlias = true
            color = Color(0xFF6A7F8E).toArgb()
            textSize = 21f
            typeface = Typeface.MONOSPACE
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

                if (tracked.aircraft.hex == selectedHex) {
                    drawCircle(HudCyan, radius = 28f * scale, center = pos, style = Stroke(2f))
                }

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
                    "  " + Formatters.speed(tracked.aircraft.groundSpeedKnots, prefs.speedUnit)
                val dist = formatDistKm(tracked.sky.slantDistanceMeters)
                val tag = drawTag(pos, title, sub, dist, color, titlePaint, subPaint, distPaint)
                hitTargets.add(
                    PlaneHit(
                        hex = tracked.aircraft.hex,
                        iconPos = pos,
                        iconRadius = (22f * scale).coerceAtLeast(40f),
                        tag = tag,
                    )
                )
            } else if (hintsDrawn < 6) {
                drawOffScreenHint(center, proj.bearingFromCenterDeg, min(w, h))
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

private fun distanceScale(slantMeters: Double): Float {
    val t = ((slantMeters - NEAR_M) / (FAR_M - NEAR_M)).coerceIn(0.0, 1.0)
    return (MAX_SCALE + (MIN_SCALE - MAX_SCALE) * t).toFloat()
}

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

/**
 * Draws a sci-fi tag beside [iconPos] and returns its bounding [TagRect] for
 * hit-testing. Uses a translucent dark background + corner bracket ticks instead
 * of a solid border to preserve the HUD aesthetic.
 */
private fun DrawScope.drawTag(
    iconPos: Offset,
    title: String,
    sub: String,
    dist: String,
    accent: Color,
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

    // Background
    drawRoundRect(
        color = TagBg,
        topLeft = Offset(left, top),
        size = Size(boxW, boxH),
        cornerRadius = CornerRadius(10f, 10f),
    )

    // Sci-fi corner ticks instead of a full border
    val tick = 10f
    val lw = 1.5f
    val tl = Offset(left, top)
    val tr = Offset(left + boxW, top)
    val bl = Offset(left, top + boxH)
    val br = Offset(left + boxW, top + boxH)
    val ac = accent.copy(alpha = 0.8f)
    // top-left
    drawLine(ac, tl, Offset(tl.x + tick, tl.y), lw)
    drawLine(ac, tl, Offset(tl.x, tl.y + tick), lw)
    // top-right
    drawLine(ac, tr, Offset(tr.x - tick, tr.y), lw)
    drawLine(ac, tr, Offset(tr.x, tr.y + tick), lw)
    // bottom-left
    drawLine(ac, bl, Offset(bl.x + tick, bl.y), lw)
    drawLine(ac, bl, Offset(bl.x, bl.y - tick), lw)
    // bottom-right
    drawLine(ac, br, Offset(br.x - tick, br.y), lw)
    drawLine(ac, br, Offset(br.x, br.y - tick), lw)

    // Left accent bar
    drawLine(accent.copy(alpha = 0.5f), Offset(left, top + 4f), Offset(left, top + boxH - 4f), 2f)

    drawContext.canvas.nativeCanvas.let { canvas ->
        val textX = left + padX
        canvas.drawText(title, textX, top + padY + titleH - 4f, titlePaint)
        canvas.drawText(sub,   textX, top + padY + titleH + gap + subH - 2f, subPaint)
        canvas.drawText(dist,  textX, top + padY + titleH + gap + subH + gap + distH - 2f, distPaint)
    }

    return TagRect(left, top, left + boxW, top + boxH)
}

private fun DrawScope.drawOffScreenHint(center: Offset, bearingDeg: Float, span: Float) {
    val radius = span * 0.42f
    rotate(degrees = bearingDeg, pivot = center) {
        val tip = Offset(center.x, center.y - radius)
        val lp = Offset(center.x - 14f, center.y - radius + 26f)
        val rp = Offset(center.x + 14f, center.y - radius + 26f)
        val path = Path().apply {
            moveTo(tip.x, tip.y)
            lineTo(lp.x, lp.y)
            lineTo(rp.x, rp.y)
            close()
        }
        drawPath(path, HintColor.copy(alpha = 0.85f))
    }
}
