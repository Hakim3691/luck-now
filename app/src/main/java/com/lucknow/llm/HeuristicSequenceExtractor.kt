package com.lucknow.llm

/**
 * Deterministic fallback used when the on-device LLM is unavailable.
 *
 * It is intentionally conservative: it only accepts structurally numeric data
 * and never guesses. The UI always reports which engine produced a sequence so
 * a degraded parse is never mistaken for an LLM parse.
 */
class HeuristicSequenceExtractor(
    private val minValues: Int = SequenceValidator.MIN_VALUES,
) : SequenceExtractionEngine {

    override val id: String = "deterministic-json"

    override fun extract(documents: List<String>, expectedDraws: Int?): ExtractionResult {
        // expectedDraws biases ranking only: the deterministic parser reports what
        // the document actually contains instead of failing on a mismatch.
        val candidates = JsonSequenceScanner.scan(documents)
        if (candidates.isEmpty()) {
            throw ExtractionException("No numeric sequence found in the intercepted JSON")
        }
        val merged = PayloadMerger.merged(documents)
        val single = CandidateRanking.best(candidates, expectedDraws)
        val best = when {
            merged == null -> single ?: throw ExtractionException("No usable numeric sequence found")
            single == null -> merged
            merged.values.size >= single.values.size -> merged
            else -> single
        }
        val problem = SequenceValidator.validate(best.values, expectedDraws = null)
        if (problem != null) throw ExtractionException("Deterministic parse rejected: $problem")

        if (best.values.size < minValues) {
            throw ExtractionException("Sequence too short (${best.values.size} values)")
        }

        return ExtractionResult(
            values = best.values,
            engine = id,
            attempts = 1,
            notes = listOf(
                "path=${best.path}",
                "candidates=${candidates.size}",
                "draws=${best.draws ?: "n/a"}",
            ),
        )
    }
}

/** Estimates how many integers a document contributes, for LLM output validation. */
object DrawVolumeEstimator {

    fun estimate(documents: List<String>): Int? {
        val merged = PayloadMerger.merged(documents)
        val candidates = JsonSequenceScanner.scan(documents)
            .filter { it.values.size >= SequenceValidator.MIN_VALUES }
        val best = CandidateRanking.best(candidates, expectedDraws = null)
        val size = maxOf(merged?.values?.size ?: 0, best?.values?.size ?: 0)
        return size.takeIf { it >= SequenceValidator.MIN_VALUES }
    }
}
