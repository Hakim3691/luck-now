package com.lucknow.llm

class ExtractionException(message: String, cause: Throwable? = null) : Exception(message, cause)

data class ExtractionResult(
    val values: List<Int>,
    val engine: String,
    val attempts: Int,
    val notes: List<String> = emptyList(),
)

/** Converts raw intercepted JSON documents into a 1-D integer sequence. */
interface SequenceExtractionEngine {
    val id: String
    fun extract(documents: List<String>, expectedDraws: Int? = null): ExtractionResult
}

/**
 * Shared acceptance rules for extracted sequences.
 *
 * The PRD requires the parsed array to match the expected draw volume, so a
 * count mismatch is a hard failure that triggers a self-healing retry rather
 * than a silent partial result.
 */
object SequenceValidator {

    const val MIN_VALUES = 10

    fun validate(values: List<Int>, expectedDraws: Int?): String? = when {
        values.isEmpty() -> "output contains no integers"
        expectedDraws != null && expectedDraws > 0 && values.size != expectedDraws ->
            "expected $expectedDraws integers (draw volume) but got ${values.size}"
        // A matched draw volume is proof of a complete parse, so the generic
        // minimum only applies when no volume could be estimated.
        expectedDraws == null && values.size < MIN_VALUES ->
            "expected at least $MIN_VALUES integers but got ${values.size}"
        values.any { it < 0 || it > 999 } -> "values outside the plausible lottery range 0..999"
        else -> null
    }
}
