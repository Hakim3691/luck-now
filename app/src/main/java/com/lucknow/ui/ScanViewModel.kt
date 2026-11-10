package com.lucknow.ui

import android.util.Log
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.lucknow.analysis.AnalysisReport
import com.lucknow.analysis.StatisticalAnalyzer
import com.lucknow.data.ScanRecord
import com.lucknow.data.ScanStore
import com.lucknow.llm.DrawVolumeEstimator
import com.lucknow.llm.ExtractionException
import com.lucknow.llm.ExtractionSelector
import com.lucknow.llm.LlamaNative
import com.lucknow.llm.ModelManager
import com.lucknow.scrape.CapturedPayload
import com.lucknow.scrape.PageInterceptionDriver
import com.lucknow.scrape.ScrapeConfig
import java.net.URL
import java.time.LocalTime
import java.time.format.DateTimeFormatter
import kotlinx.coroutines.Job
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import org.json.JSONArray

enum class Screen { Home, Scanning, Review, Results }

data class ScanUiState(
    val screen: Screen = Screen.Home,
    val url: String = "",
    val urlError: String? = null,
    val status: String = "Idle",
    val logs: List<String> = emptyList(),
    val payloadCount: Int = 0,
    val scrollSteps: Int = 0,
    val sequence: List<Int> = emptyList(),
    val extractionEngine: String = "",
    val extractionReason: String = "",
    val extractionAttempts: Int = 0,
    val degraded: Boolean = false,
    val expectedValues: Int? = null,
    val report: AnalysisReport? = null,
    val analyzing: Boolean = false,
    val error: String? = null,
    val history: List<ScanRecord> = emptyList(),
    val activeScanId: String? = null,
    val modelInstalled: Boolean = false,
    val nativeLlmAvailable: Boolean = false,
    val modelDownloadProgress: Float? = null,
    val modelDownloadError: String? = null,
)

/**
 * Drives the PRD pipeline: intercept -> normalize -> review -> analyse.
 * All heavy work is delegated to injected boundaries so the flow is testable
 * without a device (and so Cucumber can exercise the same wiring).
 */
class ScanViewModel(
    private val store: ScanStore,
    private val driver: PageInterceptionDriver,
    private val analyzer: StatisticalAnalyzer,
    private val selector: ExtractionSelector,
    private val modelManager: ModelManager? = null,
) : ViewModel() {

    private val _state = MutableStateFlow(ScanUiState())
    val state: StateFlow<ScanUiState> = _state.asStateFlow()

    private var scanJob: Job? = null

    init {
        refreshHistory()
        refreshModelState()
    }

    fun onUrlChange(value: String) {
        _state.update { it.copy(url = value, urlError = null, error = null) }
    }

    fun startScan() {
        if (scanJob?.isActive == true) return
        val normalized = normalizeUrl(_state.value.url)
        if (normalized == null) {
            _state.update { it.copy(urlError = "Enter a valid http(s) URL") }
            return
        }

        _state.update {
            it.copy(
                screen = Screen.Scanning,
                status = "Loading page in hidden browser",
                logs = emptyList(),
                payloadCount = 0,
                scrollSteps = 0,
                sequence = emptyList(),
                report = null,
                error = null,
                urlError = null,
                analyzing = false,
                activeScanId = null,
            )
        }

        scanJob = viewModelScope.launch {
            val record = try {
                store.create(normalized)
            } catch (error: Throwable) {
                fail(null, "Could not create scan: ${error.message}")
                return@launch
            }
            _state.update { it.copy(activeScanId = record.id) }

            try {
                val result = driver.intercept(
                    url = normalized,
                    config = ScrapeConfig(),
                    onPayload = { payload -> persist(record.id, payload) },
                    onLog = ::log,
                )

                _state.update {
                    it.copy(
                        payloadCount = result.payloads.size,
                        scrollSteps = result.scrollSteps,
                        status = "Normalizing ${result.payloads.size} payload(s)",
                    )
                }

                val documents = result.payloads.map { payload -> payload.body }
                if (documents.isEmpty()) {
                    fail(
                        record.id,
                        "Nothing analysable on that page: it returned no JSON with draw numbers " +
                            "and no built-in site adapter matched it. dlb.lk and nlb.lk result pages " +
                            "are read from their rendered tables; other sites need their own adapter.",
                    )
                    return@launch
                }

                val expected = DrawVolumeEstimator.estimate(documents)
                val selection = selector.select()
                log("Extraction engine: ${selection.reason}")

                val extraction = try {
                    selection.engine.extract(documents, expected)
                } catch (error: ExtractionException) {
                    fail(record.id, error.message ?: "Could not normalize the intercepted JSON")
                    return@launch
                }

                store.markAwaitingReview(
                    scanId = record.id,
                    payloadCount = documents.size,
                    valuesJson = valuesToJson(extraction.values),
                    engine = extraction.engine,
                )

                _state.update {
                    it.copy(
                        screen = Screen.Review,
                        status = "Awaiting review",
                        sequence = extraction.values,
                        extractionEngine = extraction.engine,
                        extractionReason = selection.reason,
                        extractionAttempts = extraction.attempts,
                        degraded = selection.degraded,
                        expectedValues = expected,
                    )
                }
                refreshHistory()
            } catch (error: Throwable) {
                fail(record.id, error.message ?: "Interception failed")
            }
        }
    }

    fun approveForAnalysis() {
        val values = _state.value.sequence
        val scanId = _state.value.activeScanId
        if (values.isEmpty() || _state.value.analyzing) return

        viewModelScope.launch {
            _state.update { it.copy(analyzing = true, status = "Running chi-square and ACF", error = null) }
            try {
                val report = analyzer.analyze(values)
                scanId?.let { store.markCompleted(it, report.rawJson, report.verdict) }
                _state.update {
                    it.copy(
                        screen = Screen.Results,
                        analyzing = false,
                        status = "Analysis complete",
                        report = report,
                    )
                }
                refreshHistory()
            } catch (error: Throwable) {
                scanId?.let { runCatching { store.markFailed(it, error.message ?: "Analysis failed") } }
                _state.update {
                    it.copy(
                        analyzing = false,
                        error = error.message ?: "Analysis failed",
                        status = "Analysis failed",
                    )
                }
            }
        }
    }

    fun openRecord(record: ScanRecord) {
        scanJob?.cancel()
        viewModelScope.launch {
            val values = parseValues(record.valuesJson)
            val report = record.reportJson?.let { raw ->
                runCatching { AnalysisReport.fromJson(raw) }.getOrNull()
            }
            _state.update {
                it.copy(
                    screen = if (report != null) Screen.Results else Screen.Review,
                    url = record.url,
                    activeScanId = record.id,
                    sequence = values,
                    extractionEngine = record.engine,
                    extractionReason = "Stored run from ${record.createdAt}",
                    degraded = record.engine.startsWith("deterministic"),
                    expectedValues = values.size,
                    report = report,
                    status = record.status,
                    error = record.error,
                    payloadCount = record.payloadCount,
                )
            }
        }
    }

    fun backToHome() {
        scanJob?.cancel()
        _state.update {
            it.copy(
                screen = Screen.Home,
                status = "Idle",
                error = null,
                analyzing = false,
                report = null,
                sequence = emptyList(),
                logs = emptyList(),
            )
        }
        refreshHistory()
    }

    fun dismissError() {
        _state.update { it.copy(error = null) }
    }

    fun downloadModel() {
        val manager = modelManager ?: return
        if (_state.value.modelDownloadProgress != null) return

        viewModelScope.launch {
            _state.update { it.copy(modelDownloadProgress = 0f, modelDownloadError = null) }
            try {
                manager.download { read, total ->
                    if (total > 0) {
                        _state.update {
                            it.copy(modelDownloadProgress = (read.toFloat() / total).coerceIn(0f, 1f))
                        }
                    }
                }
                _state.update { it.copy(modelDownloadProgress = null, status = "Model installed") }
                refreshModelState()
            } catch (error: Throwable) {
                _state.update {
                    it.copy(
                        modelDownloadProgress = null,
                        modelDownloadError = error.message ?: "Model download failed",
                    )
                }
            }
        }
    }

    fun refreshHistory() {
        viewModelScope.launch {
            runCatching { store.list() }.onSuccess { records ->
                _state.update { it.copy(history = records) }
            }
        }
    }

    fun refreshModelState() {
        _state.update {
            it.copy(
                modelInstalled = modelManager?.isInstalled() == true,
                nativeLlmAvailable = LlamaNative.isLoaded,
            )
        }
    }

    private fun persist(scanId: String, payload: CapturedPayload) {
        viewModelScope.launch {
            runCatching { store.savePayload(scanId, payload.url, payload.body, payload.mimeType) }
        }
        log("Intercepted ${payload.body.length} chars from ${payload.url.take(120)}")
    }

    private fun log(line: String) {
        val stamp = LocalTime.now().format(LOG_TIME)
        _state.update { it.copy(logs = (it.logs + "$stamp  $line").takeLast(MAX_LOG_LINES)) }
    }

    private suspend fun fail(scanId: String?, message: String) {
        scanId?.let { runCatching { store.markFailed(it, message) } }
        Log.w(TAG, message)
        _state.update {
            it.copy(
                screen = Screen.Home,
                status = "Failed",
                error = message,
                analyzing = false,
            )
        }
        refreshHistory()
    }

    private fun valuesToJson(values: List<Int>): String =
        JSONArray(values).toString()

    private fun parseValues(raw: String?): List<Int> {
        if (raw.isNullOrBlank()) return emptyList()
        return runCatching {
            val array = JSONArray(raw)
            (0 until array.length()).map { array.optInt(it) }
        }.getOrDefault(emptyList())
    }

    private fun normalizeUrl(raw: String): String? {
        val trimmed = raw.trim()
        if (trimmed.isEmpty()) return null
        val withScheme = when {
            trimmed.startsWith("http://", ignoreCase = true) -> trimmed
            trimmed.startsWith("https://", ignoreCase = true) -> trimmed
            else -> "https://$trimmed"
        }
        return runCatching {
            val url = URL(withScheme)
            if ((url.protocol == "http" || url.protocol == "https") && !url.host.isNullOrBlank()) {
                url.toString()
            } else {
                null
            }
        }.getOrNull()
    }

    companion object {
        private const val TAG = "LuckNow/ScanViewModel"
        private const val MAX_LOG_LINES = 300
        private val LOG_TIME = DateTimeFormatter.ofPattern("HH:mm:ss")
    }
}
