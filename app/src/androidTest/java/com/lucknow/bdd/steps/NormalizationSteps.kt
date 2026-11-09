package com.lucknow.bdd.steps

import android.content.Context
import androidx.test.core.app.ApplicationProvider
import com.lucknow.llm.ExtractionSelector
import com.lucknow.llm.ExtractionSelection
import com.lucknow.llm.LlmEngine
import com.lucknow.llm.ModelManager
import com.lucknow.llm.QwenSequenceExtractor
import io.cucumber.java.en.Given
import io.cucumber.java.en.Then
import io.cucumber.java.en.When
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue

class NormalizationSteps {

    private var scriptedEngine: ScriptedLlmEngine? = null

    private val drawHistory = """
        {"draw_history": [
          {"draw_date": "2024-01-02", "numbers": [4, 17, 23, 31, 38, 44]},
          {"draw_date": "2024-01-05", "numbers": [7, 11, 19, 25, 33, 41]}
        ]}
    """.trimIndent()

    @Given("a raw JSON string containing {string} data")
    fun rawJson(key: String) {
        assertEquals("draw_history", key)
        BddWorld.rawDocument = drawHistory
    }

    @When("the data is passed to the Qwen 0.5B local LLM prompt")
    fun passedToQwen() {
        val engine = ScriptedLlmEngine("[4, 17, 23, 31, 38, 44, 7, 11, 19, 25, 33, 41]")
        scriptedEngine = engine
        BddWorld.extraction = QwenSequenceExtractor(engine).extract(
            documents = listOf(BddWorld.rawDocument),
            expectedDraws = 12,
        )
        BddWorld.llmPrompts = engine.prompts.toList()
    }

    @Then("the LLM should output a strict 1D array of integers")
    fun strictArray() {
        val extraction = requireNotNull(BddWorld.extraction)
        assertEquals(1, extraction.attempts)
        assertTrue(extraction.values.all { it in 0..999 })
        val prompt = BddWorld.llmPrompts.first()
        assertTrue(prompt.contains("Extract dates and drawn numbers"))
        assertTrue(prompt.contains("Output ONLY a comma-separated array of integers"))
    }

    @Then("the array size must match the expected draw volume")
    fun sizeMatchesVolume() {
        assertEquals(12, requireNotNull(BddWorld.extraction).values.size)
    }

    @Given("the Qwen model returns prose on its first attempt")
    fun proseOnFirstAttempt() {
        scriptedEngine = ScriptedLlmEngine(
            "Sure, here are the numbers you asked for.",
            "[4, 17, 23, 31, 38, 44, 7, 11, 19, 25, 33, 41]",
        )
        BddWorld.rawDocument = drawHistory
    }

    @When("the extraction is retried")
    fun extractionRetried() {
        val engine = requireNotNull(scriptedEngine)
        BddWorld.extraction = QwenSequenceExtractor(engine).extract(
            documents = listOf(BddWorld.rawDocument),
            expectedDraws = 12,
        )
        BddWorld.llmPrompts = engine.prompts.toList()
    }

    @Then("the second prompt should contain the validation error")
    fun secondPromptContainsError() {
        assertEquals(2, BddWorld.llmPrompts.size)
        assertFalse(BddWorld.llmPrompts[0].contains("previous answer was rejected"))
        assertTrue(BddWorld.llmPrompts[1].contains("previous answer was rejected"))
    }

    @Then("the final array should be valid")
    fun finalArrayValid() {
        assertEquals(12, requireNotNull(BddWorld.extraction).values.size)
    }

    @Given("no on-device LLM model is installed")
    fun noModelInstalled() {
        BddWorld.selection = null
    }

    @When("the extraction engine is selected")
    fun engineSelected() {
        val context = ApplicationProvider.getApplicationContext<Context>()
        val selector = ExtractionSelector(
            modelManager = ModelManager(context),
            llmFactory = { UnavailableLlmEngine },
        )
        BddWorld.selection = selector.select()
    }

    @Then("the deterministic parser should be used")
    fun deterministicParserUsed() {
        assertEquals("deterministic-json", requireNotNull(BddWorld.selection).engine.id)
    }

    @Then("the result should be flagged as degraded")
    fun flaggedDegraded() {
        val selection: ExtractionSelection = requireNotNull(BddWorld.selection)
        assertTrue(selection.degraded)
    }

    private class ScriptedLlmEngine(vararg responses: String) : LlmEngine {
        private val queue = ArrayDeque(responses.toList())
        val prompts = mutableListOf<String>()

        override val displayName: String = "scripted-qwen"
        override fun isAvailable(): Boolean = true
        override fun close() = Unit

        override fun generate(prompt: String, maxTokens: Int, temperature: Float): String {
            prompts += prompt
            return queue.removeFirstOrNull() ?: ""
        }
    }

    private object UnavailableLlmEngine : LlmEngine {
        override val displayName: String = "unavailable"
        override fun isAvailable(): Boolean = false
        override fun generate(prompt: String, maxTokens: Int, temperature: Float): String = ""
        override fun close() = Unit
    }
}
