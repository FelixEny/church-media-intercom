package com.churchmedia.intercom.data

import android.content.Context
import androidx.datastore.preferences.core.booleanPreferencesKey
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.floatPreferencesKey
import androidx.datastore.preferences.core.stringPreferencesKey
import androidx.datastore.preferences.preferencesDataStore
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.map

data class UserSettings(
    val displayName: String = "",
    val noiseSuppression: Boolean = true,
    val volume: Float = 1f,
    val lastHost: String = "",
    val lastCode: String = "",
)

private val Context.settingsDataStore by preferencesDataStore(name = "comms_settings")

class SettingsStore(private val context: Context) {

    private object Keys {
        val displayName = stringPreferencesKey("display_name")
        val noiseSuppression = booleanPreferencesKey("noise_suppression")
        val volume = floatPreferencesKey("volume")
        val lastHost = stringPreferencesKey("last_host")
        val lastCode = stringPreferencesKey("last_code")
    }

    val settings: Flow<UserSettings> = context.settingsDataStore.data.map { prefs ->
        UserSettings(
            displayName = prefs[Keys.displayName].orEmpty(),
            noiseSuppression = prefs[Keys.noiseSuppression] ?: true,
            volume = prefs[Keys.volume] ?: 1f,
            lastHost = prefs[Keys.lastHost].orEmpty(),
            lastCode = prefs[Keys.lastCode].orEmpty(),
        )
    }

    suspend fun current(): UserSettings = settings.first()

    suspend fun saveDisplayName(name: String) {
        context.settingsDataStore.edit { it[Keys.displayName] = name }
    }

    suspend fun saveNoiseSuppression(enabled: Boolean) {
        context.settingsDataStore.edit { it[Keys.noiseSuppression] = enabled }
    }

    suspend fun saveVolume(volume: Float) {
        context.settingsDataStore.edit { it[Keys.volume] = volume }
    }

    suspend fun saveLastSession(host: String, code: String) {
        context.settingsDataStore.edit {
            it[Keys.lastHost] = host
            it[Keys.lastCode] = code
        }
    }
}
