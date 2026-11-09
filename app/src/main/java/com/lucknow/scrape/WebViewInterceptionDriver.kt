package com.lucknow.scrape

import android.annotation.SuppressLint
import android.app.Activity
import android.graphics.Color
import android.view.View
import android.view.ViewGroup
import android.webkit.CookieManager
import android.webkit.JavascriptInterface
import android.webkit.WebChromeClient
import android.webkit.WebSettings
import android.webkit.WebView
import android.webkit.WebViewClient
import androidx.webkit.WebViewCompat
import androidx.webkit.WebViewFeature
import java.security.MessageDigest
import kotlin.coroutines.resume
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.delay
import kotlinx.coroutines.suspendCancellableCoroutine
import kotlinx.coroutines.withContext
import kotlinx.coroutines.withTimeoutOrNull

/**
 * Loads a URL in a hidden, fully-attached WebView, drives pagination/infinite
 * scroll, and returns the JSON payloads observed on the wire.
 *
 * The WebView must stay attached and laid out for lazy-loading pages to fire
 * their API calls, so it is added to the activity's decor view at full size with
 * zero alpha rather than being detached or zero-sized.
 *
 * Every wait in a session is bounded. A renderer that stops answering, a page
 * that never finishes loading, and a page that reloads itself behind a cookie
 * challenge all end the session with a log line instead of leaving the scan
 * spinning behind a frozen progress bar.
 */
class WebViewInterceptionDriver(
    private val activity: Activity,
    private val transport: HttpTransport = OkHttpTransport(),
    private val policy: JsonCapturePolicy = JsonCapturePolicy(),
    private val clock: () -> Long = System::currentTimeMillis,
) : PageInterceptionDriver {

    private val captured = mutableListOf<CapturedPayload>()
    private val seenHashes = HashSet<String>()
    private val lock = Any()

    override suspend fun intercept(
        url: String,
        config: ScrapeConfig,
        onPayload: (CapturedPayload) -> Unit,
        onLog: (String) -> Unit,
    ): InterceptionResult = withContext(Dispatchers.Main) {
        synchronized(lock) {
            captured.clear()
            seenHashes.clear()
        }

        val started = clock()
        // Several sites answer the first request with a stub that sets a cookie
        // and calls location.reload(). Every finished navigation is reported here
        // so the session scans the document that actually carries the results.
        val navigations = Channel<String>(Channel.CONFLATED)
        val webView = createWebView()
        var pageLoaded = false
        var timedOut = false
        var steps = 0

        try {
            attach(webView)
            webView.addJavascriptInterface(
                JsBridge { payload -> accept(payload, onPayload, onLog) },
                JsBridge.NAME,
            )
            if (WebViewFeature.isFeatureSupported(WebViewFeature.DOCUMENT_START_SCRIPT)) {
                WebViewCompat.addDocumentStartJavaScript(
                    webView,
                    ScrollScript.documentStartHooks,
                    setOf("*"),
                )
            } else {
                onLog("Document-start hooks unavailable; relying on request interception")
            }

            webView.webViewClient = InterceptionWebViewClient(
                policy = policy,
                transport = transport,
                onCapture = { payload -> accept(payload, onPayload, onLog) },
                onLog = onLog,
                onPageFinished = { finishedUrl -> navigations.trySend(finishedUrl) },
            )
            webView.loadUrl(url)
            onLog("Loaded $url in hidden WebView")

            val firstNavigation = withTimeoutOrNull(config.pageLoadTimeoutMs) { navigations.receive() }
            pageLoaded = firstNavigation != null
            if (firstNavigation == null) {
                onLog("Page did not reach onPageFinished within ${config.pageLoadTimeoutMs} ms")
            } else {
                var settledUrl = firstNavigation
                var followUps = 0
                while (followUps < MAX_FOLLOW_UP_NAVIGATIONS) {
                    val next = withTimeoutOrNull(config.reloadGraceMs) { navigations.receive() } ?: break
                    settledUrl = next
                    followUps++
                    onLog("Follow-up navigation to ${next.take(120)}; scanning the new document")
                }
                if (settledUrl != url) onLog("Scanning document ${settledUrl.take(160)}")
            }

            // A JSON endpoint (and pages whose XHR hooks are unavailable) still
            // resolves through a single re-fetch. The transport blocks, so this
            // runs off the main thread: on the main thread it fails immediately
            // as a NetworkOnMainThreadException, whose message is null.
            if (count() == 0) {
                withContext(Dispatchers.IO) { captureDirectJson(url, onPayload, onLog) }
            }

            val deadline = started + config.settleTimeoutMs
            var stagnant = 0
            while (steps < config.maxScrollSteps && clock() < deadline) {
                val before = count()
                val scrolled = withTimeoutOrNull(config.stepTimeoutMs) {
                    webView.evaluateScript(ScrollScript.scrollOnce)
                }
                if (scrolled == null) {
                    onLog("Page stopped responding at scroll step ${steps + 1}; ending the session")
                    break
                }
                delay(config.stepDelayMs)
                steps++
                val after = count()
                stagnant = if (after == before) stagnant + 1 else 0
                if (after > before) onLog("Scroll step $steps produced ${after - before} new payload(s)")
                if (stagnant >= 2 && steps >= 2) break
            }
            if (steps < config.maxScrollSteps && clock() >= deadline) timedOut = true
            onLog("Interception finished: ${count()} JSON payload(s) in $steps scroll step(s)")
        } finally {
            navigations.close()
            cleanup(webView)
        }

        InterceptionResult(
            payloads = snapshot(),
            scrollSteps = steps,
            durationMs = clock() - started,
            pageLoaded = pageLoaded,
            timedOut = timedOut,
        )
    }

    private fun captureDirectJson(
        url: String,
        onPayload: (CapturedPayload) -> Unit,
        onLog: (String) -> Unit,
    ) {
        runCatching {
            val response = transport.get(url, mapOf("Accept" to "application/json"), policy.maxBytes)
            if (response.statusCode !in 200..299) return@runCatching
            val body = response.body.toString(Charsets.UTF_8)
            if (policy.looksLikeJsonBody(body)) {
                accept(CapturedPayload(url, body, response.contentType), onPayload, onLog)
            }
        }.onFailure { onLog("Direct JSON probe failed: ${it.message ?: it::class.java.simpleName}") }
    }

    private fun accept(
        payload: CapturedPayload,
        onPayload: (CapturedPayload) -> Unit,
        onLog: (String) -> Unit,
    ): Boolean {
        val added = synchronized(lock) {
            if (captured.size >= policy.maxPayloads) return false
            if (!policy.looksLikeJsonBody(payload.body)) return false
            if (!seenHashes.add(sha256(payload.body))) return false
            captured += payload
            true
        }
        if (added) {
            onLog("Captured JSON · ${payload.body.length} chars · ${payload.url.take(160)}")
            onPayload(payload)
        }
        return added
    }

    private fun count(): Int = synchronized(lock) { captured.size }

    private fun snapshot(): List<CapturedPayload> = synchronized(lock) { captured.toList() }

    @SuppressLint("SetJavaScriptEnabled")
    private fun createWebView(): WebView {
        val webView = WebView(activity)
        webView.setBackgroundColor(Color.TRANSPARENT)
        webView.isVerticalScrollBarEnabled = false
        webView.importantForAccessibility = View.IMPORTANT_FOR_ACCESSIBILITY_NO_HIDE_DESCENDANTS
        webView.settings.apply {
            javaScriptEnabled = true
            domStorageEnabled = true
            databaseEnabled = true
            javaScriptCanOpenWindowsAutomatically = false
            setSupportMultipleWindows(false)
            loadWithOverviewMode = true
            useWideViewPort = true
            mixedContentMode = WebSettings.MIXED_CONTENT_NEVER_ALLOW
            mediaPlaybackRequiresUserGesture = true
            cacheMode = WebSettings.LOAD_DEFAULT
            allowFileAccess = false
            allowContentAccess = false
        }
        CookieManager.getInstance().setAcceptCookie(true)
        CookieManager.getInstance().setAcceptThirdPartyCookies(webView, false)
        webView.webChromeClient = WebChromeClient()
        return webView
    }

    private fun attach(webView: WebView) {
        val container = activity.findViewById<ViewGroup>(android.R.id.content)
            ?: (activity.window.decorView as ViewGroup)
        webView.alpha = 0f
        webView.isClickable = false
        container.addView(
            webView,
            ViewGroup.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT,
                ViewGroup.LayoutParams.MATCH_PARENT,
            ),
        )
    }

    private fun cleanup(webView: WebView) {
        runCatching { webView.removeJavascriptInterface(JsBridge.NAME) }
        runCatching { webView.stopLoading() }
        runCatching { webView.webViewClient = WebViewClient() }
        runCatching { (webView.parent as? ViewGroup)?.removeView(webView) }
        runCatching { webView.destroy() }
    }

    private suspend fun WebView.evaluateScript(script: String): String? =
        suspendCancellableCoroutine { continuation ->
            evaluateJavascript(script) { value ->
                if (continuation.isActive) continuation.resume(value)
            }
        }

    private fun sha256(body: String): String {
        val digest = MessageDigest.getInstance("SHA-256").digest(body.toByteArray())
        return digest.joinToString("") { "%02x".format(it) }
    }

    private class JsBridge(private val onPayload: (CapturedPayload) -> Unit) {
        @JavascriptInterface
        fun postJson(url: String, body: String) {
            if (body.length > MAX_JS_BODY) return
            onPayload(
                CapturedPayload(
                    url = url.take(2048),
                    body = body,
                    source = CapturedPayload.Source.JS_BRIDGE,
                ),
            )
        }

        companion object {
            const val NAME = "LuckNowBridge"
            const val MAX_JS_BODY = 512_000
        }
    }

    private companion object {
        const val MAX_FOLLOW_UP_NAVIGATIONS = 3
    }
}
