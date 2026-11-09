package com.lucknow.ui.screens

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.material3.Button
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.lucknow.analysis.AnalysisReport
import com.lucknow.ui.AnalysisPresentation
import com.lucknow.ui.LuckNowActions
import com.lucknow.ui.ScanUiState
import com.lucknow.ui.components.AcfChart
import com.lucknow.ui.components.Banner
import com.lucknow.ui.components.DistributionChart
import com.lucknow.ui.components.KeyValueRow
import com.lucknow.ui.components.SectionCard
import com.lucknow.ui.describeMagnitude
import com.lucknow.ui.formatCoefficient
import com.lucknow.ui.formatPValue
import com.lucknow.ui.theme.Amber
import com.lucknow.ui.theme.Danger
import com.lucknow.ui.theme.Mint
import com.lucknow.ui.theme.MutedText

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun ResultsScreen(state: ScanUiState, actions: LuckNowActions) {
    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text("Analysis") },
                colors = TopAppBarDefaults.topAppBarColors(
                    containerColor = MaterialTheme.colorScheme.background,
                ),
            )
        },
    ) { padding ->
        val report = state.report
        if (report == null) {
            Column(modifier = Modifier.fillMaxSize().padding(padding).padding(20.dp)) {
                Text("No analysis available for this run.", color = MutedText)
            }
            return@Scaffold
        }

        LazyColumn(
            modifier = Modifier.fillMaxSize().padding(padding),
            contentPadding = PaddingValues(horizontal = 20.dp, vertical = 8.dp),
            verticalArrangement = Arrangement.spacedBy(14.dp),
        ) {
            item { VerdictBanner(report) }

            item {
                SectionCard(title = "Sequence") {
                    KeyValueRow("Draws analysed", report.length.toString())
                    KeyValueRow("Range", "${report.min} – ${report.max}")
                    KeyValueRow("Distinct values", report.distinct.toString())
                    KeyValueRow("Mean", formatCoefficient(report.mean))
                    KeyValueRow("Std. deviation", formatCoefficient(report.stddev))
                    KeyValueRow("Parsing engine", state.extractionEngine.ifBlank { "n/a" })
                }
            }

            item {
                SectionCard(
                    title = "Chi-square uniformity",
                    subtitle = "H₀: each outcome is equally likely (no mechanical bias).",
                ) {
                    DistributionChart(
                        counts = report.chiSquare.counts,
                        expectedPerBucket = report.chiSquare.expectedPerBucket,
                    )
                    KeyValueRow("Statistic", formatCoefficient(report.chiSquare.statistic))
                    KeyValueRow("Degrees of freedom", report.chiSquare.degreesOfFreedom.toString())
                    KeyValueRow(
                        label = "p-value",
                        value = formatPValue(report.chiSquare.pValue),
                        valueColor = if (report.chiSquare.biased) Danger else Mint,
                    )
                    KeyValueRow(
                        label = "Verdict",
                        value = if (report.chiSquare.biased) "Non-uniform (p < ${report.chiSquare.alpha})" else "Consistent with uniform",
                        valueColor = if (report.chiSquare.biased) Danger else Mint,
                    )
                    if (!report.chiSquare.reliable) {
                        Text(
                            text = "Low expected counts (min ${"%.1f".format(report.chiSquare.minExpectedCount)}): chi-square may be unreliable for this sample.",
                            style = MaterialTheme.typography.labelSmall,
                            color = MutedText,
                        )
                    }
                }
            }

            item {
                SectionCard(
                    title = "Autocorrelation (ACF)",
                    subtitle = "H₀: draws are independent. A significant lag implies a weak PRNG or a mechanical carry-over.",
                ) {
                    AcfChart(
                        lags = report.autocorrelation.lags,
                        coefficients = report.autocorrelation.coefficients,
                        ciLower = report.autocorrelation.ciLower,
                        ciUpper = report.autocorrelation.ciUpper,
                        significantLags = report.autocorrelation.significantLags.map { it.lag }.toSet(),
                    )
                    report.autocorrelation.strongest?.let { strongest ->
                        KeyValueRow(
                            label = "Strongest lag",
                            value = "lag ${strongest.lag} · ${formatCoefficient(strongest.coefficient)} (${describeMagnitude(strongest.coefficient)})",
                        )
                    }
                    report.autocorrelation.firstSignificantLag?.let { lag ->
                        val pValue = report.autocorrelation.pValues.getOrNull(lag) ?: Double.NaN
                        KeyValueRow(
                            label = "First significant lag",
                            value = "lag $lag · p ${formatPValue(pValue)}",
                            valueColor = Danger,
                        )
                    } ?: KeyValueRow(
                        label = "Significant lags",
                        value = "none",
                        valueColor = Mint,
                    )
                    report.autocorrelation.ljungBox?.let { ljungBox ->
                        KeyValueRow(
                            label = "Ljung-Box (lag ${ljungBox.lag})",
                            value = "stat ${formatCoefficient(ljungBox.statistic)} · p ${formatPValue(ljungBox.pValue)}",
                            valueColor = if (ljungBox.pValue < report.autocorrelation.alpha) Amber else Mint,
                        )
                    }
                    KeyValueRow("Estimator", report.autocorrelation.estimator)
                }
            }

            if (report.warnings.isNotEmpty()) {
                item {
                    SectionCard(title = "Warnings") {
                        Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                            report.warnings.forEach { warning ->
                                Text(
                                    text = warning.message,
                                    style = MaterialTheme.typography.bodySmall,
                                    color = when (warning.severity) {
                                        "high" -> Danger
                                        "medium" -> Amber
                                        else -> MutedText
                                    },
                                )
                            }
                        }
                    }
                }
            }

            item {
                SectionCard(title = "Provenance") {
                    KeyValueRow("Engine", report.engine)
                    report.libraryVersions.forEach { (name, version) ->
                        KeyValueRow(name, version)
                    }
                    KeyValueRow("Computed in", "${report.computedInMs} ms")
                    KeyValueRow("Applied alpha", report.autocorrelation.alpha.toString())
                }
            }

            item {
                Button(onClick = actions.onBackHome, modifier = Modifier.fillMaxWidth()) {
                    Text("New scan")
                }
            }
        }
    }
}

@Composable
private fun VerdictBanner(report: AnalysisReport) {
    val color = when {
        report.hasPrngFlaw -> Danger
        report.hasMechanicalBias -> Amber
        else -> Mint
    }
    Banner(
        text = "${AnalysisPresentation.headline(report)} — ${AnalysisPresentation.detail(report)}",
        color = color,
    )

    Text(
        text = "Statistical screening only: p-values test the null hypothesis at α = ${report.autocorrelation.alpha}. They cannot prove fairness.",
        style = MaterialTheme.typography.labelSmall,
        color = MutedText,
        fontWeight = FontWeight.Normal,
        modifier = Modifier.padding(top = 8.dp),
    )
}
