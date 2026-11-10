package com.lucknow.scrape

import java.net.URI
import org.json.JSONArray
import org.json.JSONObject

/**
 * Reads draw results from a page that publishes them as HTML instead of JSON.
 *
 * The script runs inside the page's own document, so it can use the site's
 * same-origin endpoints and cookies, and it reports through the page bridge as
 * a JSON document the normalizer already understands. That keeps the parsing
 * rules in one place (a real DOM) instead of re-implementing an HTML parser.
 */
interface SiteDrawAdapter {
    /** Stable id, also written into the emitted payload for the audit trail. */
    val id: String

    /** Site name for scan logs and failure messages. */
    val site: String

    fun matches(url: String): Boolean

    /**
     * JavaScript that posts a `{"adapter","label","draws":[{"draw","values"}]}`
     * document to `window.LuckNowBridge.postDraws`. It may return immediately and
     * post later; the driver waits for the bridge with a bounded timeout.
     */
    val script: String
}

/** One draw with the numbers it produced, in the order they appear on the page. */
data class DomDraw(val draw: Int?, val values: List<Int>)

/** Draws read from one page by one adapter. */
data class DomDraws(
    val adapter: String,
    val label: String?,
    val draws: List<DomDraw>,
) {
    val values: List<Int> get() = draws.flatMap { it.values }
    val drawCount: Int get() = draws.size
}

/**
 * Validates what an adapter script posts before it can enter the pipeline.
 *
 * A broken selector or a changed page must fail loudly rather than feed a short
 * or out-of-range list into the statistical analysis, so anything below the
 * normalizer's minimum is rejected here.
 */
object DomDrawPayload {

    /** Same floor the deterministic extractor applies to any sequence. */
    const val MIN_VALUES = 10

    private const val MAX_VALUES = 20_000
    private const val MAX_LABEL = 80
    private const val MAX_ADAPTER = 40

    fun parse(raw: String?): DomDraws? {
        if (raw.isNullOrBlank()) return null
        val root = runCatching { JSONObject(raw) }.getOrNull() ?: return null
        val array = root.optJSONArray("draws") ?: return null

        val draws = ArrayList<DomDraw>(array.length())
        var total = 0
        for (index in 0 until array.length()) {
            if (total >= MAX_VALUES) break
            val item = array.optJSONObject(index) ?: continue
            val values = intsOf(item.optJSONArray("values"))
            if (values.isEmpty()) continue
            draws += DomDraw(item.optInt("draw", -1).takeIf { it > 0 }, values)
            total += values.size
        }
        if (draws.isEmpty() || total < MIN_VALUES) return null

        return DomDraws(
            adapter = root.optString("adapter").take(MAX_ADAPTER).ifBlank { "site-adapter" },
            label = root.optString("label").trim().take(MAX_LABEL).takeIf { it.isNotEmpty() },
            draws = draws,
        )
    }

    /** Canonical form, so the stored payload is exactly what was validated. */
    fun toJson(draws: DomDraws): String {
        val root = JSONObject()
        root.put("adapter", draws.adapter)
        draws.label?.let { root.put("label", it) }
        val array = JSONArray()
        draws.draws.forEach { draw ->
            val item = JSONObject()
            draw.draw?.let { item.put("draw", it) }
            item.put("values", JSONArray(draw.values))
            array.put(item)
        }
        root.put("draws", array)
        return root.toString()
    }

    private fun intsOf(array: JSONArray?): List<Int> {
        if (array == null) return emptyList()
        val values = ArrayList<Int>(array.length())
        for (index in 0 until array.length()) {
            val parsed = when (val item = array.opt(index)) {
                is Number -> item.toInt()
                is String -> item.trim().toIntOrNull()
                else -> null
            } ?: continue
            // Lottery draws are 0..99; anything else came from a different column
            // (a draw number, a date, a prize value).
            if (parsed in 0..999) values += parsed
        }
        return values
    }
}

/** Site adapters, tried only when a page produced no JSON at all. */
object SiteDrawAdapters {

    val all: List<SiteDrawAdapter> = listOf(DlbResultAdapter, NlbResultAdapter)

    fun forUrl(url: String): SiteDrawAdapter? = all.firstOrNull { it.matches(url) }

    internal fun hostOf(url: String): String? =
        runCatching { URI(url).host?.lowercase() }.getOrNull()

    internal fun hostEndsWith(url: String, domain: String): Boolean {
        val host = hostOf(url) ?: return false
        return host == domain || host.endsWith(".$domain")
    }
}
