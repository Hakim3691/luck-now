package com.lucknow.ui

import androidx.activity.compose.BackHandler
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import com.lucknow.data.ScanRecord
import com.lucknow.ui.screens.HomeScreen
import com.lucknow.ui.screens.ResultsScreen
import com.lucknow.ui.screens.ReviewScreen
import com.lucknow.ui.screens.ScanningScreen

data class LuckNowActions(
    val onUrlChange: (String) -> Unit = {},
    val onAnalyze: () -> Unit = {},
    val onCancel: () -> Unit = {},
    val onApprove: () -> Unit = {},
    val onBackHome: () -> Unit = {},
    val onOpenRecord: (ScanRecord) -> Unit = {},
    val onDownloadModel: () -> Unit = {},
    val onDismissError: () -> Unit = {},
)

@Composable
fun LuckNowApp(state: ScanUiState, actions: LuckNowActions) {
    BackHandler(enabled = state.screen != Screen.Home) { actions.onBackHome() }

    Surface(modifier = Modifier.fillMaxSize(), color = MaterialTheme.colorScheme.background) {
        when (state.screen) {
            Screen.Home -> HomeScreen(state, actions)
            Screen.Scanning -> ScanningScreen(state, actions)
            Screen.Review -> ReviewScreen(state, actions)
            Screen.Results -> ResultsScreen(state, actions)
        }
    }
}
