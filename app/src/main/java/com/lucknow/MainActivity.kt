package com.lucknow

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.activity.viewModels
import androidx.compose.runtime.getValue
import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.lucknow.analysis.PythonStatisticalAnalyzer
import com.lucknow.data.SqliteScanStore
import com.lucknow.llm.ExtractionSelector
import com.lucknow.llm.ModelManager
import com.lucknow.scrape.WebViewInterceptionDriver
import com.lucknow.ui.LuckNowActions
import com.lucknow.ui.LuckNowApp
import com.lucknow.ui.ScanViewModel
import com.lucknow.ui.theme.LuckNowTheme

class MainActivity : ComponentActivity() {

    private val viewModel: ScanViewModel by viewModels { ScanViewModelFactory(this) }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()

        setContent {
            LuckNowTheme {
                val state by viewModel.state.collectAsStateWithLifecycle()
                LuckNowApp(
                    state = state,
                    actions = LuckNowActions(
                        onUrlChange = viewModel::onUrlChange,
                        onAnalyze = viewModel::startScan,
                        onCancel = viewModel::backToHome,
                        onApprove = viewModel::approveForAnalysis,
                        onBackHome = viewModel::backToHome,
                        onOpenRecord = viewModel::openRecord,
                        onDownloadModel = viewModel::downloadModel,
                        onDismissError = viewModel::dismissError,
                    ),
                )
            }
        }
    }
}

private class ScanViewModelFactory(
    private val activity: ComponentActivity,
) : ViewModelProvider.Factory {

    @Suppress("UNCHECKED_CAST")
    override fun <T : ViewModel> create(modelClass: Class<T>): T {
        val context = activity.applicationContext
        val modelManager = ModelManager(context)
        return ScanViewModel(
            store = SqliteScanStore(context),
            driver = WebViewInterceptionDriver(activity),
            analyzer = PythonStatisticalAnalyzer(),
            selector = ExtractionSelector(modelManager),
            modelManager = modelManager,
        ) as T
    }
}
