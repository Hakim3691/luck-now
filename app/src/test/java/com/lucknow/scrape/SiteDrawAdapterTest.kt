package com.lucknow.scrape

import com.lucknow.llm.HeuristicSequenceExtractor
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class SiteDrawAdapterTest {

    @Test
    fun `dlb pages resolve to the dlb adapter`() {
        listOf(
            "https://www.dlb.lk/result/12/",
            "https://dlb.lk/result/en/",
            "http://WWW.DLB.LK/result/12/",
        ).forEach { url ->
            assertEquals(DlbResultAdapter, SiteDrawAdapters.forUrl(url))
        }
    }

    @Test
    fun `nlb pages resolve to the nlb adapter`() {
        listOf(
            "https://www.nlb.lk/results/dhana-nidhanaya",
            "https://nlb.lk/results/",
        ).forEach { url ->
            assertEquals(NlbResultAdapter, SiteDrawAdapters.forUrl(url))
        }
    }

    @Test
    fun `other hosts have no adapter`() {
        listOf(
            "https://example.com/results",
            "https://notdlb.lk/result/12/",
            "https://dlb.lk.evil.example/result/12/",
            "not a url",
        ).forEach { url ->
            assertNull(url, SiteDrawAdapters.forUrl(url))
        }
    }

    @Test
    fun `adapter payload keeps every draw in page order`() {
        val draws = DomDrawPayload.parse(
            """
            {"adapter":"nlb-results","label":"Dhana Nidhanaya Lottery Results",
             "draws":[{"draw":2396,"values":[5,49,56,70]},
                      {"draw":2395,"values":[19,29,72,80]},
                      {"draw":2394,"values":[5,27,62,66]}]}
            """.trimIndent(),
        )

        requireNotNull(draws)
        assertEquals("nlb-results", draws.adapter)
        assertEquals("Dhana Nidhanaya Lottery Results", draws.label)
        assertEquals(3, draws.drawCount)
        assertEquals(listOf(5, 49, 56, 70), draws.draws.first().values)
        assertEquals(
            listOf(5, 49, 56, 70, 19, 29, 72, 80, 5, 27, 62, 66),
            draws.values,
        )
    }

    @Test
    fun `zero padded strings are numbers, draw numbers and dates are not`() {
        val draws = DomDrawPayload.parse(
            """
            {"adapter":"dlb-results","label":"Kapruka",
             "draws":[{"draw":3165,"values":["16","03",3165,20,31,58,2,13,15,21]},
                      {"draw":3164,"values":[6,42,63,71]}]}
            """.trimIndent(),
        )

        requireNotNull(draws)
        // 3165 is outside 0..999, so it is a draw/prize column rather than a number.
        assertEquals(listOf(16, 3, 20, 31, 58, 2, 13, 15, 21, 6, 42, 63, 71), draws.values)
        assertEquals(2, draws.drawCount)
    }

    @Test
    fun `a short or empty read is rejected instead of analysed`() {
        val tooShort = """
            {"adapter":"nlb-results","draws":[{"draw":2396,"values":[5,49,56,70]}]}
        """.trimIndent()
        assertNull(DomDrawPayload.parse(tooShort))
        assertNull(DomDrawPayload.parse("""{"adapter":"nlb-results","draws":[]}"""))
        assertNull(DomDrawPayload.parse("""{"adapter":"nlb-results"}"""))
        assertNull(DomDrawPayload.parse("not json"))
        assertNull(DomDrawPayload.parse(""))
        assertNull(DomDrawPayload.parse(null))
    }

    @Test
    fun `missing draw numbers and labels are tolerated`() {
        val draws = DomDrawPayload.parse(
            """
            {"adapter":"nlb-results",
             "draws":[{"values":[1,2,3,4]},{"values":[5,6,7,8]},{"values":[9,10,11,12]}]}
            """.trimIndent(),
        )

        requireNotNull(draws)
        assertNull(draws.label)
        assertNull(draws.draws.first().draw)
        assertEquals(12, draws.values.size)
    }

    @Test
    fun `canonical json round trips`() {
        val raw = """
            {"adapter":"dlb-results","label":"Kapruka","ignored":{"a":1},
             "draws":[{"draw":3165,"values":[16,20,31,58],"junk":"x"},
                      {"draw":3164,"values":[3,13,15,21]},
                      {"values":[6,13,41,72]}]}
        """.trimIndent()
        val parsed = requireNotNull(DomDrawPayload.parse(raw))

        val json = DomDrawPayload.toJson(parsed)
        assertEquals(parsed, DomDrawPayload.parse(json))
        assertTrue(json.contains("\"Kapruka\""))
        assertTrue(!json.contains("ignored") && !json.contains("junk"))
    }

    @Test
    fun `an adapter payload normalizes into the analysed sequence`() {
        val raw = """
            {"adapter":"dlb-results","label":"Kapruka",
             "draws":[{"draw":2515,"values":[13,18,25,69]},
                      {"draw":2514,"values":[5,8,19,22]},
                      {"draw":2513,"values":[1,58,63,72]},
                      {"draw":2512,"values":[9,21,44,80]},
                      {"draw":2511,"values":[3,17,33,66]},
                      {"draw":2510,"values":[11,27,55,77]}]}
        """.trimIndent()
        val draws = requireNotNull(DomDrawPayload.parse(raw))

        // The pipeline only ever sees this canonical document, so this is the
        // contract between the HTML adapters and the normalizer.
        val extraction = HeuristicSequenceExtractor().extract(listOf(DomDrawPayload.toJson(draws)))

        assertEquals(draws.values, extraction.values)
        assertEquals(
            listOf(13, 18, 25, 69, 5, 8, 19, 22, 1, 58, 63, 72, 9, 21, 44, 80, 3, 17, 33, 66, 11, 27, 55, 77),
            extraction.values,
        )
    }

    @Test
    fun `scripts target the selectors verified against the live pages`() {
        val dlb = DlbResultAdapter.script
        assertTrue("dlb posts through the bridge", dlb.contains("postDraws"))
        assertTrue("dlb uses the pagination endpoint", dlb.contains("/result/pagination_re"))
        assertTrue("dlb reads the numbers column", dlb.contains("li.res_number"))
        assertTrue("dlb discovers the lottery panel", dlb.contains("tbody[id^=\"pageData\"]"))
        assertTrue("dlb needs the hidden result id", dlb.contains("resultID"))

        val nlb = NlbResultAdapter.script
        assertTrue("nlb posts through the bridge", nlb.contains("postDraws"))
        assertTrue("nlb reads the results table", nlb.contains("table.tbl"))
        assertTrue("nlb reads the number circles", nlb.contains("li[title^=\"Number-\"]"))
        assertTrue("nlb reads the draw number", nlb.contains("td b"))
    }
}
