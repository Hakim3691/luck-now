package com.lucknow.ui.components

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.PathEffect
import androidx.compose.ui.unit.dp

/** Bar chart of observed bucket counts with the uniform expectation overlaid. */
@Composable
fun DistributionChart(
    counts: List<Int>,
    expectedPerBucket: Double,
    modifier: Modifier = Modifier,
) {
    val barColor = MaterialTheme.colorScheme.primary
    val expectedColor = MaterialTheme.colorScheme.secondary
    val baseline = MaterialTheme.colorScheme.outline

    Canvas(modifier = modifier.fillMaxWidth().height(140.dp)) {
        if (counts.isEmpty()) return@Canvas
        val maxCount = (counts.maxOrNull() ?: 1).coerceAtLeast(1).toFloat()
        val barWidth = size.width / counts.size
        val originY = size.height - 2f

        drawLine(baseline, Offset(0f, originY), Offset(size.width, originY), strokeWidth = 1f)

        counts.forEachIndexed { index, count ->
            val height = (count / maxCount) * (size.height - 12f)
            drawRect(
                color = barColor,
                topLeft = Offset(index * barWidth + 0.5f, originY - height),
                size = Size((barWidth - 1f).coerceAtLeast(1f), height),
            )
        }

        val expectedY = originY - (expectedPerBucket.toFloat() / maxCount) * (size.height - 12f)
        drawLine(
            color = expectedColor,
            start = Offset(0f, expectedY),
            end = Offset(size.width, expectedY),
            strokeWidth = 2f,
            pathEffect = PathEffect.dashPathEffect(floatArrayOf(8f, 8f)),
        )
    }
}

/** Stem plot of ACF coefficients with the Bartlett confidence band. */
@Composable
fun AcfChart(
    lags: List<Int>,
    coefficients: List<Double>,
    ciLower: List<Double>,
    ciUpper: List<Double>,
    significantLags: Set<Int>,
    modifier: Modifier = Modifier,
) {
    val stemColor = MaterialTheme.colorScheme.secondary
    val flaggedColor = MaterialTheme.colorScheme.error
    val bandColor = MaterialTheme.colorScheme.outline.copy(alpha = 0.35f)
    val axisColor = MaterialTheme.colorScheme.outline

    Canvas(modifier = modifier.fillMaxWidth().height(160.dp)) {
        if (coefficients.isEmpty()) return@Canvas
        val midY = size.height / 2f
        val scale = midY * 0.88f
        val step = if (coefficients.size > 1) size.width / (coefficients.size - 1) else size.width

        drawLine(axisColor, Offset(0f, midY), Offset(size.width, midY), strokeWidth = 1f)

        val upper = ciUpper.getOrNull(1)?.toFloat()
        val lower = ciLower.getOrNull(1)?.toFloat()
        if (upper != null && lower != null) {
            drawRect(
                color = bandColor,
                topLeft = Offset(0f, midY - upper * scale),
                size = Size(size.width, (upper - lower) * scale),
            )
        }

        coefficients.forEachIndexed { index, coefficient ->
            val x = index * step
            val y = midY - coefficient.toFloat() * scale
            val color = if (lags.getOrNull(index) in significantLags) flaggedColor else stemColor
            drawLine(color, Offset(x, midY), Offset(x, y), strokeWidth = 2f)
            drawCircle(color, radius = 3.5f, center = Offset(x, y))
        }
    }
}

/** Small horizontal bar used for risk/severity indicators. */
@Composable
fun SeverityDot(color: Color, modifier: Modifier = Modifier) {
    Canvas(modifier = modifier.height(10.dp).fillMaxWidth()) {
        drawCircle(color = color, radius = 4.dp.toPx(), center = Offset(4.dp.toPx(), size.height / 2f))
    }
}
