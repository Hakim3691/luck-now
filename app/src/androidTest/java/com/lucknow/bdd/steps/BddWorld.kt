package com.lucknow.bdd.steps

import com.lucknow.analysis.AnalysisReport
import com.lucknow.llm.ExtractionResult
import com.lucknow.llm.ExtractionSelection
import com.lucknow.scrape.CapturedPayload

/** Per-scenario state shared by the step definition classes. */
object BddWorld {
    var siteUrl: String = ""
    var rawDocument: String = ""
    var payloads: List<CapturedPayload> = emptyList()
    var pageLoaded: Boolean = false
    var scrollSteps: Int = 0
    var extraction: ExtractionResult? = null
    var selection: ExtractionSelection? = null
    var llmPrompts: List<String> = emptyList()
    var sequence: List<Int> = emptyList()
    var report: AnalysisReport? = null

    fun reset() {
        siteUrl = ""
        rawDocument = ""
        payloads = emptyList()
        pageLoaded = false
        scrollSteps = 0
        extraction = null
        selection = null
        llmPrompts = emptyList()
        sequence = emptyList()
        report = null
    }
}
