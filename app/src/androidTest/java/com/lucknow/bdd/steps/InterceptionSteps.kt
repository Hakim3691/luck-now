package com.lucknow.bdd.steps

import androidx.test.core.app.ActivityScenario
import com.lucknow.MainActivity
import com.lucknow.llm.HeuristicSequenceExtractor
import com.lucknow.scrape.ScrapeConfig
import com.lucknow.scrape.WebViewInterceptionDriver
import io.cucumber.java.After
import io.cucumber.java.en.And
import io.cucumber.java.en.Given
import io.cucumber.java.en.Then
import io.cucumber.java.en.When
import kotlinx.coroutines.runBlocking
import okhttp3.mockwebserver.Dispatcher
import okhttp3.mockwebserver.MockResponse
import okhttp3.mockwebserver.MockWebServer
import okhttp3.mockwebserver.RecordedRequest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue

/**
 * Drives the real hidden-WebView interception path against a local paginated
 * lottery site, then feeds the captured payloads to the real parsing module.
 */
class InterceptionSteps {

    private var server: MockWebServer? = null
    private var scenario: ActivityScenario<MainActivity>? = null

    @After
    fun tearDown() {
        scenario?.close()
        scenario = null
        server?.shutdown()
        server = null
    }

    @Given("the user launches {string}")
    fun theUserLaunches(application: String) {
        assertTrue(application.isNotBlank())
        scenario = ActivityScenario.launch(MainActivity::class.java)
    }

    @And("a lottery results site that paginates with infinite scroll")
    fun aLotteryResultsSite() {
        val webServer = MockWebServer()
        webServer.dispatcher = object : Dispatcher() {
            override fun dispatch(request: RecordedRequest): MockResponse {
                val path = request.path.orEmpty()
                if (path.startsWith("/api/draws")) {
                    val page = request.requestUrl?.queryParameter("page")?.toIntOrNull() ?: 1
                    return MockResponse()
                        .setResponseCode(200)
                        .setHeader("Content-Type", "application/json")
                        .setBody(drawsForPage(page))
                }
                return MockResponse()
                    .setResponseCode(200)
                    .setHeader("Content-Type", "text/html; charset=utf-8")
                    .setBody(RESULTS_PAGE)
            }
        }
        webServer.start()
        server = webServer
    }

    @When("the user enters the lottery site URL")
    fun theUserEntersTheUrl() {
        val webServer = requireNotNull(server) { "lottery site was not started" }
        BddWorld.siteUrl = webServer.url("/results").toString()
    }

    @When("taps the {string} button")
    fun tapsTheButton(label: String) {
        assertEquals("Analyze", label)
        val launched = requireNotNull(scenario) { "app was not launched" }

        lateinit var driver: WebViewInterceptionDriver
        launched.onActivity { activity -> driver = WebViewInterceptionDriver(activity) }

        val result = runBlocking {
            driver.intercept(
                url = BddWorld.siteUrl,
                config = ScrapeConfig(
                    maxScrollSteps = 8,
                    stepDelayMs = 700,
                    pageLoadTimeoutMs = 25_000,
                    settleTimeoutMs = 40_000,
                ),
            )
        }

        BddWorld.payloads = result.payloads
        BddWorld.pageLoaded = result.pageLoaded
        BddWorld.scrollSteps = result.scrollSteps

        // The intercepted JSON must be handed straight to the parsing module.
        BddWorld.extraction = HeuristicSequenceExtractor().extract(
            documents = result.payloads.map { payload -> payload.body },
        )
    }

    @Then("the app should load the URL in a hidden WebView")
    fun pageLoadedInHiddenWebView() {
        assertTrue("page never reached onPageFinished", BddWorld.pageLoaded)
    }

    @Then("intercept all incoming {string} payloads")
    fun interceptAllIncoming(mimeType: String) {
        assertEquals("application/json", mimeType)
        assertTrue("no JSON payloads were intercepted", BddWorld.payloads.isNotEmpty())
        BddWorld.payloads.forEach { payload ->
            val head = payload.body.trimStart().firstOrNull()
            assertTrue("payload is not JSON: ${payload.url}", head == '{' || head == '[')
        }
    }

    @Then("pass the intercepted JSON to the parsing module")
    fun passToParsingModule() {
        val extraction = requireNotNull(BddWorld.extraction) { "parsing module never ran" }
        // Two paginated pages x two draws x six numbers.
        assertEquals(24, extraction.values.size)
        assertTrue(BddWorld.payloads.size >= 2)
    }

    private fun drawsForPage(page: Int): String {
        val draws = if (page == 1) {
            listOf(listOf(4, 17, 23, 31, 38, 44), listOf(7, 11, 19, 25, 33, 41))
        } else {
            listOf(listOf(2, 13, 21, 29, 35, 49), listOf(3, 9, 16, 27, 36, 45))
        }
        val entries = draws.joinToString(",") { numbers ->
            """{"draw_date":"2024-02-0$page","numbers":[${numbers.joinToString(",")}]}"""
        }
        return """{"draw_history":[$entries]}"""
    }

    companion object {
        private val RESULTS_PAGE = """
            <!doctype html>
            <html>
              <head><title>Lottery results</title></head>
              <body style="height:3000px;margin:0">
                <div id="app">loading</div>
                <script>
                  var page = 1;
                  function load(p) {
                    return fetch('/api/draws?page=' + p, { headers: { 'Accept': 'application/json' } })
                      .then(function (r) { return r.json(); })
                      .then(function (data) {
                        document.getElementById('app').textContent =
                          data.draw_history.map(function (d) { return d.numbers.join(','); }).join(' | ');
                      });
                  }
                  load(1);
                  window.addEventListener('scroll', function () {
                    if (page < 2 && (window.scrollY + window.innerHeight) > 1800) {
                      page = 2;
                      load(2);
                    }
                  });
                </script>
              </body>
            </html>
        """.trimIndent()
    }
}
