package com.lucknow.ui

import com.lucknow.analysis.AnalysisReport

/**
 * Single source of truth for how verdicts are phrased in the UI.
 *
 * Keeping the wording here (rather than inline in a composable) means the BDD
 * suite can assert exactly what the user is shown.
 */
object AnalysisPresentation {

    const val PRNG_FLAW_HEADLINE = "PRNG Flaw Detected"
    const val BIAS_HEADLINE = "Mechanical Bias Detected"
    const val CLEAN_HEADLINE = "No evidence of bias or PRNG flaws"

    fun headline(report: AnalysisReport): String = when {
        report.hasPrngFlaw -> PRNG_FLAW_HEADLINE
        report.hasMechanicalBias -> BIAS_HEADLINE
        else -> CLEAN_HEADLINE
    }

    fun detail(report: AnalysisReport): String = when {
        report.hasPrngFlaw -> {
            val lag = report.autocorrelation.firstSignificantLag
            val pValue = report.autocorrelation.pValues.getOrNull(lag ?: -1) ?: Double.NaN
            "Autocorrelation is significant at lag $lag (p = ${formatPValue(pValue)} < ${report.autocorrelation.alpha}). Draws are not independent."
        }

        report.hasMechanicalBias ->
            "Chi-square p = ${formatPValue(report.chiSquare.pValue)}: the outcome distribution is not uniform."

        else -> "No significant structure found in ${report.length} draws."
    }
}
