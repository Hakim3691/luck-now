package com.lucknow.data

import android.content.ContentValues
import android.content.Context
import android.database.sqlite.SQLiteDatabase
import android.database.sqlite.SQLiteOpenHelper
import java.util.UUID
import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

/**
 * SQLite-backed scan store.
 *
 * Raw payloads are kept (bounded) so a run can be re-inspected offline, but the
 * database is excluded from backup: scraped data never leaves the device.
 */
class SqliteScanStore(
    context: Context,
    private val io: CoroutineDispatcher = Dispatchers.IO,
) : ScanStore {

    private val helper = Helper(context.applicationContext)

    override suspend fun create(url: String): ScanRecord = withContext(io) {
        val record = ScanRecord(
            id = UUID.randomUUID().toString(),
            url = url,
            createdAt = System.currentTimeMillis(),
            status = STATUS_INTERCEPTING,
            payloadCount = 0,
            valueCount = 0,
            engine = "",
            verdict = null,
            valuesJson = null,
            reportJson = null,
            error = null,
        )
        helper.writableDatabase.insertOrThrow(TABLE_SCANS, null, record.toValues())
        record
    }

    override suspend fun savePayload(
        scanId: String,
        url: String,
        body: String,
        mimeType: String?,
    ) = withContext(io) {
        val database = helper.writableDatabase
        val truncated = body.take(MAX_PAYLOAD_CHARS)
        database.insert(TABLE_PAYLOADS, null, ContentValues().apply {
            put("scan_id", scanId)
            put("url", url)
            put("mime_type", mimeType)
            put("body", truncated)
            put("captured_at", System.currentTimeMillis())
        })
        // Keep only the most recent payloads per scan to bound storage.
        database.execSQL(
            "DELETE FROM $TABLE_PAYLOADS WHERE scan_id = ? AND id NOT IN " +
                "(SELECT id FROM $TABLE_PAYLOADS WHERE scan_id = ? ORDER BY id DESC LIMIT $MAX_STORED_PAYLOADS)",
            arrayOf(scanId, scanId),
        )
        Unit
    }

    override suspend fun markAwaitingReview(
        scanId: String,
        payloadCount: Int,
        valuesJson: String,
        engine: String,
    ) = withContext(io) {
        helper.writableDatabase.update(
            TABLE_SCANS,
            ContentValues().apply {
                put("status", STATUS_AWAITING_REVIEW)
                put("payload_count", payloadCount)
                put("values_json", valuesJson)
                put("engine", engine)
            },
            "id = ?",
            arrayOf(scanId),
        )
        Unit
    }

    override suspend fun markCompleted(scanId: String, reportJson: String, verdict: String) =
        withContext(io) {
            helper.writableDatabase.update(
                TABLE_SCANS,
                ContentValues().apply {
                    put("status", STATUS_COMPLETED)
                    put("report_json", reportJson)
                    put("verdict", verdict)
                },
                "id = ?",
                arrayOf(scanId),
            )
            Unit
        }

    override suspend fun markFailed(scanId: String, error: String) = withContext(io) {
        helper.writableDatabase.update(
            TABLE_SCANS,
            ContentValues().apply {
                put("status", STATUS_FAILED)
                put("error", error)
            },
            "id = ?",
            arrayOf(scanId),
        )
        Unit
    }

    override suspend fun list(): List<ScanRecord> = withContext(io) {
        helper.readableDatabase
            .query(TABLE_SCANS, null, null, null, null, null, "created_at DESC", "100")
            .use { cursor ->
                buildList {
                    while (cursor.moveToNext()) add(cursor.toRecord())
                }
            }
    }

    override suspend fun get(id: String): ScanRecord? = withContext(io) {
        helper.readableDatabase
            .query(TABLE_SCANS, null, "id = ?", arrayOf(id), null, null, null)
            .use { cursor -> if (cursor.moveToFirst()) cursor.toRecord() else null }
    }

    private fun ScanRecord.toValues() = ContentValues().apply {
        put("id", id)
        put("url", url)
        put("created_at", createdAt)
        put("status", status)
        put("payload_count", payloadCount)
        put("value_count", valueCount)
        put("engine", engine)
        put("verdict", verdict)
        put("values_json", valuesJson)
        put("report_json", reportJson)
        put("error", error)
    }

    private fun android.database.Cursor.toRecord() = ScanRecord(
        id = getString(getColumnIndexOrThrow("id")),
        url = getString(getColumnIndexOrThrow("url")),
        createdAt = getLong(getColumnIndexOrThrow("created_at")),
        status = getString(getColumnIndexOrThrow("status")),
        payloadCount = getInt(getColumnIndexOrThrow("payload_count")),
        valueCount = getInt(getColumnIndexOrThrow("value_count")),
        engine = getString(getColumnIndexOrThrow("engine")),
        verdict = getStringOrNull("verdict"),
        valuesJson = getStringOrNull("values_json"),
        reportJson = getStringOrNull("report_json"),
        error = getStringOrNull("error"),
    )

    private fun android.database.Cursor.getStringOrNull(column: String): String? {
        val index = getColumnIndexOrThrow(column)
        return if (isNull(index)) null else getString(index)
    }

    private class Helper(context: Context) : SQLiteOpenHelper(context, DB_NAME, null, DB_VERSION) {
        override fun onCreate(db: SQLiteDatabase) {
            db.execSQL(
                """
                CREATE TABLE $TABLE_SCANS (
                    id TEXT PRIMARY KEY NOT NULL,
                    url TEXT NOT NULL,
                    created_at INTEGER NOT NULL,
                    status TEXT NOT NULL,
                    payload_count INTEGER NOT NULL DEFAULT 0,
                    value_count INTEGER NOT NULL DEFAULT 0,
                    engine TEXT NOT NULL DEFAULT '',
                    verdict TEXT,
                    values_json TEXT,
                    report_json TEXT,
                    error TEXT
                )
                """.trimIndent(),
            )
            db.execSQL(
                """
                CREATE TABLE $TABLE_PAYLOADS (
                    id INTEGER PRIMARY KEY AUTOINCREMENT,
                    scan_id TEXT NOT NULL,
                    url TEXT NOT NULL,
                    mime_type TEXT,
                    body TEXT NOT NULL,
                    captured_at INTEGER NOT NULL
                )
                """.trimIndent(),
            )
            db.execSQL("CREATE INDEX idx_payloads_scan ON $TABLE_PAYLOADS(scan_id)")
            db.execSQL("CREATE INDEX idx_scans_created ON $TABLE_SCANS(created_at DESC)")
        }

        override fun onUpgrade(db: SQLiteDatabase, oldVersion: Int, newVersion: Int) = Unit
    }

    companion object {
        const val STATUS_INTERCEPTING = "INTERCEPTING"
        const val STATUS_AWAITING_REVIEW = "AWAITING_REVIEW"
        const val STATUS_COMPLETED = "COMPLETED"
        const val STATUS_FAILED = "FAILED"

        private const val DB_NAME = "lucknow.db"
        private const val DB_VERSION = 1
        private const val TABLE_SCANS = "scans"
        private const val TABLE_PAYLOADS = "payloads"
        private const val MAX_STORED_PAYLOADS = 8
        private const val MAX_PAYLOAD_CHARS = 128_000
    }
}
