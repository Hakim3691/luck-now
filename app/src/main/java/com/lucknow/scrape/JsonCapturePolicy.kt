package com.lucknow.scrape

/**
 * Decides whether a WebView subresource is a JSON API payload worth capturing.
 *
 * The primary signal is the request's `Accept` header (per the TRD). Real
 * lottery sites are inconsistent, so API-looking paths and XHR markers are also
 * accepted; static assets are always rejected.
 */
class JsonCapturePolicy(
    val maxBytes: Int = MAX_BYTES,
    val maxPayloads: Int = MAX_PAYLOADS,
) {

    fun isCandidate(url: String, headers: Map<String, String>): Boolean {
        if (url.isBlank()) return false
        val lowerUrl = url.lowercase()
        val path = lowerUrl.substringBefore('#').substringBefore('?')

        if (BLOCKED_EXTENSIONS.any { path.endsWith(it) }) return false

        val accept = headers.valueFor("Accept")
        if (accept.contains("json", ignoreCase = true)) return true

        if (path.endsWith(".json") || path.contains("/api/") || path.contains("/api.")) return true

        if (headers.valueFor("X-Requested-With").contains("xmlhttprequest", ignoreCase = true)) {
            return true
        }

        return headers.valueFor("Content-Type").contains("json", ignoreCase = true)
    }

    fun looksLikeJsonBody(body: String): Boolean {
        val trimmed = body.trimStart()
        if (trimmed.isEmpty() || trimmed.length > maxBytes) return false
        val first = trimmed.first()
        return first == '{' || first == '['
    }

    private fun Map<String, String>.valueFor(name: String): String =
        entries.firstOrNull { it.key.equals(name, ignoreCase = true) }?.value.orEmpty()

    companion object {
        const val MAX_BYTES = 4 * 1024 * 1024
        const val MAX_PAYLOADS = 40

        private val BLOCKED_EXTENSIONS = setOf(
            ".png", ".jpg", ".jpeg", ".gif", ".webp", ".svg", ".ico",
            ".css", ".woff", ".woff2", ".ttf", ".otf", ".mp4", ".webm",
            ".js", ".map", ".wasm",
        )
    }
}
