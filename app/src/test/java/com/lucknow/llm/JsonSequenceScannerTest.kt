package com.lucknow.llm

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class JsonSequenceScannerTest {

    private val drawHistory = """
        {
          "meta": {"page": 1, "total": 3},
          "draw_history": [
            {"draw_date": "2024-01-02", "numbers": [4, 17, 23, 31, 38, 44]},
            {"draw_date": "2024-01-05", "numbers": [7, 11, 19, 25, 33, 41]},
            {"draw_date": "2024-01-09", "numbers": [2, 13, 21, 29, 35, 49]}
          ]
        }
    """.trimIndent()

    @Test
    fun `flattens arrays of draw objects`() {
        val candidates = JsonSequenceScanner.scan(listOf(drawHistory))
        val best = CandidateRanking.best(candidates, expectedDraws = 18)!!
        assertEquals(listOf(4, 17, 23, 31, 38, 44, 7, 11, 19, 25, 33, 41, 2, 13, 21, 29, 35, 49), best.values)
        assertTrue(best.path.contains("draw_history"))
        assertEquals(3, best.draws)
    }

    @Test
    fun `reads nested numeric fields`() {
        val document = """{"winners": [{"ball": 5}, {"ball": 9}, {"ball": 12}]}"""
        val candidates = JsonSequenceScanner.scan(listOf(document))
        val best = CandidateRanking.best(candidates, expectedDraws = 3)!!
        assertEquals(listOf(5, 9, 12), best.values)
    }

    @Test
    fun `parses numeric strings and composite draw strings`() {
        val document = """{"draws": ["04, 17, 23", "07-11-19", "2 13 21"]}"""
        val candidates = JsonSequenceScanner.scan(listOf(document))
        val best = CandidateRanking.best(candidates, expectedDraws = 9)!!
        assertEquals(listOf(4, 17, 23, 7, 11, 19, 2, 13, 21), best.values)
    }

    @Test
    fun `ignores documents without numbers`() {
        val candidates = JsonSequenceScanner.scan(listOf("""{"status": "ok", "message": "no draws"}"""))
        assertTrue(candidates.isEmpty())
    }

    @Test
    fun `survives malformed json`() {
        val candidates = JsonSequenceScanner.scan(listOf("not json at all", "", "<html></html>"))
        assertTrue(candidates.isEmpty())
    }
}
