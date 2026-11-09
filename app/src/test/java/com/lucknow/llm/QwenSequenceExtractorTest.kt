package com.lucknow.llm

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Assert.fail
import org.junit.Test

class QwenSequenceExtractorTest {

    private val document = """
        {"draw_history": [
          {"numbers": [4, 17, 23, 31, 38, 44]},
          {"numbers": [7, 11, 19, 25, 33, 41]}
        ]}
    """.trimIndent()

    private val validAnswer = "[4, 17, 23, 31, 38, 44, 7, 11, 19, 25, 33, 41]"

    @Test
    fun `returns a strict integer array matching the draw volume`() {
        val engine = ScriptedLlmEngine(validAnswer)
        val result = QwenSequenceExtractor(engine).extract(listOf(document), expectedDraws = 12)

        assertEquals(listOf(4, 17, 23, 31, 38, 44, 7, 11, 19, 25, 33, 41), result.values)
        assertEquals(1, result.attempts)
        assertEquals("qwen2.5-0.5b-instruct", result.engine)
    }

    @Test
    fun `sends the rigid system prompt from the TRD`() {
        val engine = ScriptedLlmEngine(validAnswer)
        QwenSequenceExtractor(engine).extract(listOf(document), expectedDraws = 12)

        val prompt = engine.prompts.single()
        assertTrue(prompt.contains("Extract dates and drawn numbers"))
        assertTrue(prompt.contains("Output ONLY a comma-separated array of integers"))
        assertTrue(prompt.contains("exactly 12 integers"))
        assertTrue(prompt.trimEnd().endsWith("<|im_start|>assistant"))
    }

    @Test
    fun `self heals by injecting the validation error into the next prompt`() {
        val engine = ScriptedLlmEngine(
            "I cannot help with that request.",
            validAnswer,
        )

        val result = QwenSequenceExtractor(engine).extract(listOf(document), expectedDraws = 12)

        assertEquals(2, result.attempts)
        assertEquals(
            "the first prompt carries no rejection context",
            false,
            engine.prompts[0].contains("previous answer was rejected"),
        )
        assertTrue(engine.prompts[1].contains("previous answer was rejected"))
        assertTrue(engine.prompts[1].contains("output contains no integers"))
    }

    @Test
    fun `rejects an answer whose size does not match the expected volume`() {
        val engine = ScriptedLlmEngine("4, 17, 23", "4, 17, 23", "4, 17, 23")
        try {
            QwenSequenceExtractor(engine, maxAttempts = 3).extract(listOf(document), expectedDraws = 12)
            fail("Expected ExtractionException")
        } catch (error: ExtractionException) {
            assertTrue(error.message!!.contains("expected 12 integers"))
            assertEquals(3, engine.prompts.size)
        }
    }

    @Test
    fun `rejects prose output instead of mining digits from it`() {
        val engine = ScriptedLlmEngine("Here are the numbers: 4, 5, 6", "Here are the numbers: 4, 5, 6")
        try {
            QwenSequenceExtractor(engine, maxAttempts = 2).extract(listOf(document), expectedDraws = 3)
            fail("Expected ExtractionException")
        } catch (error: ExtractionException) {
            assertTrue(error.message!!.contains("rejected"))
        }
    }

    private class ScriptedLlmEngine(vararg responses: String) : LlmEngine {
        private val queue = ArrayDeque(responses.toList())
        val prompts = mutableListOf<String>()

        override val displayName: String = "scripted"
        override fun isAvailable(): Boolean = true
        override fun close() = Unit

        override fun generate(prompt: String, maxTokens: Int, temperature: Float): String {
            prompts += prompt
            return queue.removeFirstOrNull() ?: queue.lastOrNull() ?: ""
        }
    }
}
