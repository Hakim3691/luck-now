package com.lucknow.llm

class LlmException(message: String, cause: Throwable? = null) : Exception(message, cause)

/** Text-generation boundary, so the Qwen parsing loop is testable without native code. */
interface LlmEngine {
    val displayName: String
    fun isAvailable(): Boolean
    fun generate(prompt: String, maxTokens: Int = 512, temperature: Float = 0f): String
    fun close()
}

/**
 * JNI surface of the bundled llama.cpp build (`app/src/main/cpp/llama_bridge.cpp`).
 *
 * The library is only present when the app is built with
 * `-Plucknow.withLlama=true` and vendored llama.cpp sources; [isLoaded] makes the
 * rest of the app degrade gracefully when it is absent.
 */
object LlamaNative {
    val isLoaded: Boolean by lazy {
        runCatching { System.loadLibrary("lucknow_llm") }.isSuccess
    }

    external fun loadModel(modelPath: String, contextSize: Int, threads: Int): Long

    external fun generate(handle: Long, prompt: String, maxTokens: Int, temperature: Float): String

    external fun release(handle: Long)
}

/**
 * Qwen2.5-0.5B-Instruct running fully on-device through llama.cpp.
 * Generation is CPU-bound and serialised; the model is loaded on first use.
 */
class LlamaCppEngine(
    private val modelPath: String,
    private val contextSize: Int = 4096,
    private val threads: Int = 4,
) : LlmEngine {

    private var handle: Long = 0L

    override val displayName: String = "Qwen2.5-0.5B-Instruct (llama.cpp)"

    override fun isAvailable(): Boolean = LlamaNative.isLoaded

    @Synchronized
    override fun generate(prompt: String, maxTokens: Int, temperature: Float): String {
        if (!LlamaNative.isLoaded) {
            throw LlmException("Native llama.cpp runtime is not bundled in this build")
        }
        if (handle == 0L) {
            handle = LlamaNative.loadModel(modelPath, contextSize, threads)
            if (handle == 0L) {
                throw LlmException("Failed to load model at $modelPath")
            }
        }
        val output = LlamaNative.generate(handle, prompt, maxTokens, temperature)
        if (output.isBlank()) throw LlmException("Model produced no output")
        return output
    }

    @Synchronized
    override fun close() {
        if (handle != 0L) {
            runCatching { LlamaNative.release(handle) }
            handle = 0L
        }
    }
}
