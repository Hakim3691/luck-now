package com.lucknow.ui.screens

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.material3.Button
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.unit.dp
import com.lucknow.ui.LuckNowActions
import com.lucknow.ui.ScanUiState
import com.lucknow.ui.components.Banner
import com.lucknow.ui.components.KeyValueRow
import com.lucknow.ui.components.SectionCard
import com.lucknow.ui.theme.Amber
import com.lucknow.ui.theme.MutedText

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun ReviewScreen(state: ScanUiState, actions: LuckNowActions) {
    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text("Review extracted sequence") },
                colors = TopAppBarDefaults.topAppBarColors(
                    containerColor = MaterialTheme.colorScheme.background,
                ),
            )
        },
    ) { padding ->
        LazyColumn(
            modifier = Modifier.fillMaxSize().padding(padding),
            contentPadding = PaddingValues(horizontal = 20.dp, vertical = 8.dp),
            verticalArrangement = Arrangement.spacedBy(14.dp),
        ) {
            item {
                if (state.degraded) {
                    Banner(
                        text = "Deterministic parser used: ${state.extractionReason}",
                        color = Amber,
                    )
                } else {
                    Banner(text = state.extractionReason, color = MaterialTheme.colorScheme.secondary)
                }
            }

            item {
                SectionCard(title = "Extraction") {
                    KeyValueRow("Integers extracted", state.sequence.size.toString())
                    KeyValueRow("Parsing engine", state.extractionEngine.ifBlank { "n/a" })
                    KeyValueRow("Attempts", state.extractionAttempts.toString())
                    KeyValueRow("Expected volume", state.expectedValues?.toString() ?: "n/a")
                    KeyValueRow("Payloads captured", state.payloadCount.toString())
                }
            }

            item {
                SectionCard(title = "Extracted integer sequence") {
                    Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
                        state.sequence.chunked(VALUES_PER_ROW).take(MAX_ROWS).forEach { row ->
                            Row(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                                row.forEach { value ->
                                    Text(
                                        text = value.toString().padStart(3),
                                        style = MaterialTheme.typography.labelMedium,
                                        fontFamily = FontFamily.Monospace,
                                        modifier = Modifier.padding(end = 2.dp),
                                    )
                                }
                            }
                        }
                        if (state.sequence.size > VALUES_PER_ROW * MAX_ROWS) {
                            Text(
                                text = "… ${state.sequence.size - VALUES_PER_ROW * MAX_ROWS} more values not shown",
                                style = MaterialTheme.typography.labelSmall,
                                color = MutedText,
                            )
                        }
                    }
                }
            }

            state.error?.let { message ->
                item { Banner(message, MaterialTheme.colorScheme.error) }
            }

            item {
                Button(
                    onClick = actions.onApprove,
                    modifier = Modifier.fillMaxWidth(),
                    enabled = state.sequence.isNotEmpty() && !state.analyzing,
                ) {
                    if (state.analyzing) {
                        CircularProgressIndicator(modifier = Modifier.padding(end = 10.dp))
                    }
                    Text(if (state.analyzing) "Running statistics…" else "Run statistical analysis")
                }
            }

            item {
                OutlinedButton(onClick = actions.onBackHome, modifier = Modifier.fillMaxWidth()) {
                    Text("Discard and go back")
                }
            }
        }
    }
}

private const val VALUES_PER_ROW = 12
private const val MAX_ROWS = 20
