package com.lucknow.analysis

import com.chaquo.python.Python
import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import org.json.JSONObject

/** Boundary to the on-device statistical engine. */
interface StatisticalAnalyzer {
    suspend fun analyze(values: List<Int>, alpha: Double = 0.05): AnalysisReport
}

/**
 * Runs `lucknow.analysis.analyze` inside the embedded CPython (Chaquopy)
 * interpreter. No network access, no API keys: scipy/statsmodels execute locally.
 */
class PythonStatisticalAnalyzer(
    private val dispatcher: CoroutineDispatcher = Dispatchers.Default,
    private val pythonProvider: () -> Python = { Python.getInstance() },
) : StatisticalAnalyzer {

    override suspend fun analyze(values: List<Int>, alpha: Double): AnalysisReport =
        withContext(dispatcher) {
            require(values.size >= MIN_SAMPLES) {
                "At least $MIN_SAMPLES draws are required for analysis"
            }

            val module = try {
                pythonProvider().getModule("lucknow.analysis")
            } catch (error: Throwable) {
                throw AnalysisException("Python runtime unavailable on this device", error)
            }

            val valuesJson = values.joinToString(separator = ",", prefix = "[", postfix = "]")
            val optionsJson = JSONObject()
                .put("alpha", alpha)
                .put("max_lag", MAX_LAG)
                .toString()

            val raw = try {
                module.callAttr("analyze", valuesJson, optionsJson).toString()
            } catch (error: Throwable) {
                throw AnalysisException("On-device analysis failed: ${error.message}", error)
            }

            AnalysisReport.fromJson(raw)
        }

    companion object {
        const val MIN_SAMPLES = 3
        private const val MAX_LAG = 20
    }
}
