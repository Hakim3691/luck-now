package com.lucknow.bdd.steps

import com.lucknow.analysis.PythonStatisticalAnalyzer
import com.lucknow.ui.AnalysisPresentation
import io.cucumber.java.en.Given
import io.cucumber.java.en.Then
import io.cucumber.java.en.When
import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertTrue

/** Exercises the real Chaquopy/scipy/statsmodels engine on the device. */
class AnalysisSteps {

    private val analyzer = PythonStatisticalAnalyzer()

    @Given("a normalized array of historical draw integers with a repeating cycle")
    fun repeatingCycle() {
        BddWorld.sequence = List(120) { index -> index % 6 + 1 }
    }

    @Given("a normalized array of historical draw integers that is well mixed")
    fun wellMixed() {
        BddWorld.sequence = mixedSequence(size = 600)
    }

    @Given("a normalized array of historical draw integers that favour one ball")
    fun biasedSequence() {
        BddWorld.sequence = skewedSequence(size = 200)
    }

    @When("the Autocorrelation test is executed")
    fun runAutocorrelation() = analyze()

    @When("the Chi-Square test is executed")
    fun runChiSquare() = analyze()

    @When("the statistical analysis is executed offline")
    fun runOffline() = analyze()

    @Then("the system should calculate the ACF correlation coefficient")
    fun acfCalculated() {
        val autocorrelation = requireNotNull(BddWorld.report).autocorrelation
        assertTrue(autocorrelation.coefficients.size > 2)
        assertEquals(autocorrelation.coefficients.size, autocorrelation.pValues.size)
        assertNotNull(autocorrelation.strongest)
    }

    @Then("the ACF p-value should be below {double}")
    fun acfPValueBelow(threshold: Double) {
        val autocorrelation = requireNotNull(BddWorld.report).autocorrelation
        val significant = autocorrelation.significantLags
        assertTrue("no significant autocorrelation lag found", significant.isNotEmpty())
        assertTrue(
            "ACF p-value ${significant.first().pValue} not below $threshold",
            significant.first().pValue < threshold,
        )
    }

    @Then("the Chi-Square p-value should be below {double}")
    fun chiSquarePValueBelow(threshold: Double) {
        val chiSquare = requireNotNull(BddWorld.report).chiSquare
        assertTrue(
            "chi-square p-value ${chiSquare.pValue} not below $threshold",
            chiSquare.pValue < threshold,
        )
    }

    @Then("the UI should highlight a {string} warning")
    fun uiHighlights(expected: String) {
        assertEquals(expected, AnalysisPresentation.headline(requireNotNull(BddWorld.report)))
    }

    @Then("the result should not flag a PRNG flaw")
    fun noPrngFlaw() {
        assertFalse(requireNotNull(BddWorld.report).hasPrngFlaw)
    }

    @Then("the report should come from the embedded Python engine")
    fun reportFromPython() {
        assertEquals("lucknow-python", requireNotNull(BddWorld.report).engine)
    }

    @Then("the report should include the local scipy and statsmodels versions")
    fun reportIncludesLibraries() {
        val versions = requireNotNull(BddWorld.report).libraryVersions
        assertTrue("scipy version missing", versions.containsKey("scipy"))
        assertTrue("statsmodels version missing", versions.containsKey("statsmodels"))
    }

    private fun analyze() {
        BddWorld.report = runBlocking { analyzer.analyze(BddWorld.sequence) }
    }

    /**
     * Deterministic, cryptographically mixed fixture (SHA-256 of the index), so
     * the "well mixed" scenario is reproducible and genuinely free of serial
     * correlation. Verified host-side in python_tests/test_analysis.py.
     */
    private fun mixedSequence(size: Int): List<Int> {
        val digest = java.security.MessageDigest.getInstance("SHA-256")
        return List(size) { index ->
            val hash = digest.digest("lucknow-$index".toByteArray())
            (((hash[0].toInt() and 0xFF) shl 8) or (hash[1].toInt() and 0xFF)) % 6 + 1
        }
    }

    /**
     * A loaded machine: ball 1 comes up about half the time and the remaining
     * balls share the rest. The order is deterministic yet serially independent
     * (SHA-256 of the index), because a physically biased machine skews the
     * *distribution*, not the *order*: block-ordered draws would also be
     * perfectly autocorrelated, so the engine would report a PRNG flaw and the
     * headline would (correctly) hide the bias finding.
     *
     * Verified host-side against the real engine: chi-square p = 4.1e-44 and
     * the largest |ACF| (0.098) stays under the significance threshold (0.139)
     * for n = 200, so no lag is flagged and the bias headline is shown.
     */
    private fun skewedSequence(size: Int): List<Int> {
        val digest = java.security.MessageDigest.getInstance("SHA-256")
        return List(size) { index ->
            val hash = digest.digest("lucknow-loaded-27-$index".toByteArray())
            val draw = ((hash[0].toInt() and 0xFF) shl 8) or (hash[1].toInt() and 0xFF)
            if (draw < 32_768) 1 else 2 + (draw % 5)
        }
    }
}
