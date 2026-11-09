package com.lucknow.llm

import android.content.Context
import java.io.File
import java.io.IOException
import java.security.MessageDigest
import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import okhttp3.OkHttpClient
import okhttp3.Request
import com.lucknow.scrape.OkHttpTransport

/**
 * Owns the 380 MB Qwen2.5-0.5B-Instruct Q4_K_M GGUF file.
 *
 * The model is deliberately not baked into the APK: keeping it out of the
 * artifact preserves per-ABI APK sizes and lets Play/enterprise distribution
 * ship a small binary. It is downloaded once, over HTTPS, into app-private
 * storage and verified before use.
 */
class ModelManager(
    context: Context,
    private val client: OkHttpClient = OkHttpTransport.defaultClient(),
    private val io: CoroutineDispatcher = Dispatchers.IO,
) {
    private val directory = File(context.filesDir, "models")
    val modelFile: File = File(directory, FILE_NAME)

    fun isInstalled(): Boolean = modelFile.isFile && modelFile.length() >= MIN_BYTES

    fun installedBytes(): Long = if (modelFile.isFile) modelFile.length() else 0L

    /**
     * Downloads the model to a temporary file and atomically moves it into
     * place. Progress is reported in bytes so the UI can show a real bar.
     */
    suspend fun download(onProgress: (bytesRead: Long, totalBytes: Long) -> Unit = { _, _ -> }): File =
        withContext(io) {
            directory.mkdirs()
            val partial = File(directory, "$FILE_NAME.part")
            if (partial.exists()) partial.delete()

            val request = Request.Builder().url(MODEL_URL).get().build()
            client.newCall(request).execute().use { response ->
                if (!response.isSuccessful) {
                    throw IOException("Model download failed with HTTP ${response.code}")
                }
                val body = response.body ?: throw IOException("Model download returned no body")
                val total = body.contentLength()
                partial.outputStream().use { output ->
                    body.byteStream().use { input ->
                        val buffer = ByteArray(DEFAULT_BUFFER_SIZE)
                        var read: Long = 0
                        while (true) {
                            val count = input.read(buffer)
                            if (count < 0) break
                            output.write(buffer, 0, count)
                            read += count
                            onProgress(read, total)
                        }
                    }
                }
            }

            if (partial.length() < MIN_BYTES) {
                partial.delete()
                throw IOException("Downloaded file is too small to be the Qwen model")
            }
            if (EXPECTED_SHA256.isNotBlank()) {
                val actual = sha256(partial)
                if (!actual.equals(EXPECTED_SHA256, ignoreCase = true)) {
                    partial.delete()
                    throw IOException("Model checksum mismatch (expected $EXPECTED_SHA256, got $actual)")
                }
            }
            if (modelFile.exists()) modelFile.delete()
            if (!partial.renameTo(modelFile)) {
                throw IOException("Unable to move model into place")
            }
            modelFile
        }

    fun delete(): Boolean = modelFile.delete()

    private fun sha256(file: File): String {
        val digest = MessageDigest.getInstance("SHA-256")
        file.inputStream().use { stream ->
            val buffer = ByteArray(DEFAULT_BUFFER_SIZE)
            while (true) {
                val count = stream.read(buffer)
                if (count < 0) break
                digest.update(buffer, 0, count)
            }
        }
        return digest.digest().joinToString("") { "%02x".format(it) }
    }

    companion object {
        const val FILE_NAME = "qwen2.5-0.5b-instruct-q4_k_m.gguf"
        const val MODEL_URL =
            "https://huggingface.co/Qwen/Qwen2.5-0.5B-Instruct-GGUF/resolve/main/" + FILE_NAME
        const val MIN_BYTES = 300L * 1024 * 1024

        /**
         * Pinned SHA-256 of the Q4_K_M artifact. Left blank in source until the
         * release artifact is hashed; when set, downloads are verified before use.
         */
        const val EXPECTED_SHA256 = ""
    }
}
