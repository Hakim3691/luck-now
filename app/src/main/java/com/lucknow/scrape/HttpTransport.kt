package com.lucknow.scrape

import java.io.IOException
import java.util.concurrent.TimeUnit
import okhttp3.OkHttpClient
import okhttp3.Request

data class TransportResponse(
    val statusCode: Int,
    val body: ByteArray,
    val contentType: String?,
)

/** Transport abstraction so interception can be exercised without a WebView. */
interface HttpTransport {
    fun get(url: String, headers: Map<String, String> = emptyMap(), maxBytes: Int): TransportResponse
}

class OkHttpTransport(
    private val client: OkHttpClient = defaultClient(),
) : HttpTransport {

    override fun get(url: String, headers: Map<String, String>, maxBytes: Int): TransportResponse {
        val builder = Request.Builder().url(url).get()
        headers.forEach { (name, value) ->
            // OkHttp rejects a handful of connection-managed headers; skip them.
            runCatching { builder.addHeader(name, value) }
        }

        client.newCall(builder.build()).execute().use { response ->
            val body = response.body ?: return TransportResponse(response.code, ByteArray(0), null)
            val stream = body.byteStream()
            val bytes = stream.readNBytes(maxBytes + 1)
            if (bytes.size > maxBytes) throw IOException("Payload exceeds ${maxBytes} bytes")
            return TransportResponse(
                statusCode = response.code,
                body = bytes,
                contentType = response.header("Content-Type") ?: body.contentType()?.toString(),
            )
        }
    }

    companion object {
        private const val USER_AGENT =
            "Mozilla/5.0 (Linux; Android 14) AppleWebKit/537.36 (KHTML, like Gecko) " +
                "Chrome/124.0 Mobile Safari/537.36 LuckNow/1.0"

        fun defaultClient(): OkHttpClient = OkHttpClient.Builder()
            .connectTimeout(15, TimeUnit.SECONDS)
            .readTimeout(20, TimeUnit.SECONDS)
            .callTimeout(30, TimeUnit.SECONDS)
            .retryOnConnectionFailure(true)
            .followRedirects(true)
            .followSslRedirects(true)
            .addInterceptor { chain ->
                chain.proceed(
                    chain.request().newBuilder()
                        .header("User-Agent", USER_AGENT)
                        .header("Accept-Language", "en-US,en;q=0.9")
                        .build(),
                )
            }
            .build()
    }
}
