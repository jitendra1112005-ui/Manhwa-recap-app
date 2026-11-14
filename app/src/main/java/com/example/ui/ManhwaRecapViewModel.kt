package com.example.ui

import android.app.Application
import android.graphics.Bitmap
import android.net.Uri
import android.os.Build
import android.util.Log
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import com.example.data.ImageProcessor
import com.example.data.ManhwaRecapAiRepository
import com.example.data.PreferencesRepository
import com.example.export.ScriptExporter
import com.example.model.ExportFormat
import com.example.model.ExportOutcome
import com.example.model.NarrationStyle
import com.example.model.PanelItem
import com.example.model.ScriptLanguage
import com.example.model.ScriptUiState
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Job
import kotlinx.coroutines.TimeoutCancellationException
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharedFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asSharedFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.launch
import kotlinx.coroutines.withTimeout
import java.util.UUID

private const val TAG = "ManhwaRecapVM"
private const val AI_TIMEOUT_MS = 60_000L

class ManhwaRecapViewModel(application: Application) : AndroidViewModel(application) {

    private val prefsRepo = PreferencesRepository(application.applicationContext)
    private val aiRepo = ManhwaRecapAiRepository()

    private val _panels = MutableStateFlow<List<PanelItem>>(emptyList())
    val panels: StateFlow<List<PanelItem>> = _panels.asStateFlow()

    private val _seriesTitle = MutableStateFlow("")
    val seriesTitle: StateFlow<String> = _seriesTitle.asStateFlow()

    private val _language = MutableStateFlow(ScriptLanguage.HINDI)
    val language: StateFlow<ScriptLanguage> = _language.asStateFlow()

    private val _narrationStyle = MutableStateFlow(NarrationStyle.DRAMATIC_RECAP)
    val narrationStyle: StateFlow<NarrationStyle> = _narrationStyle.asStateFlow()

    private val _customPrompt = MutableStateFlow("")
    val customPrompt: StateFlow<String> = _customPrompt.asStateFlow()

    private val _scriptUiState = MutableStateFlow<ScriptUiState>(ScriptUiState.Idle)
    val scriptUiState: StateFlow<ScriptUiState> = _scriptUiState.asStateFlow()

    private val _editableScript = MutableStateFlow("")
    val editableScript: StateFlow<String> = _editableScript.asStateFlow()

    private val _isEditingScript = MutableStateFlow(false)
    val isEditingScript: StateFlow<Boolean> = _isEditingScript.asStateFlow()

    private val _isExporting = MutableStateFlow(false)
    val isExporting: StateFlow<Boolean> = _isExporting.asStateFlow()

    private val _exportOutcome = MutableStateFlow<ExportOutcome?>(null)
    val exportOutcome: StateFlow<ExportOutcome?> = _exportOutcome.asStateFlow()

    private val _exportError = MutableStateFlow<String?>(null)
    val exportError: StateFlow<String?> = _exportError.asStateFlow()

    private val _legacySafRequest = MutableSharedFlow<Pair<ExportFormat, String>>()
    val legacySafRequest: SharedFlow<Pair<ExportFormat, String>> = _legacySafRequest.asSharedFlow()

    private var generationJob: Job? = null

    init {
        viewModelScope.launch {
            val saved = prefsRepo.preferencesFlow.first()
            _language.value = saved.language
            _narrationStyle.value = saved.style
            _customPrompt.value = saved.customPrompt
            _seriesTitle.value = saved.seriesTitle
        }
    }

    fun addPanels(uris: List<Uri>) {
        if (uris.isEmpty()) return
        val newItems = uris.map { uri ->
            PanelItem(id = UUID.randomUUID().toString(), uri = uri)
        }
        _panels.value = _panels.value + newItems
    }

    fun removePanel(id: String) {
        _panels.value = _panels.value.filterNot { it.id == id }
    }

    fun movePanelLeft(index: Int) {
        val current = _panels.value
        if (index <= 0 || index >= current.size) return
        val mutable = current.toMutableList()
        val item = mutable.removeAt(index)
        mutable.add(index - 1, item)
        _panels.value = mutable
    }

    fun movePanelRight(index: Int) {
        val current = _panels.value
        if (index < 0 || index >= current.size - 1) return
        val mutable = current.toMutableList()
        val item = mutable.removeAt(index)
        mutable.add(index + 1, item)
        _panels.value = mutable
    }

    fun clearPanels() {
        _panels.value = emptyList()
    }

    fun setSeriesTitle(title: String) {
        _seriesTitle.value = title
        viewModelScope.launch {
            prefsRepo.saveSeriesTitle(title)
        }
    }

    fun setLanguage(language: ScriptLanguage) {
        _language.value = language
        viewModelScope.launch {
            prefsRepo.saveLanguage(language)
        }
    }

    fun setNarrationStyle(style: NarrationStyle) {
        _narrationStyle.value = style
        viewModelScope.launch {
            prefsRepo.saveStyle(style)
        }
    }

    fun setCustomPrompt(prompt: String) {
        _customPrompt.value = prompt
        viewModelScope.launch {
            prefsRepo.saveCustomPrompt(prompt)
        }
    }

    fun appendPromptPreset(instruction: String) {
        val current = _customPrompt.value.trim()
        val updated = when {
            current.isEmpty() -> instruction
            current.contains(instruction, ignoreCase = true) -> current
            else -> "$current\n$instruction"
        }
        setCustomPrompt(updated)
    }

    fun toggleEditMode() {
        _isEditingScript.value = !_isEditingScript.value
    }

    fun updateEditableScript(updated: String) {
        _editableScript.value = updated
        if (_scriptUiState.value is ScriptUiState.Success) {
            _scriptUiState.value = ScriptUiState.Success(updated)
        }
    }

    fun generateRecap() {
        val currentPanels = _panels.value
        if (currentPanels.isEmpty()) {
            _scriptUiState.value = ScriptUiState.Error(
                "Please upload at least one manhwa panel image first."
            )
            return
        }

        generationJob?.cancel()
        _isEditingScript.value = false

        generationJob = viewModelScope.launch {
            val context = getApplication<Application>().applicationContext
            val decodedBitmaps = mutableListOf<Bitmap>()

            try {
                val total = currentPanels.size
                currentPanels.forEachIndexed { index, panel ->
                    _scriptUiState.value = ScriptUiState.PreparingImages(index + 1, total)
                    val bitmap = ImageProcessor.decodePanelBitmap(context, panel.uri, total)
                    decodedBitmaps.add(bitmap)
                }

                _scriptUiState.value = ScriptUiState.Streaming("")
                val accumulated = StringBuilder()

                withTimeout(AI_TIMEOUT_MS) {
                    aiRepo.streamConnectedRecapScript(
                        panelBitmaps = decodedBitmaps,
                        language = _language.value,
                        style = _narrationStyle.value,
                        seriesTitle = _seriesTitle.value,
                        customPrompt = _customPrompt.value
                    ).collect { chunk ->
                        accumulated.append(chunk)
                        val textNow = accumulated.toString()
                        _editableScript.value = textNow
                        _scriptUiState.value = ScriptUiState.Streaming(textNow)
                    }
                }

                val finalScript = accumulated.toString().trim()
                if (finalScript.isNotBlank()) {
                    _editableScript.value = finalScript
                    _scriptUiState.value = ScriptUiState.Success(finalScript)
                } else {
                    _scriptUiState.value = ScriptUiState.Error(
                        "The AI returned an empty script. Please try again with clearer panels."
                    )
                }
            } catch (e: TimeoutCancellationException) {
                Log.e(TAG, "Recap generation timed out", e)
                val partial = _editableScript.value.trim()
                if (partial.isNotBlank()) {
                    _scriptUiState.value = ScriptUiState.Success(partial)
                    _exportError.value = "Generation timed out at 60s; partial script kept."
                } else {
                    _scriptUiState.value = ScriptUiState.Error(
                        "Generation timed out after 60 seconds. Try fewer panels at once or retry."
                    )
                }
            } catch (e: CancellationException) {
                throw e
            } catch (e: Exception) {
                Log.e(TAG, "Failed to generate recap script", e)
                _scriptUiState.value = ScriptUiState.Error(
                    e.localizedMessage ?: "Failed to generate recap script. Please check your connection and try again."
                )
            } finally {
                decodedBitmaps.forEach { bitmap ->
                    if (!bitmap.isRecycled) {
                        bitmap.recycle()
                    }
                }
            }
        }
    }

    fun cancelGeneration() {
        generationJob?.cancel()
        generationJob = null
        val partial = _editableScript.value.trim()
        _scriptUiState.value = if (partial.isNotBlank()) {
            ScriptUiState.Success(partial)
        } else {
            ScriptUiState.Idle
        }
    }

    fun downloadTxt() {
        val script = _editableScript.value.trim()
        if (script.isEmpty()) return

        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.Q) {
            viewModelScope.launch {
                val suggested = ScriptExporter.buildSuggestedFileName(_seriesTitle.value, "txt")
                _legacySafRequest.emit(ExportFormat.TXT to suggested)
            }
            return
        }

        viewModelScope.launch {
            _isExporting.value = true
            try {
                val outcome = ScriptExporter.saveTxtToDownloads(
                    context = getApplication<Application>().applicationContext,
                    seriesTitle = _seriesTitle.value,
                    script = script
                )
                _exportOutcome.value = outcome
            } catch (e: CancellationException) {
                throw e
            } catch (e: Exception) {
                Log.e(TAG, "Failed to export TXT", e)
                _exportError.value = e.localizedMessage ?: "Unable to save TXT script."
            } finally {
                _isExporting.value = false
            }
        }
    }

    fun downloadPdf() {
        val script = _editableScript.value.trim()
        if (script.isEmpty()) return

        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.Q) {
            viewModelScope.launch {
                val suggested = ScriptExporter.buildSuggestedFileName(_seriesTitle.value, "pdf")
                _legacySafRequest.emit(ExportFormat.PDF to suggested)
            }
            return
        }

        viewModelScope.launch {
            _isExporting.value = true
            try {
                val outcome = ScriptExporter.savePdfToDownloads(
                    context = getApplication<Application>().applicationContext,
                    seriesTitle = _seriesTitle.value,
                    language = _language.value,
                    panelCount = _panels.value.size,
                    script = script
                )
                _exportOutcome.value = outcome
            } catch (e: CancellationException) {
                throw e
            } catch (e: Exception) {
                Log.e(TAG, "Failed to export PDF", e)
                _exportError.value = e.localizedMessage ?: "Unable to generate PDF."
            } finally {
                _isExporting.value = false
            }
        }
    }

    fun saveTxtToSafUri(uri: Uri) {
        val script = _editableScript.value.trim()
        if (script.isEmpty()) return
        viewModelScope.launch {
            _isExporting.value = true
            try {
                val fileName = ScriptExporter.buildSuggestedFileName(_seriesTitle.value, "txt")
                val outcome = ScriptExporter.writeTxtToUri(
                    context = getApplication<Application>().applicationContext,
                    uri = uri,
                    fileName = fileName,
                    script = script
                )
                _exportOutcome.value = outcome
            } catch (e: CancellationException) {
                throw e
            } catch (e: Exception) {
                Log.e(TAG, "Failed to write TXT to SAF Uri", e)
                _exportError.value = e.localizedMessage ?: "Unable to save TXT script."
            } finally {
                _isExporting.value = false
            }
        }
    }

    fun savePdfToSafUri(uri: Uri) {
        val script = _editableScript.value.trim()
        if (script.isEmpty()) return
        viewModelScope.launch {
            _isExporting.value = true
            try {
                val fileName = ScriptExporter.buildSuggestedFileName(_seriesTitle.value, "pdf")
                val outcome = ScriptExporter.writePdfToUri(
                    context = getApplication<Application>().applicationContext,
                    uri = uri,
                    fileName = fileName,
                    seriesTitle = _seriesTitle.value,
                    language = _language.value,
                    panelCount = _panels.value.size,
                    script = script
                )
                _exportOutcome.value = outcome
            } catch (e: CancellationException) {
                throw e
            } catch (e: Exception) {
                Log.e(TAG, "Failed to write PDF to SAF Uri", e)
                _exportError.value = e.localizedMessage ?: "Unable to save PDF."
            } finally {
                _isExporting.value = false
            }
        }
    }

    fun clearExportOutcome() {
        _exportOutcome.value = null
    }

    fun clearExportError() {
        _exportError.value = null
    }
}
