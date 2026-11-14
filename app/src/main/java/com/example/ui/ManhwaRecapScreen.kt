package com.example.ui

import android.content.ClipData
import android.content.ClipboardManager
import android.content.Context
import android.content.Intent
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.PickVisualMediaRequest
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.imePadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.safeDrawing
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Scaffold
import androidx.compose.material3.SnackbarDuration
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.SnackbarResult
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewmodel.compose.viewModel
import com.example.model.ExportFormat
import com.example.ui.components.CustomPromptCard
import com.example.ui.components.GenerateScriptButtonBar
import com.example.ui.components.HeroUploadBanner
import com.example.ui.components.LanguageAndStyleCard
import com.example.ui.components.PanelStoryboardCard
import com.example.ui.components.ScriptOutputCard
import com.example.ui.components.StudioTopHeader
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch

@Composable
fun ManhwaRecapScreen(
    viewModel: ManhwaRecapViewModel = viewModel()
) {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    val snackbarHostState = remember { SnackbarHostState() }

    val panels by viewModel.panels.collectAsStateWithLifecycle()
    val seriesTitle by viewModel.seriesTitle.collectAsStateWithLifecycle()
    val language by viewModel.language.collectAsStateWithLifecycle()
    val narrationStyle by viewModel.narrationStyle.collectAsStateWithLifecycle()
    val customPrompt by viewModel.customPrompt.collectAsStateWithLifecycle()
    val scriptUiState by viewModel.scriptUiState.collectAsStateWithLifecycle()
    val editableScript by viewModel.editableScript.collectAsStateWithLifecycle()
    val isEditingScript by viewModel.isEditingScript.collectAsStateWithLifecycle()
    val isExporting by viewModel.isExporting.collectAsStateWithLifecycle()
    val exportOutcome by viewModel.exportOutcome.collectAsStateWithLifecycle()
    val exportError by viewModel.exportError.collectAsStateWithLifecycle()

    var isCopied by remember { mutableStateOf(false) }

    val multiPhotoPicker = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.PickMultipleVisualMedia(maxItems = 30)
    ) { uris ->
        if (uris.isNotEmpty()) {
            viewModel.addPanels(uris)
        }
    }

    val createTxtLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.CreateDocument("text/plain")
    ) { uri ->
        uri?.let { viewModel.saveTxtToSafUri(it) }
    }

    val createPdfLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.CreateDocument("application/pdf")
    ) { uri ->
        uri?.let { viewModel.savePdfToSafUri(it) }
    }

    LaunchedEffect(Unit) {
        viewModel.legacySafRequest.collect { (format, suggestedName) ->
            when (format) {
                ExportFormat.TXT -> createTxtLauncher.launch(suggestedName)
                ExportFormat.PDF -> createPdfLauncher.launch(suggestedName)
            }
        }
    }

    LaunchedEffect(exportOutcome) {
        val outcome = exportOutcome ?: return@LaunchedEffect
        val actionLabel = if (outcome.uri != null) "Open" else null
        val result = snackbarHostState.showSnackbar(
            message = outcome.message,
            actionLabel = actionLabel,
            duration = SnackbarDuration.Long
        )
        if (result == SnackbarResult.ActionPerformed && outcome.uri != null) {
            val viewIntent = Intent(Intent.ACTION_VIEW).apply {
                setDataAndType(outcome.uri, outcome.mimeType)
                addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
            }
            runCatching {
                context.startActivity(Intent.createChooser(viewIntent, "Open ${outcome.fileName}"))
            }.onFailure {
                snackbarHostState.showSnackbar("Saved in Downloads/ManhwaRecap/${outcome.fileName}")
            }
        }
        viewModel.clearExportOutcome()
    }

    LaunchedEffect(exportError) {
        val err = exportError ?: return@LaunchedEffect
        snackbarHostState.showSnackbar(err)
        viewModel.clearExportError()
    }

    val launchPicker = {
        multiPhotoPicker.launch(
            PickVisualMediaRequest(ActivityResultContracts.PickVisualMedia.ImageOnly)
        )
    }

    val copyScript = {
        val text = editableScript.trim()
        if (text.isNotEmpty()) {
            val clipboard = context.getSystemService(Context.CLIPBOARD_SERVICE) as ClipboardManager
            clipboard.setPrimaryClip(ClipData.newPlainText("Manhwa Recap Script", text))
            isCopied = true
            scope.launch {
                snackbarHostState.showSnackbar("Script copied to clipboard")
                delay(2_000L)
                isCopied = false
            }
        }
    }

    val shareScript = {
        val text = editableScript.trim()
        if (text.isNotEmpty()) {
            val shareIntent = Intent(Intent.ACTION_SEND).apply {
                type = "text/plain"
                putExtra(
                    Intent.EXTRA_SUBJECT,
                    seriesTitle.trim().ifBlank { "Manhwa Recap Script" }
                )
                putExtra(Intent.EXTRA_TEXT, text)
            }
            context.startActivity(Intent.createChooser(shareIntent, "Share Recap Script"))
        }
    }

    Scaffold(
        snackbarHost = { SnackbarHost(hostState = snackbarHostState) },
        contentWindowInsets = WindowInsets.safeDrawing
    ) { innerPadding ->
        BoxWithConstraints(
            modifier = Modifier
                .fillMaxSize()
                .padding(innerPadding)
                .imePadding()
        ) {
            val isExpandedLayout = maxWidth >= 840.dp

            if (isExpandedLayout) {
                Row(
                    modifier = Modifier
                        .fillMaxSize()
                        .padding(horizontal = 24.dp, vertical = 16.dp),
                    horizontalArrangement = Arrangement.spacedBy(20.dp)
                ) {
                    Column(
                        modifier = Modifier
                            .weight(0.48f)
                            .verticalScroll(rememberScrollState()),
                        verticalArrangement = Arrangement.spacedBy(14.dp)
                    ) {
                        StudioTopHeader(
                            panelCount = panels.size,
                            selectedLanguage = language
                        )

                        if (panels.isEmpty()) {
                            HeroUploadBanner(onUploadClick = launchPicker)
                        } else {
                            PanelStoryboardCard(
                                panels = panels,
                                onAddMore = launchPicker,
                                onClearAll = viewModel::clearPanels,
                                onMoveLeft = viewModel::movePanelLeft,
                                onMoveRight = viewModel::movePanelRight,
                                onRemove = viewModel::removePanel
                            )
                        }

                        LanguageAndStyleCard(
                            seriesTitle = seriesTitle,
                            onSeriesTitleChange = viewModel::setSeriesTitle,
                            selectedLanguage = language,
                            onLanguageSelected = viewModel::setLanguage,
                            selectedStyle = narrationStyle,
                            onStyleSelected = viewModel::setNarrationStyle
                        )

                        CustomPromptCard(
                            customPrompt = customPrompt,
                            onCustomPromptChange = viewModel::setCustomPrompt,
                            onApplyPreset = viewModel::appendPromptPreset
                        )

                        GenerateScriptButtonBar(
                            uiState = scriptUiState,
                            hasPanels = panels.isNotEmpty(),
                            hasExistingScript = editableScript.isNotBlank(),
                            onGenerateClick = viewModel::generateRecap,
                            onCancelClick = viewModel::cancelGeneration
                        )

                        Spacer(modifier = Modifier.height(16.dp))
                    }

                    Column(
                        modifier = Modifier
                            .weight(0.52f)
                            .verticalScroll(rememberScrollState()),
                        verticalArrangement = Arrangement.spacedBy(14.dp)
                    ) {
                        ScriptOutputCard(
                            uiState = scriptUiState,
                            scriptText = editableScript,
                            isEditing = isEditingScript,
                            isCopied = isCopied,
                            isExporting = isExporting,
                            onToggleEdit = viewModel::toggleEditMode,
                            onScriptChange = viewModel::updateEditableScript,
                            onCopyClick = copyScript,
                            onDownloadTxtClick = viewModel::downloadTxt,
                            onDownloadPdfClick = viewModel::downloadPdf,
                            onShareClick = shareScript
                        )
                        Spacer(modifier = Modifier.height(16.dp))
                    }
                }
            } else {
                Column(
                    modifier = Modifier
                        .fillMaxSize()
                        .verticalScroll(rememberScrollState())
                        .padding(PaddingValues(horizontal = 16.dp, vertical = 14.dp)),
                    horizontalAlignment = Alignment.CenterHorizontally
                ) {
                    Column(
                        modifier = Modifier
                            .fillMaxWidth()
                            .widthIn(max = 620.dp),
                        verticalArrangement = Arrangement.spacedBy(14.dp)
                    ) {
                        StudioTopHeader(
                            panelCount = panels.size,
                            selectedLanguage = language
                        )

                        if (panels.isEmpty()) {
                            HeroUploadBanner(onUploadClick = launchPicker)
                        } else {
                            PanelStoryboardCard(
                                panels = panels,
                                onAddMore = launchPicker,
                                onClearAll = viewModel::clearPanels,
                                onMoveLeft = viewModel::movePanelLeft,
                                onMoveRight = viewModel::movePanelRight,
                                onRemove = viewModel::removePanel
                            )
                        }

                        LanguageAndStyleCard(
                            seriesTitle = seriesTitle,
                            onSeriesTitleChange = viewModel::setSeriesTitle,
                            selectedLanguage = language,
                            onLanguageSelected = viewModel::setLanguage,
                            selectedStyle = narrationStyle,
                            onStyleSelected = viewModel::setNarrationStyle
                        )

                        CustomPromptCard(
                            customPrompt = customPrompt,
                            onCustomPromptChange = viewModel::setCustomPrompt,
                            onApplyPreset = viewModel::appendPromptPreset
                        )

                        GenerateScriptButtonBar(
                            uiState = scriptUiState,
                            hasPanels = panels.isNotEmpty(),
                            hasExistingScript = editableScript.isNotBlank(),
                            onGenerateClick = viewModel::generateRecap,
                            onCancelClick = viewModel::cancelGeneration
                        )

                        ScriptOutputCard(
                            uiState = scriptUiState,
                            scriptText = editableScript,
                            isEditing = isEditingScript,
                            isCopied = isCopied,
                            isExporting = isExporting,
                            onToggleEdit = viewModel::toggleEditMode,
                            onScriptChange = viewModel::updateEditableScript,
                            onCopyClick = copyScript,
                            onDownloadTxtClick = viewModel::downloadTxt,
                            onDownloadPdfClick = viewModel::downloadPdf,
                            onShareClick = shareScript
                        )

                        Spacer(modifier = Modifier.height(24.dp))
                    }
                }
            }
        }
    }
}
