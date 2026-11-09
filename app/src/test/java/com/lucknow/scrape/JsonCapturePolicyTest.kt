package com.lucknow.scrape

import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class JsonCapturePolicyTest {

    private val policy = JsonCapturePolicy()

    @Test
    fun `accepts requests that ask for json`() {
        assertTrue(
            policy.isCandidate(
                url = "https://example-lottery.com/results",
                headers = mapOf("Accept" to "application/json, text/plain, */*"),
            ),
        )
    }

    @Test
    fun `accepts api looking paths`() {
        assertTrue(policy.isCandidate("https://example-lottery.com/api/draws?page=2", emptyMap()))
        assertTrue(policy.isCandidate("https://example-lottery.com/data/results.json", emptyMap()))
    }

    @Test
    fun `accepts xhr markers`() {
        assertTrue(
            policy.isCandidate(
                url = "https://example-lottery.com/results",
                headers = mapOf("X-Requested-With" to "XMLHttpRequest"),
            ),
        )
    }

    @Test
    fun `rejects static assets even when the accept header is broad`() {
        val headers = mapOf("Accept" to "*/*")
        assertFalse(policy.isCandidate("https://example-lottery.com/logo.png", headers))
        assertFalse(policy.isCandidate("https://example-lottery.com/app.css", headers))
        assertFalse(policy.isCandidate("https://example-lottery.com/app.js", headers))
    }

    @Test
    fun `rejects images requested with a json accept header`() {
        assertFalse(
            policy.isCandidate(
                url = "https://example-lottery.com/sprite.webp",
                headers = mapOf("Accept" to "application/json"),
            ),
        )
    }

    @Test
    fun `recognises json bodies only`() {
        assertTrue(policy.looksLikeJsonBody("""{"draw_history": []}"""))
        assertTrue(policy.looksLikeJsonBody("  [1,2,3]"))
        assertFalse(policy.looksLikeJsonBody("<!doctype html><html></html>"))
        assertFalse(policy.looksLikeJsonBody(""))
    }
}
