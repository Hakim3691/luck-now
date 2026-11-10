package com.lucknow.scrape

/** A single JSON payload captured from the hidden browser session. */
data class CapturedPayload(
    val url: String,
    val body: String,
    val mimeType: String? = "application/json",
    val capturedAt: Long = System.currentTimeMillis(),
    val source: Source = Source.NETWORK_INTERCEPT,
) {
    enum class Source {
        NETWORK_INTERCEPT,
        JS_BRIDGE,
        /** Draw numbers read from rendered HTML by a [SiteDrawAdapter]. */
        DOM_ADAPTER,
    }
}
