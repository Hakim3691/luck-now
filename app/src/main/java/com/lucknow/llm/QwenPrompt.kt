package com.lucknow.llm

/**
 * Rigid Qwen2.5-Instruct prompt (ChatML).
 *
 * The system instruction is taken from the TRD. The rejection context is
 * appended on retries so the small 0.5B model can self-heal its JSON output.
 */
object QwenPrompt {

    const val MAX_DOCUMENT_CHARS = 6000

    fun build(document: String, expectedDraws: Int?, previousError: String?): String =
        buildString {
            append("<|im_start|>system\n")
            append("Extract dates and drawn numbers. Output ONLY a comma-separated array of integers. ")
            append("Do not output words, explanations, brackets, dates, or punctuation other than commas. ")
            if (expectedDraws != null && expectedDraws > 0) {
                append("The document contains exactly $expectedDraws drawn numbers; output exactly $expectedDraws integers.")
            }
            append("\n<|im_end|>\n")
            append("<|im_start|>user\n")
            if (previousError != null) {
                append("Your previous answer was rejected: $previousError. Correct it.\n\n")
            }
            append(document.take(MAX_DOCUMENT_CHARS))
            append("\n<|im_end|>\n<|im_start|>assistant\n")
        }
}
