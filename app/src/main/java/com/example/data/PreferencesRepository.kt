package com.example.data

import android.content.Context
import androidx.datastore.core.DataStore
import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.stringPreferencesKey
import androidx.datastore.preferences.preferencesDataStore
import com.example.model.NarrationStyle
import com.example.model.ScriptLanguage
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map

private val Context.dataStore: DataStore<Preferences> by preferencesDataStore(name = "manhwa_recap_prefs")

data class UserRecapPreferences(
    val language: ScriptLanguage = ScriptLanguage.HINDI,
    val style: NarrationStyle = NarrationStyle.DRAMATIC_RECAP,
    val customPrompt: String = "",
    val seriesTitle: String = ""
)

class PreferencesRepository(private val context: Context) {

    private object Keys {
        val LANGUAGE = stringPreferencesKey("selected_language")
        val STYLE = stringPreferencesKey("selected_style")
        val CUSTOM_PROMPT = stringPreferencesKey("custom_prompt")
        val SERIES_TITLE = stringPreferencesKey("series_title")
    }

    val preferencesFlow: Flow<UserRecapPreferences> = context.dataStore.data.map { prefs ->
        val language = prefs[Keys.LANGUAGE]?.let { name ->
            ScriptLanguage.entries.firstOrNull { it.name == name }
        } ?: ScriptLanguage.HINDI

        val style = prefs[Keys.STYLE]?.let { name ->
            NarrationStyle.entries.firstOrNull { it.name == name }
        } ?: NarrationStyle.DRAMATIC_RECAP

        UserRecapPreferences(
            language = language,
            style = style,
            customPrompt = prefs[Keys.CUSTOM_PROMPT].orEmpty(),
            seriesTitle = prefs[Keys.SERIES_TITLE].orEmpty()
        )
    }

    suspend fun saveLanguage(language: ScriptLanguage) {
        context.dataStore.edit { it[Keys.LANGUAGE] = language.name }
    }

    suspend fun saveStyle(style: NarrationStyle) {
        context.dataStore.edit { it[Keys.STYLE] = style.name }
    }

    suspend fun saveCustomPrompt(prompt: String) {
        context.dataStore.edit { it[Keys.CUSTOM_PROMPT] = prompt }
    }

    suspend fun saveSeriesTitle(title: String) {
        context.dataStore.edit { it[Keys.SERIES_TITLE] = title }
    }
}
