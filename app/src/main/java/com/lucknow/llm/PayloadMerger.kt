package com.lucknow.llm

/**
 * Combines the best sequence from each intercepted payload so paginated or
 * infinite-scroll data becomes one continuous sequence.
 *
 * Payloads only merge when they expose the same field shape and draw count;
 * duplicate draws (the same page fetched twice, or a request-interception and
 * JS-bridge copy of one response) are dropped by chunk signature.
 */
object PayloadMerger {

    fun merged(documents: List<String>): SequenceCandidate? {
        if (documents.isEmpty()) return null

        val perDocument = documents.mapNotNull { document ->
            CandidateRanking.best(JsonSequenceScanner.scan(listOf(document)), expectedDraws = null)
        }
        if (perDocument.isEmpty()) return null

        // Rank across documents so a draw-shaped field wins over a larger,
        // unrelated numeric array on the same page.
        val chosen = CandidateRanking.best(perDocument, expectedDraws = null) ?: return null
        val shape = chosen.path.substringAfterLast('.')
        val compatible = perDocument.filter {
            it.path.substringAfterLast('.') == shape && it.draws == chosen.draws
        }
        if (compatible.size <= 1) return chosen

        val chunkSize = chosen.draws
            ?.takeIf { it > 0 }
            ?.let { draws -> chosen.values.size / draws }
            ?.takeIf { it > 0 }

        val seen = mutableSetOf<String>()
        val values = mutableListOf<Int>()

        compatible.forEach { candidate ->
            if (chunkSize != null) {
                candidate.values.chunked(chunkSize).forEach { chunk ->
                    if (seen.add(chunk.joinToString(","))) values += chunk
                }
            } else if (seen.add(candidate.values.joinToString(","))) {
                values += candidate.values
            }
        }

        val draws = if (chunkSize != null) values.size / chunkSize else chosen.draws
        return chosen.copy(values = values, draws = draws)
    }
}
