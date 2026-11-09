package com.lucknow.data

/** Immutable view of a persisted scan (the app's audit trail). */
data class ScanRecord(
    val id: String,
    val url: String,
    val createdAt: Long,
    val status: String,
    val payloadCount: Int,
    val valueCount: Int,
    val engine: String,
    val verdict: String?,
    val valuesJson: String?,
    val reportJson: String?,
    val error: String?,
)

/** Persistence boundary, so the pipeline can run against an in-memory store in tests. */
interface ScanStore {
    suspend fun create(url: String): ScanRecord

    suspend fun savePayload(scanId: String, url: String, body: String, mimeType: String?)

    suspend fun markAwaitingReview(
        scanId: String,
        payloadCount: Int,
        valuesJson: String,
        engine: String,
    )

    suspend fun markCompleted(scanId: String, reportJson: String, verdict: String)

    suspend fun markFailed(scanId: String, error: String)

    suspend fun list(): List<ScanRecord>

    suspend fun get(id: String): ScanRecord?
}
