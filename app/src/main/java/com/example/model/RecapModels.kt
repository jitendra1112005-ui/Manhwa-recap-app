package com.example.model

import android.net.Uri

enum class ScriptLanguage(
    val displayName: String,
    val shortCode: String
) {
    ENGLISH("English", "EN"),
    HINDI("हिंदी (Hindi)", "HI")
}

enum class NarrationStyle(
    val title: String,
    val description: String,
    val promptGuidance: String
) {
    DRAMATIC_RECAP(
        title = "Dramatic Recap",
        description = "High-energy YouTube narration with tension & hooks",
        promptGuidance = "Deliver a gripping, cinematic YouTube manhwa recap narration with strong emotional hooks, vivid action pacing, and smooth momentum."
    ),
    FAST_SHORTS(
        title = "Fast Shorts",
        description = "Punchy, rapid-fire pacing for Reels & Shorts",
        promptGuidance = "Keep sentences crisp, punchy, and fast-paced for short-form voiceovers while ensuring every line transitions seamlessly into the next."
    ),
    STORYTELLER(
        title = "Storyteller",
        description = "Immersive, detailed narrative flow",
        promptGuidance = "Narrate like an engaging master storyteller, weaving character thoughts, dialogue essence, and visual details into one continuous story."
    ),
    SUSPENSE(
        title = "Dark Suspense",
        description = "Intense, mysterious cliffhanger tone",
        promptGuidance = "Build creeping tension, mystery, and dramatic foreshadowing between scenes, leading smoothly to an intense cliffhanger."
    )
}

data class PanelItem(
    val id: String,
    val uri: Uri
)

sealed interface ScriptUiState {
    data object Idle : ScriptUiState
    data class PreparingImages(val current: Int, val total: Int) : ScriptUiState
    data class Streaming(val text: String) : ScriptUiState
    data class Success(val text: String) : ScriptUiState
    data class Error(val message: String) : ScriptUiState
}

enum class ExportFormat {
    TXT,
    PDF
}

data class ExportOutcome(
    val format: ExportFormat,
    val fileName: String,
    val uri: Uri?,
    val mimeType: String,
    val message: String
)

data class PromptPreset(
    val label: String,
    val instruction: String
)

val DEFAULT_PROMPT_PRESETS = listOf(
    PromptPreset(
        label = "YouTube Hook",
        instruction = "Start with an irresistible 1-line hook and end with a dramatic cliffhanger."
    ),
    PromptPreset(
        label = "Explain Fights",
        instruction = "Highlight attack power, speed, and facial reactions clearly during action beats."
    ),
    PromptPreset(
        label = "Natural Voiceover",
        instruction = "Write clean spoken narration without stage directions, brackets, or timestamps."
    ),
    PromptPreset(
        label = "Deep Emotion",
        instruction = "Emphasize betrayal, inner conflict, and emotional turning points."
    )
)
