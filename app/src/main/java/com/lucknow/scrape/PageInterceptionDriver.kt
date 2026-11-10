package com.lucknow.scrape

/** Tuning for one hidden-browser session. */
data class ScrapeConfig(
    val maxScrollSteps: Int = 12,
    val stepDelayMs: Long = 900,
    val pageLoadTimeoutMs: Long = 20_000,
    val settleTimeoutMs: Long = 45_000,
    /** Per scroll step; a renderer that stops answering must not stall the scan. */
    val stepTimeoutMs: Long = 4_000,
    /** Grace window for a cookie/JS challenge page that reloads itself. */
    val reloadGraceMs: Long = 1_500,
    /** How long a site adapter may take to read the rendered HTML. */
    val domAdapterTimeoutMs: Long = 12_000,
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
