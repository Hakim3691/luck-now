package com.lucknow.llm

import org.json.JSONArray
import org.json.JSONObject
import org.json.JSONTokener

/** A numeric sequence candidate discovered inside a JSON document. */
data class SequenceCandidate(
    val path: String,
    val values: List<Int>,
    val draws: Int?,
)

/**
 * Structural scan of arbitrary JSON for integer sequences.
 *
 * Handles the three shapes lottery APIs normally use:
 *  - flat numeric arrays:            `[4, 17, 23, ...]`
 *  - arrays of draw objects:         `[{"draw_date": ..., "number": 17}, ...]`
 *  - arrays of draw objects holding an array of numbers:
 *                                    `[{"draw_date": ..., "numbers": [4, 17, ...]}, ...]`
 */
object JsonSequenceScanner {

    private const val MAX_DEPTH = 12
    private const val MAX_CANDIDATES = 250
    private const val MAX_ARRAY_ITEMS = 2000
    private const val MIN_SCALAR_CANDIDATE = 3

    fun scan(documents: List<String>): List<SequenceCandidate> {
        val candidates = mutableListOf<SequenceCandidate>()
        documents.forEach { document ->
            runCatching {
                val root = JSONTokener(document).nextValue()
                walk(root, "$", 0, candidates)
            }
        }
        return candidates
    }

    private fun walk(node: Any?, path: String, depth: Int, out: MutableList<SequenceCandidate>) {
        if (depth > MAX_DEPTH || out.size >= MAX_CANDIDATES) return
        when (node) {
            is JSONArray -> {
                scalarValues(node)?.takeIf { it.size >= MIN_SCALAR_CANDIDATE }?.let {
                    out += SequenceCandidate(path, it, null)
                }
                objectsIn(node)?.let { objects -> collectFieldCandidates(objects, path, out) }

                val limit = minOf(node.length(), MAX_ARRAY_ITEMS)
                for (index in 0 until limit) {
                    walk(node.opt(index), "$path[$index]", depth + 1, out)
                }
            }

            is JSONObject -> {
                val keys = node.keys()
                while (keys.hasNext()) {
                    val key = keys.next()
                    walk(node.opt(key), "$path.$key", depth + 1, out)
                }
            }

            else -> Unit
        }
    }

    private fun collectFieldCandidates(
        objects: List<JSONObject>,
        path: String,
        out: MutableList<SequenceCandidate>,
    ) {
        val allKeys = objects.flatMap { obj -> obj.keys().asSequence().toList() }.distinct()

        allKeys.forEach { key ->
            if (objects.all { it.has(key) && !it.isNull(key) }) {
                val parsed = objects.map { it.opt(key).asExactInt() }
                if (parsed.isNotEmpty() && parsed.all { it != null }) {
                    out += SequenceCandidate(
                        path = "$path[].$key",
                        values = parsed.filterNotNull(),
                        draws = objects.size,
                    )
                }
            }

            val arrays = objects.map { it.optJSONArray(key) }
            if (arrays.all { it != null }) {
                val flattened = arrays.flatMap { array -> scalarValues(array!!) ?: emptyList() }
                if (flattened.isNotEmpty()) {
                    out += SequenceCandidate("$path[].$key[]", flattened, objects.size)
                }
            }
        }
    }

    private fun objectsIn(array: JSONArray): List<JSONObject>? {
        if (array.length() == 0) return null
        val objects = ArrayList<JSONObject>(array.length())
        for (index in 0 until array.length()) {
            val objectValue = array.optJSONObject(index) ?: return null
            objects += objectValue
        }
        return objects
    }

    private fun scalarValues(array: JSONArray): List<Int>? {
        val values = ArrayList<Int>(array.length())
        for (index in 0 until array.length()) {
            val item = array.opt(index)
            val parsed = when (item) {
                is Number -> item.asExactInt()?.let { listOf(it) }
                is String -> parseNumericString(item)
                else -> null
            } ?: return null
            values += parsed
        }
        return values
    }

    private fun parseNumericString(raw: String): List<Int>? {
        val trimmed = raw.trim()
        if (trimmed.isEmpty()) return null
        trimmed.toIntOrNull()?.let { return listOf(it) }
        // Composite draws such as "4, 17, 23" / "04-17-23" / "2 13 21".
        // Draw numbers are always positive, so separators are simply discarded
        // rather than being mistaken for minus signs.
        val tokens = trimmed.split(NON_DIGIT_PATTERN).filter { it.isNotEmpty() }
        if (tokens.isEmpty()) return null
        if (trimmed.none { it.isDigit() }) return null
        return tokens.map { it.toIntOrNull() ?: return null }
    }

    private fun Any?.asExactInt(): Int? = when (this) {
        is Int -> this
        is Long -> if (this in Int.MIN_VALUE.toLong()..Int.MAX_VALUE.toLong()) toInt() else null
        is Double -> if (this % 1.0 == 0.0 && this in Int.MIN_VALUE.toDouble()..Int.MAX_VALUE.toDouble()) toInt() else null
        is Float -> if (this % 1.0f == 0.0f) toInt() else null
        is Number -> toInt()
        is String -> trim().toIntOrNull()
        else -> null
    }

    private val NON_DIGIT_PATTERN = Regex("[^0-9]+")
}

/** Ranks candidates so the most draw-like sequence wins. */
object CandidateRanking {

    fun score(candidate: SequenceCandidate, expectedDraws: Int?): Long {
        var score = candidate.values.size.toLong()
        if (candidate.values.any { it < 0 || it > 999 }) score /= 4
        if (expectedDraws != null && candidate.draws == expectedDraws) score += 100_000
        if (candidate.path.contains("draw", ignoreCase = true)) score += 2_000
        if (candidate.path.contains("number", ignoreCase = true)) score += 1_000
        if (candidate.path.contains("result", ignoreCase = true)) score += 500
        return score
    }

    fun best(candidates: List<SequenceCandidate>, expectedDraws: Int?): SequenceCandidate? =
        candidates.maxByOrNull { score(it, expectedDraws) }
}
