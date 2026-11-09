package com.lucknow.llm

data class ExtractionSelection(
    val engine: SequenceExtractionEngine,
    val degraded: Boolean,
    val reason: String,
)

/**
 * Chooses the Qwen LLM when the native runtime and model are both present, and
 * otherwise falls back to the deterministic parser. The choice is surfaced in
 * the UI so results are never misattributed.
 */
class ExtractionSelector(
    private val modelManager: ModelManager,
    private val llmFactory: (ModelManager) -> LlmEngine = { LlamaCppEngine(it.modelFile.absolutePath) },
    private val heuristic: SequenceExtractionEngine = HeuristicSequenceExtractor(),
) {
    fun select(): ExtractionSelection {
        val llm = llmFactory(modelManager)
        return when {
            !llm.isAvailable() -> ExtractionSelection(
                engine = heuristic,
                degraded = true,
                reason = "llama.cpp runtime not bundled in this build",
            )

            !modelManager.isInstalled() -> ExtractionSelection(
                engine = heuristic,
                degraded = true,
                reason = "Qwen2.5-0.5B model not installed",
            )

            else -> ExtractionSelection(
                engine = QwenSequenceExtractor(llm),
                degraded = false,
                reason = "Qwen2.5-0.5B-Instruct via llama.cpp",
            )
        }
    }
}
