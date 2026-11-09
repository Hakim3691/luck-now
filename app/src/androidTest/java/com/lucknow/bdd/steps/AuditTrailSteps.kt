package com.lucknow.bdd.steps

import android.content.Context
import androidx.test.core.app.ApplicationProvider
import com.lucknow.data.ScanRecord
import com.lucknow.data.SqliteScanStore
import io.cucumber.java.After
import io.cucumber.java.en.Given
import io.cucumber.java.en.Then
import io.cucumber.java.en.When
import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertTrue

class AuditTrailSteps {

    private val context = ApplicationProvider.getApplicationContext<Context>()
    private lateinit var store: SqliteScanStore
    private lateinit var record: ScanRecord

    @After
    fun clearDatabase() {
        context.deleteDatabase("lucknow.db")
    }

    @Given("the app has finished a scan")
    fun finishedScan() {
        store = SqliteScanStore(context)
        record = runBlocking { store.create("https://example-lottery.com/results") }
    }

    @When("the scan is marked complete with a verdict")
    fun markComplete() {
        runBlocking {
            store.savePayload(record.id, "https://example-lottery.com/api/draws", """{"draws":[1,2,3]}""", "application/json")
            store.markAwaitingReview(record.id, 1, "[1,2,3,4,5,6]", "qwen2.5-0.5b-instruct")
            store.markCompleted(record.id, """{"ok":true}""", "autocorrelated")
        }
    }

    @Then("the audit trail should list the run with its verdict")
    fun listedWithVerdict() {
        val records = runBlocking { store.list() }
        val stored = records.firstOrNull { it.id == record.id }
        assertNotNull("scan missing from audit trail", stored)
        assertEquals("autocorrelated", stored!!.verdict)
        assertEquals("COMPLETED", stored.status)
    }

    @Then("the stored dataset should be readable offline")
    fun datasetReadable() {
        val stored = runBlocking { store.get(record.id) }
        assertEquals("[1,2,3,4,5,6]", stored?.valuesJson)
        assertTrue(stored!!.engine.startsWith("qwen"))
    }
}
