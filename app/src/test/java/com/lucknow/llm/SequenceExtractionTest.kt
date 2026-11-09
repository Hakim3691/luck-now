package com.lucknow.llm

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Assert.fail
import org.junit.Test

class SequenceExtractionTest {

    @Test
    fun `validates empty output`() {
        assertEquals("output contains no integers", SequenceValidator.validate(emptyList(), null))
    }

    @Test
    fun `validates draw volume mismatch`() {
        val problem = SequenceValidator.validate(listOf(1, 2, 3), expectedDraws = 12)
        assertNotNull(problem)
        assertTrue(problem!!.contains("expected 12 integers"))
    }

    @Test
    fun `accepts a matched draw volume below the generic minimum`() {
        assertNull(SequenceValidator.validate(listOf(4, 9, 11, 22), expectedDraws = 4))
    }

    @Test
    fun `rejects short sequences when no volume is known`() {
        val problem = SequenceValidator.validate(listOf(1, 2, 3), expectedDraws = null)
        assertTrue(problem!!.contains("at least 10"))
    }

    @Test
    fun `rejects implausible values`() {
        val values = (1..20).map { it * 1000 }
        val problem = SequenceValidator.validate(values, expectedDraws = 20)
        assertTrue(problem!!.contains("0..999"))
    }

    @Test
    fun `heuristic extractor finds and reports the draw sequence`() {
        val document = """
            {"draw_history": [
              {"draw_date": "2024-01-02", "numbers": [4, 17, 23, 31, 38, 44]},
              {"draw_date": "2024-01-05", "numbers": [7, 11, 19, 25, 33, 41]}
            ]}
        """.trimIndent()

        val result = HeuristicSequenceExtractor().extract(listOf(document), expectedDraws = 12)

        assertEquals(12, result.values.size)
        assertEquals("deterministic-json", result.engine)
        assertTrue(result.notes.any { it.contains("draw_history") })
    }

    @Test
    fun `heuristic extractor fails loudly on documents without numbers`() {
        try {
            HeuristicSequenceExtractor().extract(listOf("""{"status":"maintenance"}"""))
            fail("Expected ExtractionException")
        } catch (error: ExtractionException) {
            assertTrue(error.message!!.contains("No numeric sequence"))
        }
    }

    @Test
    fun `estimates the draw volume from the document`() {
        val document = """{"draws":[{"n":1},{"n":2},{"n":3},{"n":4},{"n":5},{"n":6},{"n":7},{"n":8},{"n":9},{"n":10}]}"""
        assertEquals(10, DrawVolumeEstimator.estimate(listOf(document)))
    }
}
