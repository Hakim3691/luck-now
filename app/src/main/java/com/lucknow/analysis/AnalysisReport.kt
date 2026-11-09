package com.lucknow.analysis

import org.json.JSONArray
import org.json.JSONObject

class AnalysisException(message: String, cause: Throwable? = null) : Exception(message, cause)

/** Result of the Chaquopy/statistics stage. Mirrors `lucknow.analysis`'s JSON contract. */
data class AnalysisReport(
    val engine: String,
    val libraryVersions: Map<String, String>,
    val length: Int,
    val min: Int,
    val max: Int,
    val mean: Double,
    val stddev: Double,
    val distinct: Int,
    val chiSquare: ChiSquareReport,
    val autocorrelation: AcfReport,
    val warnings: List<AnalysisWarning>,
    val verdict: String,
    val computedInMs: Int,
    val rawJson: String = "",
) {
    val hasPrngFlaw: Boolean get() = autocorrelation.flawed
    val hasMechanicalBias: Boolean get() = chiSquare.biased
    val isClean: Boolean get() = !hasPrngFlaw && !hasMechanicalBias

    companion object {
        fun fromJson(raw: String): AnalysisReport {
            val root = runCatching { JSONObject(raw) }
                .getOrElse { throw AnalysisException("Analysis engine returned invalid JSON", it) }

            if (!root.optBoolean("ok", false)) {
                val message = root.optJSONObject("error")?.optString("message").orEmpty()
                throw AnalysisException(message.ifBlank { "Analysis engine failed" })
            }

            val sequence = root.getJSONObject("sequence")
            val versions = root.optJSONObject("library_versions")?.let { json ->
                buildMap { json.keys().forEach { key -> put(key, json.optString(key)) } }
            }.orEmpty()

            return AnalysisReport(
                engine = root.optString("engine", "unknown"),
                libraryVersions = versions,
                length = sequence.optInt("length"),
                min = sequence.optInt("min"),
                max = sequence.optInt("max"),
                mean = sequence.optDouble("mean", 0.0),
                stddev = sequence.optDouble("stddev", 0.0),
                distinct = sequence.optInt("distinct"),
                chiSquare = ChiSquareReport.fromJson(root.getJSONObject("chi_square")),
                autocorrelation = AcfReport.fromJson(root.getJSONObject("autocorrelation")),
                warnings = root.optJSONArray("warnings").toObjectList { AnalysisWarning.fromJson(it) },
                verdict = root.optString("verdict", "unknown"),
                computedInMs = root.optInt("computed_in_ms"),
                rawJson = raw,
            )
        }

        fun errorFromJson(raw: String): String? = runCatching {
            val root = JSONObject(raw)
            if (root.optBoolean("ok", false)) null
            else root.optJSONObject("error")?.optString("message") ?: "Analysis engine failed"
        }.getOrNull()
    }
}

data class ChiSquareReport(
    val statistic: Double,
    val pValue: Double,
    val degreesOfFreedom: Int,
    val supportMin: Int,
    val supportMax: Int,
    val expectedPerBucket: Double,
    val minExpectedCount: Double,
    val reliable: Boolean,
    val alpha: Double,
    val biased: Boolean,
    val counts: List<Int>,
    val labels: List<Int>,
) {
    companion object {
        fun fromJson(json: JSONObject): ChiSquareReport {
            val support = json.getJSONObject("support")
            return ChiSquareReport(
                statistic = json.optDouble("statistic", 0.0),
                pValue = json.optDouble("p_value", 1.0),
                degreesOfFreedom = json.optInt("degrees_of_freedom"),
                supportMin = support.optInt("min"),
                supportMax = support.optInt("max"),
                expectedPerBucket = json.optDouble("expected_per_bucket", 0.0),
                minExpectedCount = json.optDouble("min_expected_count", 0.0),
                reliable = json.optBoolean("reliable", false),
                alpha = json.optDouble("alpha", 0.05),
                biased = json.optBoolean("biased", false),
                counts = json.optJSONArray("counts").toIntList(),
                labels = json.optJSONArray("labels").toIntList(),
            )
        }
    }
}

data class AcfReport(
    val alpha: Double,
    val maxLag: Int,
    val lags: List<Int>,
    val coefficients: List<Double>,
    val ciLower: List<Double>,
    val ciUpper: List<Double>,
    val pValues: List<Double>,
    val significantLags: List<SignificantLag>,
    val firstSignificantLag: Int?,
    val strongest: StrongestLag?,
    val ljungBox: LjungBoxResult?,
    val flawed: Boolean,
    val estimator: String,
    val fallbackEstimator: Boolean,
) {
    companion object {
        fun fromJson(json: JSONObject): AcfReport = AcfReport(
            alpha = json.optDouble("alpha", 0.05),
            maxLag = json.optInt("max_lag"),
            lags = json.optJSONArray("lags").toIntList(),
            coefficients = json.optJSONArray("coefficients").toDoubleList(),
            ciLower = json.optJSONArray("ci_lower").toDoubleList(),
            ciUpper = json.optJSONArray("ci_upper").toDoubleList(),
            pValues = json.optJSONArray("p_values").toDoubleList(),
            significantLags = json.optJSONArray("significant_lags")
                .toObjectList { SignificantLag.fromJson(it) },
            firstSignificantLag = json.optIntOrNull("first_significant_lag"),
            strongest = json.optJSONObject("strongest")?.let {
                StrongestLag(it.optInt("lag"), it.optDouble("coefficient", 0.0))
            },
            ljungBox = json.optJSONObject("ljung_box")?.let {
                LjungBoxResult(it.optInt("lag"), it.optDouble("statistic", 0.0), it.optDouble("p_value", 1.0))
            },
            flawed = json.optBoolean("flawed", false),
            estimator = json.optString("estimator", "unknown"),
            fallbackEstimator = json.optBoolean("fallback_estimator", false),
        )
    }
}

data class SignificantLag(val lag: Int, val coefficient: Double, val pValue: Double) {
    companion object {
        fun fromJson(json: JSONObject) = SignificantLag(
            lag = json.optInt("lag"),
            coefficient = json.optDouble("coefficient", 0.0),
            pValue = json.optDouble("p_value", 1.0),
        )
    }
}

data class StrongestLag(val lag: Int, val coefficient: Double)

data class LjungBoxResult(val lag: Int, val statistic: Double, val pValue: Double)

data class AnalysisWarning(val code: String, val severity: String, val message: String) {
    companion object {
        fun fromJson(json: JSONObject) = AnalysisWarning(
            code = json.optString("code"),
            severity = json.optString("severity", "low"),
            message = json.optString("message"),
        )
    }
}

private fun JSONArray?.toIntList(): List<Int> {
    if (this == null) return emptyList()
    return (0 until length()).map { optInt(it) }
}

private fun JSONArray?.toDoubleList(): List<Double> {
    if (this == null) return emptyList()
    return (0 until length()).map { optDouble(it) }
}

private fun <T> JSONArray?.toObjectList(mapper: (JSONObject) -> T): List<T> {
    if (this == null) return emptyList()
    return (0 until length()).mapNotNull { index -> optJSONObject(index)?.let(mapper) }
}

private fun JSONObject.optIntOrNull(key: String): Int? =
    if (has(key) && !isNull(key)) optInt(key) else null
