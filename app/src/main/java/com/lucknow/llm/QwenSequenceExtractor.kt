package com.lucknow.llm

/**
 * LLM-based normalization with self-healing retries.
 *
 * Every rejected answer has its validation error injected into the next prompt,
 * which is what lets a 0.5B model converge on structurally valid output.
 */
class QwenSequenceExtractor(
    private val llm: LlmEngine,
    private val maxAttempts: Int = 3,
) : SequenceExtractionEngine {

    override val id: String = "qwen2.5-0.5b-instruct"

    override fun extract(documents: List<String>, expectedDraws: Int?): ExtractionResult {
        val document = selectDocument(documents)
        if (document.isBlank()) throw ExtractionException("No intercepted JSON to parse")

        var lastError: String? = null
        val notes = mutableListOf<String>()

        for (attempt in 1..maxAttempts) {
            val prompt = QwenPrompt.build(document, expectedDraws, lastError)
            val output = try {
                llm.generate(prompt, maxTokens = MAX_TOKENS, temperature = 0f)
            } catch (error: Exception) {
                throw ExtractionException("On-device LLM failed: ${error.message}", error)
            }

            val values = parseInts(output)
            val problem = SequenceValidator.validate(values, expectedDraws)
            if (problem == null) {
                notes += "prompt_chars=${prompt.length}"
                notes += "context_error=${lastError ?: "none"}"
                return ExtractionResult(values, id, attempt, notes)
            }

            lastError = problem
            notes += "attempt $attempt rejected: $problem"
        }

        throw ExtractionException(
            "Qwen output rejected after $maxAttempts attempts ($lastError)",
        )
    }

    private fun parseInts(output: String): List<Int> {
        val trimmed = output.trim()
        if (trimmed.isEmpty()) return emptyList()
        // The prompt forbids prose; any letter means the model ignored it and the
        // attempt should be retried rather than mined for digits.
        if (trimmed.any { it.isLetter() }) return emptyList()
        return NUMBER_PATTERN.findAll(trimmed).mapNotNull { it.value.toIntOrNull() }.toList()
    }

    private fun selectDocument(documents: List<String>): String {
        val best = documents.maxByOrNull { it.length } ?: return ""
        return best.take(QwenPrompt.MAX_DOCUMENT_CHARS)
    }

    companion object {
        private const val MAX_TOKENS = 512
        private val NUMBER_PATTERN = Regex("-?\\d+")
    }
}
