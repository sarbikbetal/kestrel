package com.sarbikbetal.kestrel.ui.sky

import android.graphics.Paint
import android.graphics.Typeface
import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.tween
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.State
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.snapshotFlow
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.drawscope.DrawScope
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.drawscope.rotate
import androidx.compose.ui.graphics.drawscope.scale
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

// Plane silhouette centered at (0,0), scale 1.1f baked in - allocated once.
private val PLANE_PATH: Path = Path().apply {
    val s = 2f
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

// Off-screen hint arrow: tip at (0,0) pointing up, base 26 px below - allocated once.
private val HINT_ARROW_PATH: Path = Path().apply {
    moveTo(0f, 0f); lineTo(-14f, 26f); lineTo(14f, 26f); close()
}

private val ICON_STROKE = Stroke(2f)
private const val CROSSHAIR_RADIUS_PX = 55f

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

/** Pre-formatted tag strings for a plane. Built off the draw path (data changes
 *  at ~10 Hz) so the 50 Hz canvas redraw never re-runs Formatters/String.format.
 *  Two short data lines keep the tag proportionate: distance, then altitude · speed. */
private class PlaneLabel(val title: String, val line1: String, val line2: String)

/** Render data for the crosshair-focused plane, deferred so its enlarged tag
 *  always paints on the top layer (after every other plane in the frame). */
private class DeferredFocus(
    val hex: String,
    val pos: Offset,
    val color: Color,
    val screenRot: Float?,
    val selected: Boolean,
    val label: PlaneLabel,
)

@Composable
fun SkyOverlay(
    planes: List<TrackedPlane>,
    orientation: State<DeviceOrientation>,
    prefs: UserPrefs,
    selectedHex: String?,
    onSelect: (String?) -> Unit,
    modifier: Modifier = Modifier,
    /** Called with the hex of the plane under the crosshair, or null when none. */
    onFocusChange: (String?) -> Unit = {},
) {
    val colorScheme = MaterialTheme.colorScheme
    val planeStroke = colorScheme.surface
    val tagBg = Color(0xFF0F131B).copy(alpha = 0.82f)
    val tagBorder = Color.White.copy(alpha = 0.16f)
    val titleColor = Color.White
    val subColor = Color.White.copy(alpha = 0.66f)
    val hintColor = colorScheme.tertiary
    val selectionRing = colorScheme.primary

    // Tracks the single plane closest to the crosshair (null = none within threshold).
    val nearHexState = remember { mutableStateOf<String?>(null) }
    val tagScale = remember { Animatable(1f) }
    val curOnFocusChange by rememberUpdatedState(onFocusChange)
    LaunchedEffect(Unit) {
        snapshotFlow { nearHexState.value }.collect { curOnFocusChange(it) }
    }
    LaunchedEffect(nearHexState.value) {
        val expanding = nearHexState.value != null
        tagScale.animateTo(
            targetValue = if (expanding) 1.45f else 1f,
            animationSpec = tween(durationMillis = if (expanding) 300 else 150),
        )
    }

    // Cap at 60 and pre-format labels off the draw path. `planes` changes at ~10 Hz,
    // so this recomputes far less often than the 50 Hz orientation-driven redraw.
    val visible = remember(planes) { planes.take(60) }
    val labels = remember(visible, prefs) {
        visible.map { tracked ->
            val flag = tracked.country?.flag?.let { "$it " } ?: ""
            val alt = Formatters.altitude(tracked.aircraft.altitudeMeters, prefs.altitudeUnit)
            val spd = Formatters.speed(tracked.aircraft.groundSpeedKnots, prefs.speedUnit)
            val dist = formatDistKm(tracked.sky.slantDistanceMeters)
            PlaneLabel(
                title = "$flag${tracked.aircraft.callsign}",
                line1 = "$alt · $spd",
                line2 = dist,
            )
        }
    }

    val hitTargets = remember { ArrayList<PlaneHit>(64) }
    val titlePaint = remember {
        Paint().apply {
            isAntiAlias = true
            color = titleColor.toArgb()
            textSize = 36f
            typeface = Typeface.DEFAULT_BOLD
            letterSpacing = 0.04f
        }
    }
    val subPaint = remember {
        Paint().apply {
            isAntiAlias = true
            color = subColor.toArgb()
            textSize = 27f
            typeface = Typeface.DEFAULT
            letterSpacing = 0.02f
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
        // Read orientation in the draw phase: phone motion repositions every plane
        // without recomposing the overlay.
        val o = orientation.value
        val w = size.width
        val h = size.height
        val center = Offset(w / 2f, h / 2f)

        drawReticle(center)
        hitTargets.clear()

        // Find the single closest on-screen plane to the crosshair this frame.
        var newNearHex: String? = null
        var newNearDist = CROSSHAIR_RADIUS_PX

        // The crosshair-focused plane is drawn after the loop so its enlarged tag
        // sits on top of every other plane instead of being overdrawn by later ones.
        var focused: DeferredFocus? = null

        var hintsDrawn = 0
        visible.forEachIndexed { i, tracked ->
            val proj = SkyMath.project(
                planeAzimuthDeg = tracked.sky.azimuthDeg,
                planeElevationDeg = tracked.sky.elevationDeg,
                deviceAzimuthDeg = o.compassBearingDeg.toDouble(),
                deviceElevationDeg = o.elevationDeg.toDouble(),
                deviceRollDeg = o.rollDeg.toDouble(),
                widthPx = w,
                heightPx = h,
            )

            if (proj.onScreen) {
                val pos = Offset(proj.x, proj.y)
                val color = Color(PlaneSize.classify(tracked.aircraft.category).colorArgb)

                val ahead = tracked.skyAhead
                val screenRot = ahead?.let {
                    val pa = SkyMath.project(
                        planeAzimuthDeg = it.azimuthDeg,
                        planeElevationDeg = it.elevationDeg,
                        deviceAzimuthDeg = o.compassBearingDeg.toDouble(),
                        deviceElevationDeg = o.elevationDeg.toDouble(),
                        deviceRollDeg = o.rollDeg.toDouble(),
                        widthPx = w,
                        heightPx = h,
                    )
                    val dx = pa.x - proj.x
                    val dy = pa.y - proj.y
                    if (hypot(dx, dy) > 0.5f) {
                        Math.toDegrees(atan2(dx.toDouble(), -dy.toDouble())).toFloat()
                    } else null
                }

                // Track closest plane to crosshair (single winner only).
                val d = (pos - center).getDistance()
                if (d < newNearDist) {
                    newNearDist = d
                    newNearHex = tracked.aircraft.hex
                }

                val selected = tracked.aircraft.hex == selectedHex
                if (tracked.aircraft.hex == nearHexState.value) {
                    focused = DeferredFocus(tracked.aircraft.hex, pos, color, screenRot, selected, labels[i])
                } else {
                    drawPlaneEntry(
                        tracked.aircraft.hex, pos, color, screenRot, selected,
                        planeStroke, labels[i], 1f,
                        tagBg, tagBorder, titlePaint, subPaint, hitTargets,
                    )
                }
            } else {
                val isSelected = tracked.aircraft.hex == selectedHex
                // Selected plane always gets a hint regardless of the cap.
                if (isSelected) {
                    drawOffScreenHint(
                        center = center,
                        bearingDeg = proj.bearingFromCenterDeg,
                        span = min(w, h),
                        color = selectionRing,
                        scale = 2.2f,
                        selected = true,
                    )
                } else if (hintsDrawn < 6) {
                    drawOffScreenHint(
                        center = center,
                        bearingDeg = proj.bearingFromCenterDeg,
                        span = min(w, h),
                        color = hintColor,
                    )
                    hintsDrawn++
                }
            }
        }

        // Top layer: the enlarged, focused plane and its tag.
        focused?.let { f ->
            drawPlaneEntry(
                f.hex, f.pos, f.color, f.screenRot, f.selected,
                planeStroke, f.label, tagScale.value,
                tagBg, tagBorder, titlePaint, subPaint, hitTargets,
                addFirst = true,
            )
        }

        // Trigger animation only when the near-hex actually changes.
        if (nearHexState.value != newNearHex) {
            nearHexState.value = newNearHex
        }
    }
}

private const val SELECTED_ICON_SCALE = 1.4f
private val SELECTED_HINT_STROKE = Stroke(width = 4f, join = androidx.compose.ui.graphics.StrokeJoin.Round)

private fun DrawScope.drawReticle(center: Offset) {
    val r = 26f
    drawCircle(Color.White.copy(alpha = 0.35f), radius = r, center = center, style = Stroke(1.5f))
    drawLine(Color.White.copy(alpha = 0.25f), Offset(center.x - r - 14f, center.y), Offset(center.x - r, center.y), 1.5f)
    drawLine(Color.White.copy(alpha = 0.25f), Offset(center.x + r, center.y), Offset(center.x + r + 14f, center.y), 1.5f)
}


/** Draws one plane (selection ring + icon + tag) and registers its hit target.
 *  [addFirst] puts the hit target ahead of the rest so taps match the top-most
 *  (focused) tag, mirroring the deferred-draw layering. */
private fun DrawScope.drawPlaneEntry(
    hex: String,
    pos: Offset,
    color: Color,
    screenRot: Float?,
    selected: Boolean,
    planeStroke: Color,
    label: PlaneLabel,
    scale: Float,
    tagBg: Color,
    tagBorder: Color,
    titlePaint: Paint,
    subPaint: Paint,
    hitTargets: MutableList<PlaneHit>,
    addFirst: Boolean = false,
) {
    // Icon grows at half the tag's rate so focus reads without the plane overwhelming the sky.
    // Selected: bigger icon with a white outline (mirrors the map view's selected plane).
    val iconScale = (1f + (scale - 1f) * 0.5f) * (if (selected) SELECTED_ICON_SCALE else 1f)
    val stroke = if (selected) Color.White else planeStroke
    if (screenRot != null) drawPlaneIcon(pos, screenRot, color, stroke = stroke, scale = iconScale)
    else {
        val rad = 16f * iconScale
        if (selected) drawCircle(stroke, radius = rad + 3f, center = pos)
        drawCircle(color, radius = rad, center = pos)
    }
    val tag = drawTag(pos, label.title, label.line1, label.line2, color, tagBg, tagBorder, titlePaint, subPaint, scale)
    val hit = PlaneHit(hex = hex, iconPos = pos, iconRadius = 40f, tag = tag)
    if (addFirst) hitTargets.add(0, hit) else hitTargets.add(hit)
}

// ponytail: PLANE_PATH is a module-level val; translate+rotate avoids per-frame Path allocs.
// pivot = Offset.Zero rotates around the translated origin (= pos on screen).
private fun DrawScope.drawPlaneIcon(pos: Offset, rotationDeg: Float, color: Color, stroke: Color, scale: Float) {
    translate(left = pos.x, top = pos.y) {
        scale(scale, pivot = Offset.Zero) {
            rotate(degrees = rotationDeg, pivot = Offset.Zero) {
                drawPath(PLANE_PATH, color)
                drawPath(PLANE_PATH, stroke, style = ICON_STROKE)
            }
        }
    }
}

private fun DrawScope.drawTag(
    iconPos: Offset,
    title: String,
    line1: String,
    line2: String,
    accent: Color,
    background: Color,
    border: Color,
    titlePaint: Paint,
    infoPaint: Paint,
    scale: Float = 1f,
): TagRect {
    // Scale paint text sizes for this draw call only, then restore.
    val baseTitleSize = titlePaint.textSize
    val baseInfoSize = infoPaint.textSize
    if (scale != 1f) {
        titlePaint.textSize = baseTitleSize * scale
        infoPaint.textSize = baseInfoSize * scale
    }

    val padX = 14f * scale
    val padY = 10f * scale
    val lineGap = 3f * scale
    val stripeW = 4f * scale
    val stripeGap = 11f * scale
    val leader = 11f * scale  // short connector from icon to the tag

    val titleH = titlePaint.textSize
    val infoH = infoPaint.textSize

    val contentLeftPad = padX + stripeW + stripeGap
    val textW = max(
        titlePaint.measureText(title),
        max(infoPaint.measureText(line1), infoPaint.measureText(line2)),
    )
    val boxW = contentLeftPad + textW + padX
    val boxH = titleH + infoH * 2 + lineGap * 2 + padY * 2

    val left = iconPos.x + 16f + leader
    val top = iconPos.y - boxH / 2f
    val cr = CornerRadius(13f * scale, 13f * scale)

    // Leader: a short hairline linking the icon to the tag so the pairing is clear.
    drawLine(
        color = border,
        start = Offset(iconPos.x + 16f, iconPos.y),
        end = Offset(left, iconPos.y),
        strokeWidth = 1.5f,
    )

    // Background + hairline border (border brightens slightly when enlarged).
    drawRoundRect(
        color = background,
        topLeft = Offset(left, top),
        size = Size(boxW, boxH),
        cornerRadius = cr,
    )
    drawRoundRect(
        color = if (scale > 1f) border.copy(alpha = 0.32f) else border,
        topLeft = Offset(left, top),
        size = Size(boxW, boxH),
        cornerRadius = cr,
        style = Stroke(1.5f),
    )

    // Category accent stripe at the left inside edge (replaces the old dot + divider).
    val stripeX = left + padX
    val stripeTop = top + padY
    val stripeH = boxH - padY * 2
    drawRoundRect(
        color = accent,
        topLeft = Offset(stripeX, stripeTop),
        size = Size(stripeW, stripeH),
        cornerRadius = CornerRadius(stripeW / 2f, stripeW / 2f),
    )

    // Text: callsign on top, then distance and altitude · speed.
    drawContext.canvas.nativeCanvas.let { canvas ->
        val textX = left + contentLeftPad
        val titleBase = top + padY + titleH - 2f * scale
        canvas.drawText(title, textX, titleBase, titlePaint)
        val line1Base = titleBase + lineGap + infoH
        canvas.drawText(line1, textX, line1Base, infoPaint)
        canvas.drawText(line2, textX, line1Base + lineGap + infoH, infoPaint)
    }

    // Restore paint sizes
    if (scale != 1f) {
        titlePaint.textSize = baseTitleSize
        infoPaint.textSize = baseInfoSize
    }

    return TagRect(left, top, left + boxW, top + boxH)
}

// ponytail: HINT_ARROW_PATH is a module-level val; translate+scale avoids per-frame Path+Offset allocs.
private fun DrawScope.drawOffScreenHint(
    center: Offset,
    bearingDeg: Float,
    span: Float,
    color: Color,
    scale: Float = 1f,
    selected: Boolean = false,
) {
    val radius = span * 0.42f
    rotate(degrees = bearingDeg, pivot = center) {
        translate(left = center.x, top = center.y - radius) {
            scale(scale = scale, pivot = Offset(0f, 13f)) {
                if (selected) {
                    // Selected: solid, white-outlined, double chevron (a second arrow trails
                    // the first) so it can't be mistaken for the faint generic hints.
                    translate(top = 16f) {
                        drawPath(HINT_ARROW_PATH, Color.White, style = SELECTED_HINT_STROKE)
                        drawPath(HINT_ARROW_PATH, color)
                    }
                    drawPath(HINT_ARROW_PATH, Color.White, style = SELECTED_HINT_STROKE)
                    drawPath(HINT_ARROW_PATH, color)
                } else {
                    drawPath(HINT_ARROW_PATH, color.copy(alpha = 0.9f))
                }
            }
        }
    }
}
