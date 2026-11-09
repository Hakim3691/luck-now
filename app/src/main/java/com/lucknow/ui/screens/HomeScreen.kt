package com.lucknow.ui.screens

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.CloudDownload
import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp
import com.lucknow.R
import com.lucknow.data.ScanRecord
import com.lucknow.ui.LuckNowActions
import com.lucknow.ui.ScanUiState
import com.lucknow.ui.components.Banner
import com.lucknow.ui.components.SectionCard
import com.lucknow.ui.formatTimestamp
import com.lucknow.ui.theme.Mint
import com.lucknow.ui.theme.MutedText

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun HomeScreen(state: ScanUiState, actions: LuckNowActions) {
    Scaffold(
        topBar = {
            TopAppBar(
                title = {
                    Column {
                        Text(stringResource(R.string.app_name), fontWeight = FontWeight.Bold)
                        Text(
                            text = stringResource(R.string.app_tagline),
                            style = MaterialTheme.typography.labelSmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                        )
                    }
                },
                colors = TopAppBarDefaults.topAppBarColors(
                    containerColor = MaterialTheme.colorScheme.background,
                ),
            )
        },
    ) { padding ->
        LazyColumn(
            modifier = Modifier.fillMaxSize().padding(padding),
            contentPadding = PaddingValues(start = 20.dp, end = 20.dp, top = 8.dp, bottom = 32.dp),
            verticalArrangement = Arrangement.spacedBy(14.dp),
        ) {
            item { ModelCard(state, actions) }
            item { UrlCard(state, actions) }

            state.error?.let { message ->
                item { Banner(message, MaterialTheme.colorScheme.error) }
            }

            item {
                Text(
                    text = stringResource(R.string.home_history).uppercase(),
                    style = MaterialTheme.typography.labelMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
            if (state.history.isEmpty()) {
                item {
                    Text(
                        text = stringResource(R.string.home_history_empty),
                        style = MaterialTheme.typography.bodySmall,
                        color = MutedText,
                    )
                }
            }
            items(state.history, key = { it.id }) { record ->
                HistoryRow(record) { actions.onOpenRecord(record) }
            }

            item {
                Text(
                    text = stringResource(R.string.home_privacy),
                    style = MaterialTheme.typography.labelSmall,
                    color = MutedText,
                    modifier = Modifier.padding(top = 8.dp),
                )
            }
        }
    }
}

@Composable
private fun ModelCard(state: ScanUiState, actions: LuckNowActions) {
    SectionCard(title = "Parsing engine") {
        val ready = state.modelInstalled && state.nativeLlmAvailable
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.spacedBy(10.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Text(
                text = when {
                    ready -> stringResource(R.string.home_model_ready)
                    !state.nativeLlmAvailable -> "Deterministic parser (llama.cpp not in this build)"
                    else -> stringResource(R.string.home_model_missing)
                },
                style = MaterialTheme.typography.bodyMedium,
                fontWeight = FontWeight.Medium,
                color = if (ready) Mint else MaterialTheme.colorScheme.onSurface,
            )
        }

        Text(
            text = when {
                ready -> "Qwen2.5-0.5B-Instruct runs entirely on this device."
                !state.nativeLlmAvailable ->
                    "This APK was built without the native llama.cpp runtime, so JSON is normalized by the deterministic parser instead. Build with -Plucknow.withLlama=true to enable Qwen."
                else -> "Download the ~380 MB Qwen2.5-0.5B-Instruct Q4_K_M model to enable on-device AI parsing."
            },
            style = MaterialTheme.typography.bodySmall,
            color = MutedText,
        )

        val progress = state.modelDownloadProgress
        if (state.nativeLlmAvailable && !state.modelInstalled && progress == null) {
            Button(onClick = actions.onDownloadModel) {
                Icon(Icons.Filled.CloudDownload, contentDescription = null)
                Text(
                    text = stringResource(R.string.home_download_model),
                    modifier = Modifier.padding(start = 8.dp),
                )
            }
        }
        if (progress != null) {
            LinearProgressIndicator(progress = { progress }, modifier = Modifier.fillMaxWidth())
            Text(
                text = "Downloading model… ${(progress * 100).toInt()}%",
                style = MaterialTheme.typography.labelSmall,
                color = MutedText,
            )
        }
        state.modelDownloadError?.let { Banner(it, MaterialTheme.colorScheme.error) }
    }
}

@Composable
private fun UrlCard(state: ScanUiState, actions: LuckNowActions) {
    SectionCard(title = "Target") {
        OutlinedTextField(
            value = state.url,
            onValueChange = actions.onUrlChange,
            modifier = Modifier.fillMaxWidth(),
            label = { Text(stringResource(R.string.home_url_label)) },
            placeholder = { Text(stringResource(R.string.home_url_placeholder)) },
            singleLine = true,
            isError = state.urlError != null,
            supportingText = state.urlError?.let { error -> { Text(error) } },
            keyboardOptions = KeyboardOptions(
                keyboardType = KeyboardType.Uri,
                imeAction = ImeAction.Go,
            ),
            keyboardActions = KeyboardActions(onGo = { actions.onAnalyze() }),
        )
        Button(
            onClick = actions.onAnalyze,
            modifier = Modifier.fillMaxWidth(),
            enabled = state.url.isNotBlank(),
        ) {
            Icon(Icons.Filled.PlayArrow, contentDescription = null)
            Text(
                text = stringResource(R.string.home_analyze),
                modifier = Modifier.padding(start = 8.dp),
            )
        }
    }
}

@Composable
private fun HistoryRow(record: ScanRecord, onClick: () -> Unit) {
    Card(
        modifier = Modifier.fillMaxWidth().clickable(onClick = onClick),
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceVariant),
    ) {
        Column(
            modifier = Modifier.padding(14.dp),
            verticalArrangement = Arrangement.spacedBy(6.dp),
        ) {
            Text(
                text = record.url,
                style = MaterialTheme.typography.bodyMedium,
                maxLines = 1,
            )
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
            ) {
                Text(
                    text = verdictLabel(record),
                    style = MaterialTheme.typography.labelSmall,
                    color = verdictColor(record),
                )
                Text(
                    text = formatTimestamp(record.createdAt),
                    style = MaterialTheme.typography.labelSmall,
                    color = MutedText,
                )
            }
        }
    }
}

@Composable
private fun verdictColor(record: ScanRecord): Color = when (record.verdict) {
    "biased" -> MaterialTheme.colorScheme.error
    "autocorrelated" -> MaterialTheme.colorScheme.secondary
    "no_evidence_of_bias" -> Mint
    else -> MutedText
}

private fun verdictLabel(record: ScanRecord): String = when (record.verdict) {
    "biased" -> "Mechanical bias detected"
    "autocorrelated" -> "PRNG flaw detected"
    "no_evidence_of_bias" -> "No evidence of bias"
    else -> record.status.replace('_', ' ').lowercase().replaceFirstChar { it.uppercase() }
}
