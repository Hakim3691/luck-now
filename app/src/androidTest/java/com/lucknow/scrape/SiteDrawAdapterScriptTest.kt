package com.lucknow.scrape

import android.os.SystemClock
import android.webkit.JavascriptInterface
import android.webkit.WebView
import android.webkit.WebViewClient
import androidx.test.core.app.ActivityScenario
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import com.lucknow.MainActivity
import java.util.concurrent.CopyOnWriteArrayList
import java.util.concurrent.CountDownLatch
import java.util.concurrent.TimeUnit
import org.json.JSONObject
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith

/**
 * Runs the real site-adapter scripts inside a real WebView against saved HTML.
 *
 * The fixtures are trimmed copies of the live pages - the tab strip and the panel
 * the dlb adapter reads, and the first rows of the nlb results table - with every
 * script and image removed, so the run never touches the network. Only the dlb
 * pagination response is stubbed, because that page fetches its rows itself. This
 * is where the selectors are actually proven: a JVM test cannot execute them.
 */
@RunWith(AndroidJUnit4::class)
class SiteDrawAdapterScriptTest {

    private val instrumentation = InstrumentationRegistry.getInstrumentation()
    private var scenario: ActivityScenario<MainActivity>? = null
    private var webView: WebView? = null

    @Before
    fun setUp() {
        scenario = ActivityScenario.launch(MainActivity::class.java)
    }

    @After
    fun tearDown() {
        webView?.let { view -> instrumentation.runOnMainSync { view.destroy() } }
        webView = null
        scenario?.close()
        scenario = null
    }

    @Test
    fun dlb_reads_the_active_lottery_tab() {
        val payload = runAdapter(
            asset = "dom/dlb_results_page.html",
            baseUrl = "https://www.dlb.lk/result/12/",
            script = DlbResultAdapter.script,
            prelude = dlbPaginationStub(),
        )

        assertEquals("dlb-results", payload.getString("adapter"))
        assertEquals("Kapruka", payload.getString("label"))

        val draws = payload.getJSONArray("draws")
        assertEquals(6, draws.length())
        val first = draws.getJSONObject(0)
        assertEquals(2515, first.getInt("draw"))
        // Neither the draw letter (li.res_eng_letter) nor the gold 2nd-chance
        // number shown with the results may be counted as a drawn number.
        assertEquals(listOf(13, 18, 25, 69), first.getJSONArray("values").toIntegers())

        val parsed = requireNotNull(DomDrawPayload.parse(payload.toString()))
        assertEquals(24, parsed.values.size)
        assertTrue(parsed.values.all { it in 0..99 })
    }

    @Test
    fun nlb_reads_the_rendered_results_table() {
        val payload = runAdapter(
            asset = "dom/nlb_results_page.html",
            baseUrl = "https://www.nlb.lk/results/dhana-nidhanaya",
            script = NlbResultAdapter.script,
        )

        assertEquals("nlb-results", payload.getString("adapter"))
        assertTrue(payload.getString("label").contains("Dhana Nidhanaya"))

        val draws = payload.getJSONArray("draws")
        assertEquals(11, draws.length())
        assertEquals(2396, draws.getJSONObject(0).getInt("draw"))
        assertEquals(
            listOf(5, 49, 56, 70),
            draws.getJSONObject(0).getJSONArray("values").toIntegers(),
        )

        val parsed = requireNotNull(DomDrawPayload.parse(payload.toString()))
        assertEquals(44, parsed.values.size)
        assertTrue(parsed.values.all { it in 0..99 })
    }

    private fun runAdapter(
        asset: String,
        baseUrl: String,
        script: String,
        prelude: String? = null,
    ): JSONObject {
        val html = instrumentation.context.assets.open(asset).bufferedReader().use { it.readText() }
        val bridge = RecordingBridge()
        val loaded = CountDownLatch(1)

        scenario?.onActivity { activity ->
            val view = WebView(activity)
            view.settings.javaScriptEnabled = true
            view.addJavascriptInterface(bridge, "LuckNowBridge")
            view.webViewClient = object : WebViewClient() {
                override fun onPageFinished(view: WebView, url: String) {
                    loaded.countDown()
                }
            }
            view.loadDataWithBaseURL(baseUrl, html, "text/html", "utf-8", null)
            webView = view
        }

        assertTrue("fixture did not load", loaded.await(30, TimeUnit.SECONDS))

        instrumentation.runOnMainSync {
            val view = requireNotNull(webView)
            prelude?.let { view.evaluateJavascript(it, null) }
            view.evaluateJavascript(script, null)
        }

        val deadline = SystemClock.uptimeMillis() + 10_000
        while (bridge.payloads.isEmpty() && SystemClock.uptimeMillis() < deadline) {
            Thread.sleep(50)
        }

        assertTrue("the adapter posted no payload", bridge.payloads.isNotEmpty())
        return JSONObject(bridge.payloads.first())
    }

    /** dlb fills its table from its own endpoint; serve the saved rows instead. */
    private fun dlbPaginationStub(): String {
        val rows = instrumentation.context.assets
            .open("dom/dlb_pagination_rows.html")
            .bufferedReader()
            .use { it.readText() }
        return """
            window.XMLHttpRequest = function () {
              var self = this;
              this.open = function () {};
              this.setRequestHeader = function () {};
              this.send = function () {
                setTimeout(function () {
                  self.status = 200;
                  self.responseText = ${JSONObject.quote(rows)};
                  if (self.onload) { self.onload(); }
                }, 1);
              };
            };
        """.trimIndent()
    }

    private fun org.json.JSONArray.toIntegers(): List<Int> =
        (0 until length()).map { getInt(it) }

    /** Kept out of the private scope so the WebView bridge can call it. */
    class RecordingBridge {
        val payloads = CopyOnWriteArrayList<String>()

        @JavascriptInterface
        fun postDraws(json: String) {
            payloads += json
        }
    }
}
