package com.example.data

import android.graphics.Bitmap
import com.example.model.NarrationStyle
import com.example.model.ScriptLanguage
import com.google.firebase.Firebase
import com.google.firebase.ai.ai
import com.google.firebase.ai.type.content
import com.google.firebase.ai.type.generationConfig
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.mapNotNull

class ManhwaRecapAiRepository {

    private val model by lazy {
        Firebase.ai.generativeModel(
            modelName = "gemini-3.8-flash",
            generationConfig = generationConfig {
                temperature = 0.75f
                topP = 0.92f
            },
            systemInstruction = content {
                text(
                    """
                    You are an elite Manhwa & Webtoon Recap Scriptwriter for viral story-driven narration videos.
                    Your #1 rule is SEAMLESS NARRATIVE CONTINUITY:
                    - Treat the ordered sequence of manhwa panels as one continuous, unbroken story.
                    - EVERY SINGLE LINE MUST CONNECT NATURALLY TO THE PREVIOUS LINE using smooth cause-and-effect transitions, emotional momentum, and temporal bridges.
                    - NEVER break immersion with labels like "Panel 1:", "Image 2:", "In the first picture", or bulleted image descriptions.
                    - Read all speech bubbles, sound effects, character expressions, injuries, outfits, and visual clues across panels in exact order so the plot, names, and stakes stay 100% consistent from start to finish.
                    - Output pure, voiceover-ready script prose (no markdown bold/asterisks, no headings, no meta commentary) broken only into natural spoken paragraphs.
                    """.trimIndent()
                )
            }
        )
    }

    fun streamConnectedRecapScript(
        panelBitmaps: List<Bitmap>,
        language: ScriptLanguage,
        style: NarrationStyle,
        seriesTitle: String,
        customPrompt: String
    ): Flow<String> {
        val languageInstruction = when (language) {
            ScriptLanguage.HINDI ->
                "Write the ENTIRE script in natural, expressive Hindi using Devanagari script (हिंदी), in the engaging conversational tone of top Indian manhwa recap channels. Ensure every Hindi sentence flows smoothly into the next using natural connectors (जैसे: 'लेकिन तभी...', 'इसके ठीक बाद...', 'पर वो नहीं जानता था कि...', 'देखते ही देखते...')."
            ScriptLanguage.ENGLISH ->
                "Write the ENTIRE script in crisp, gripping English suited for a high-retention manhwa recap voiceover. Ensure every sentence flows seamlessly into the next using natural narrative transitions ('Before he could even breathe...', 'Little did they know...', 'In that exact moment...')."
        }

        val promptContent = content {
            text(
                buildString {
                    appendLine("Create a continuous, tightly connected manhwa recap voiceover script from the following ${panelBitmaps.size} panels in their exact reading order.")
                    if (seriesTitle.isNotBlank()) {
                        appendLine("Series / Chapter context: ${seriesTitle.trim()}")
                    }
                    appendLine("Language requirement: $languageInstruction")
                    appendLine("Narration tone: ${style.title} — ${style.promptGuidance}")
                    if (customPrompt.isNotBlank()) {
                        appendLine("User's custom instructions (follow strictly): ${customPrompt.trim()}")
                    }
                    appendLine("Critical reminder: Every line must link organically with the previous and next line so the script reads as one seamless, uninterrupted story. Do not mention panel numbers or image numbers.")
                }
            )
            panelBitmaps.forEachIndexed { index, bitmap ->
                text("Story Beat ${index + 1} of ${panelBitmaps.size}:")
                image(bitmap)
            }
            text("Now write the seamless, connected recap script from beginning to end:")
        }

        return model.generateContentStream(promptContent)
            .mapNotNull { chunk -> chunk.text }
    }
}
