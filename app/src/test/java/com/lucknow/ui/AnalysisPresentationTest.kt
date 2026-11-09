package com.lucknow.ui

import com.lucknow.analysis.sampleReport
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class AnalysisPresentationTest {

    @Test
    fun `flags a prng flaw when autocorrelation is significant`() {
        val report = sampleReport(flawed = true, acfP = 0.004)

        assertEquals("PRNG Flaw Detected", AnalysisPresentation.headline(report))
        val detail = AnalysisPresentation.detail(report)
        assertTrue(detail.contains("lag 1"))
        assertTrue(detail.contains("< 0.05"))
    }

    @Test
    fun `flags mechanical bias when chi square is significant`() {
        val report = sampleReport(flawed = false, biased = true, chiP = 0.001)

        assertEquals("Mechanical Bias Detected", AnalysisPresentation.headline(report))
        assertTrue(AnalysisPresentation.detail(report).contains("not uniform"))
    }

    @Test
    fun `reports a clean run when nothing is significant`() {
        val report = sampleReport()

        assertEquals("No evidence of bias or PRNG flaws", AnalysisPresentation.headline(report))
    }

    @Test
    fun `prng flaws take precedence over bias in the headline`() {
        val report = sampleReport(flawed = true, biased = true)
        assertEquals("PRNG Flaw Detected", AnalysisPresentation.headline(report))
    }
}
