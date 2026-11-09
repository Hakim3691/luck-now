package com.lucknow.analysis

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Assert.fail
import org.junit.Test

class AnalysisReportTest {

    @Test
    fun `parses the python engine contract`() {
        val report = sampleReport(flawed = true, biased = false, acfP = 0.004)

        assertEquals("lucknow-python", report.engine)
        assertEquals(120, report.length)
        assertEquals(6, report.distinct)
        assertEquals(6, report.chiSquare.counts.size)
        assertEquals(20.0, report.chiSquare.expectedPerBucket, 0.001)
        assertFalse(report.chiSquare.biased)
        assertTrue(report.hasPrngFlaw)
        assertEquals(1, report.autocorrelation.firstSignificantLag)
        assertEquals(0.62, report.autocorrelation.strongest!!.coefficient, 0.001)
        assertNotNull(report.autocorrelation.ljungBox)
        assertEquals("statsmodels.tsa.stattools.acf", report.autocorrelation.estimator)
        assertEquals("1.8.1", report.libraryVersions["scipy"])
    }

    @Test
    fun `surfaces engine failures with the python message`() {
        val raw = """{"ok": false, "engine": "lucknow-python", "error": {"code": "ANALYSIS_FAILED", "message": "sequence is empty"}}"""
        try {
            AnalysisReport.fromJson(raw)
            fail("Expected AnalysisException")
        } catch (error: AnalysisException) {
            assertEquals("sequence is empty", error.message)
        }
    }

    @Test
    fun `rejects non json responses`() {
        try {
            AnalysisReport.fromJson("<html>oops</html>")
            fail("Expected AnalysisException")
        } catch (error: AnalysisException) {
            assertTrue(error.message!!.contains("invalid JSON"))
        }
    }

    @Test
    fun `extracts error text from failure documents`() {
        val raw = """{"ok": false, "error": {"code": "ANALYSIS_FAILED", "message": "boom"}}"""
        assertEquals("boom", AnalysisReport.errorFromJson(raw))
        assertNull(AnalysisReport.errorFromJson("""{"ok": true}"""))
    }
}
