package com.xergioalex.kmppttdynamics.ui.components

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.layout.size
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.StrokeJoin
import androidx.compose.ui.graphics.drawscope.DrawScope
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp

@Composable
fun IconCheck(
    tint: Color,
    modifier: Modifier = Modifier,
    size: Dp = 24.dp,
) {
    Canvas(modifier = modifier.size(size)) {
        val w = this.size.width
        val h = this.size.height
        val stroke = Stroke(width = w * 0.12f, cap = StrokeCap.Round, join = StrokeJoin.Round)
        val path = Path().apply {
            moveTo(w * 0.2f, h * 0.5f)
            lineTo(w * 0.42f, h * 0.72f)
            lineTo(w * 0.8f, h * 0.28f)
        }
        drawPath(path, tint, style = stroke)
    }
}

@Composable
fun IconClose(
    tint: Color,
    modifier: Modifier = Modifier,
    size: Dp = 24.dp,
) {
    Canvas(modifier = modifier.size(size)) {
        val w = this.size.width
        val h = this.size.height
        val stroke = Stroke(width = w * 0.12f, cap = StrokeCap.Round)
        drawLine(tint, Offset(w * 0.25f, h * 0.25f), Offset(w * 0.75f, h * 0.75f), strokeWidth = stroke.width)
        drawLine(tint, Offset(w * 0.75f, h * 0.25f), Offset(w * 0.25f, h * 0.75f), strokeWidth = stroke.width)
    }
}

@Composable
fun IconArrowBack(
    tint: Color,
    modifier: Modifier = Modifier,
    size: Dp = 24.dp,
) {
    Canvas(modifier = modifier.size(size)) {
        val w = this.size.width
        val h = this.size.height
        val stroke = Stroke(width = w * 0.10f, cap = StrokeCap.Round, join = StrokeJoin.Round)
        val path = Path().apply {
            moveTo(w * 0.6f, h * 0.2f)
            lineTo(w * 0.3f, h * 0.5f)
            lineTo(w * 0.6f, h * 0.8f)
        }
        drawPath(path, tint, style = stroke)
        drawLine(tint, Offset(w * 0.3f, h * 0.5f), Offset(w * 0.78f, h * 0.5f), strokeWidth = stroke.width)
    }
}

@Composable
fun IconThumbUp(
    tint: Color,
    filled: Boolean = true,
    modifier: Modifier = Modifier,
    size: Dp = 24.dp,
) {
    Canvas(modifier = modifier.size(size)) {
        val w = this.size.width
        val h = this.size.height
        val stroke = Stroke(width = w * 0.08f, cap = StrokeCap.Round, join = StrokeJoin.Round)
        val thumb = Path().apply {
            moveTo(w * 0.28f, h * 0.45f)
            lineTo(w * 0.40f, h * 0.18f)
            lineTo(w * 0.52f, h * 0.18f)
            lineTo(w * 0.52f, h * 0.38f)
            lineTo(w * 0.80f, h * 0.38f)
            lineTo(w * 0.80f, h * 0.80f)
            lineTo(w * 0.28f, h * 0.80f)
            close()
        }
        if (filled) {
            drawPath(thumb, tint)
        } else {
            drawPath(thumb, tint, style = stroke)
        }
    }
}

@Composable
fun IconLightMode(
    tint: Color,
    modifier: Modifier = Modifier,
    size: Dp = 24.dp,
) {
    Canvas(modifier = modifier.size(size)) {
        drawSun(tint)
    }
}

private fun DrawScope.drawSun(color: Color) {
    val cx = size.width / 2f
    val cy = size.height / 2f
    val r = size.width * 0.18f
    drawCircle(color, radius = r, center = Offset(cx, cy))
    val rayLen = size.width * 0.14f
    val rayStart = r + size.width * 0.06f
    val sw = size.width * 0.08f
    for (i in 0 until 8) {
        val angle = (i * 45) * kotlin.math.PI / 180.0
        val cos = kotlin.math.cos(angle).toFloat()
        val sin = kotlin.math.sin(angle).toFloat()
        drawLine(
            color,
            start = Offset(cx + cos * rayStart, cy + sin * rayStart),
            end = Offset(cx + cos * (rayStart + rayLen), cy + sin * (rayStart + rayLen)),
            strokeWidth = sw,
            cap = StrokeCap.Round,
        )
    }
}

@Composable
fun IconDarkMode(
    tint: Color,
    modifier: Modifier = Modifier,
    size: Dp = 24.dp,
) {
    Canvas(modifier = modifier.size(size)) {
        val w = this.size.width
        val h = this.size.height
        val cx = w * 0.46f
        val cy = h * 0.46f
        val r = w * 0.30f
        val stroke = Stroke(width = w * 0.09f, cap = StrokeCap.Round)
        drawArc(
            color = tint,
            startAngle = -30f,
            sweepAngle = 240f,
            useCenter = false,
            topLeft = Offset(cx - r, cy - r),
            size = Size(r * 2, r * 2),
            style = stroke,
        )
        drawCircle(tint, radius = w * 0.04f, center = Offset(w * 0.72f, h * 0.28f))
        drawCircle(tint, radius = w * 0.03f, center = Offset(w * 0.80f, h * 0.45f))
    }
}

@Composable
fun IconContrast(
    tint: Color,
    modifier: Modifier = Modifier,
    size: Dp = 24.dp,
) {
    Canvas(modifier = modifier.size(size)) {
        val w = this.size.width
        val h = this.size.height
        val cx = w / 2f
        val cy = h / 2f
        val r = w * 0.36f
        val stroke = Stroke(width = w * 0.08f)
        drawCircle(tint, radius = r, center = Offset(cx, cy), style = stroke)
        drawArc(
            color = tint,
            startAngle = -90f,
            sweepAngle = 180f,
            useCenter = true,
            topLeft = Offset(cx - r, cy - r),
            size = Size(r * 2, r * 2),
        )
    }
}

@Composable
fun IconSparkle(
    tint: Color,
    modifier: Modifier = Modifier,
    size: Dp = 24.dp,
) {
    Canvas(modifier = modifier.size(size)) {
        val w = this.size.width
        val h = this.size.height
        val cx = w / 2f
        val cy = h / 2f
        val path = Path().apply {
            moveTo(cx, cy - h * 0.4f)
            lineTo(cx + w * 0.08f, cy - h * 0.08f)
            lineTo(cx + w * 0.4f, cy)
            lineTo(cx + w * 0.08f, cy + h * 0.08f)
            lineTo(cx, cy + h * 0.4f)
            lineTo(cx - w * 0.08f, cy + h * 0.08f)
            lineTo(cx - w * 0.4f, cy)
            lineTo(cx - w * 0.08f, cy - h * 0.08f)
            close()
        }
        drawPath(path, tint)
    }
}

@Composable
fun IconCircleOutline(
    tint: Color,
    modifier: Modifier = Modifier,
    size: Dp = 24.dp,
) {
    Canvas(modifier = modifier.size(size)) {
        val w = this.size.width
        val stroke = Stroke(width = w * 0.12f)
        drawCircle(tint, radius = w * 0.36f, style = stroke)
    }
}

@Composable
fun IconLock(
    tint: Color,
    modifier: Modifier = Modifier,
    size: Dp = 24.dp,
) {
    Canvas(modifier = modifier.size(size)) {
        val w = this.size.width
        val h = this.size.height
        val stroke = Stroke(width = w * 0.10f, cap = StrokeCap.Round, join = StrokeJoin.Round)
        val bodyLeft = w * 0.25f
        val bodyTop = h * 0.45f
        val bodyRight = w * 0.75f
        val bodyBottom = h * 0.85f
        drawRect(tint, topLeft = Offset(bodyLeft, bodyTop), size = Size(bodyRight - bodyLeft, bodyBottom - bodyTop))
        val arcLeft = w * 0.32f
        val arcRight = w * 0.68f
        val arcTop = h * 0.18f
        val arcBottom = h * 0.50f
        drawArc(
            color = tint,
            startAngle = 180f,
            sweepAngle = 180f,
            useCenter = false,
            topLeft = Offset(arcLeft, arcTop),
            size = Size(arcRight - arcLeft, arcBottom - arcTop),
            style = stroke,
        )
    }
}
