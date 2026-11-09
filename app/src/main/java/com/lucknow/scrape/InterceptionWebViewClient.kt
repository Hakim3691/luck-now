package com.lucknow.scrape

import android.webkit.WebResourceRequest
import android.webkit.WebResourceResponse
import android.webkit.WebView
import android.webkit.WebViewClient
import java.io.ByteArrayInputStream

/**
 * WebView client that intercepts JSON traffic.
 *
 * `shouldInterceptRequest` runs off the main thread, so it is safe to perform
 * the re-fetch synchronously. The body is inspected, handed to the capture sink,
 * and then returned to the WebView unchanged so the page behaves normally.
 */
class InterceptionWebViewClient(
    private val policy: JsonCapturePolicy,
    private val transport: HttpTransport,
    private val onCapture: (CapturedPayload) -> Unit,
    private val onLog: (String) -> Unit,
    private val onPageFinished: (String) -> Unit,
) : WebViewClient() {

    override fun shouldInterceptRequest(
        view: WebView,
        request: WebResourceRequest,
    ): WebResourceResponse? {
        if (request.isForMainFrame || request.method != "GET") return null
        val url = request.url.toString()
        if (!policy.isCandidate(url, request.requestHeaders)) return null

        return try {
            val response = transport.get(url, request.requestHeaders, policy.maxBytes)
            if (response.statusCode !in 200..299) {
                onLog("Skipped ${response.statusCode} response from $url")
                return null
            }
            val body = response.body.toString(Charsets.UTF_8)
            val isJson = response.contentType?.contains("json", ignoreCase = true) == true ||
                policy.looksLikeJsonBody(body)
            if (!isJson) return null

            onCapture(
                CapturedPayload(
                    url = url,
                    body = body,
                    mimeType = response.contentType,
                ),
            )
            WebResourceResponse(
                response.contentType?.substringBefore(';')?.trim() ?: "application/json",
                "utf-8",
                200,
                "OK",
                mapOf(
                    "Access-Control-Allow-Origin" to "*",
                    "Cache-Control" to "no-store",
                ),
                ByteArrayInputStream(response.body),
            )
        } catch (error: Exception) {
            // Returning null hands the request back to the WebView, so a failed
            // capture never breaks page rendering.
            onLog("Interception passthrough for $url (${error.message})")
            null
        }
    }

    override fun onPageFinished(view: WebView, url: String) {
        onPageFinished(url)
    }

    override fun onReceivedError(
        view: WebView,
        request: WebResourceRequest,
        error: android.webkit.WebResourceError,
    ) {
        if (request.isForMainFrame) {
            onLog("Navigation error ${error.errorCode}: ${error.description}")
        }
    }
}
