@file:SuppressLint("ConstantLocale")
// Statistical output uses Locale.US on purpose: p-values and coefficients must use
// "." as the decimal separator regardless of the device locale.

package com.lucknow.ui

import android.annotation.SuppressLint
import java.time.Instant
import java.time.ZoneId
import java.time.format.DateTimeFormatter
import java.util.Locale
import kotlin.math.abs

private val TIMESTAMP = DateTimeFormatter.ofPattern("dd MMM yyyy · HH:mm", Locale.getDefault())

fun formatTimestamp(epochMillis: Long): String =
    Instant.ofEpochMilli(epochMillis).atZone(ZoneId.systemDefault()).format(TIMESTAMP)

fun formatPValue(value: Double): String = when {
    value.isNaN() -> "n/a"
    value < 0.0001 -> "< 0.0001"
    value < 0.001 -> String.format(Locale.US, "%.4f", value)
    else -> String.format(Locale.US, "%.3f", value)
}

fun formatCoefficient(value: Double): String = String.format(Locale.US, "%.3f", value)

fun describeMagnitude(value: Double): String = when {
    abs(value) >= 0.5 -> "strong"
    abs(value) >= 0.3 -> "moderate"
    abs(value) >= 0.1 -> "weak"
    else -> "negligible"
}
