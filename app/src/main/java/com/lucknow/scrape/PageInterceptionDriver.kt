package com.lucknow.scrape

/** Tuning for one hidden-browser session. */
data class ScrapeConfig(
    val maxScrollSteps: Int = 12,
    val stepDelayMs: Long = 900,
    val pageLoadTimeoutMs: Long = 20_000,
    val settleTimeoutMs: Long = 45_000,
)

data class InterceptionResult(
    val payloads: List<CapturedPayload>,
    val scrollSteps: Int,
    val durationMs: Long,
    val pageLoaded: Boolean,
    val timedOut: Boolean,
)

/** Drives a page (hidden WebView) and returns the JSON payloads it produced. */
interface PageInterceptionDriver {
    suspend fun intercept(
        url: String,
        config: ScrapeConfig = ScrapeConfig(),
        onPayload: (CapturedPayload) -> Unit = {},
        onLog: (String) -> Unit = {},
    ): InterceptionResult
}
