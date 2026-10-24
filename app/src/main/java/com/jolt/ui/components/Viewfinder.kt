package com.jolt.ui.components

import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.tween
import androidx.compose.foundation.Canvas
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Rect
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.PathFillType
import androidx.compose.ui.graphics.PathMeasure
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.unit.dp
import com.jolt.mission.VIEWFINDER_FRACTION
import com.jolt.ui.theme.Amber

/**
 * Dims everything outside the square the analyzer looks at, draws corner brackets around it,
 * and fills a ring around the brackets as [progress] (0–1) rises.
 */
@Composable
fun Viewfinder(progress: Float?, modifier: Modifier = Modifier, locked: Boolean = false) {
    val animated by animateFloatAsState(progress ?: 0f, tween(220), label = "progress")
    Canvas(modifier) {
        // Mirrors FrameAnalyzer: a square of the visible area's short side, centred.
        val side = minOf(size.width, size.height) * VIEWFINDER_FRACTION
        val box = Rect(Offset((size.width - side) / 2, (size.height - side) / 2), Size(side, side))
        val radius = 28.dp.toPx()

        val scrim = Path().apply {
            fillType = PathFillType.EvenOdd
            addRect(Rect(Offset.Zero, size))
            addRoundRect(androidx.compose.ui.geometry.RoundRect(box, CornerRadius(radius)))
        }
        drawPath(scrim, Color.Black.copy(alpha = 0.55f))

        val stroke = 4.dp.toPx()
        val arm = side * 0.14f
        val color = if (locked) Amber else Color.White
        val corners = listOf(
            box.topLeft to Offset(1f, 1f),
            box.topRight to Offset(-1f, 1f),
            box.bottomLeft to Offset(1f, -1f),
            box.bottomRight to Offset(-1f, -1f),
        )
        corners.forEach { (corner, dir) ->
            val path = Path().apply {
                moveTo(corner.x, corner.y + dir.y * (radius + arm))
                lineTo(corner.x, corner.y + dir.y * radius)
                quadraticTo(corner.x, corner.y, corner.x + dir.x * radius, corner.y)
                lineTo(corner.x + dir.x * (radius + arm), corner.y)
            }
            drawPath(path, color, style = Stroke(stroke, cap = StrokeCap.Round))
        }

        if (progress != null) {
            val gap = 14.dp.toPx()
            val ring = box.inflate(gap)
            val ringStroke = 6.dp.toPx()
            val track = Path().apply {
                addRoundRect(androidx.compose.ui.geometry.RoundRect(ring, CornerRadius(radius + gap)))
            }
            drawPath(track, Color.White.copy(alpha = 0.15f), style = Stroke(ringStroke))
            if (animated > 0f) {
                val measure = PathMeasure().apply { setPath(track, false) }
                val filled = Path()
                measure.getSegment(0f, measure.length * animated, filled, true)
                drawPath(filled, Amber, style = Stroke(ringStroke, cap = StrokeCap.Round))
            }
        }
    }
}
