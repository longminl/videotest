package com.videocollect.app.data

import android.content.Context
import androidx.datastore.core.DataStore
import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.intPreferencesKey
import androidx.datastore.preferences.core.stringPreferencesKey
import androidx.datastore.preferences.preferencesDataStore
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map

private val Context.dataStore: DataStore<Preferences> by preferencesDataStore(name = "settings")

class SettingsRepository(private val context: Context) {

    companion object {
        private val KEY_HOST = stringPreferencesKey("server_host")
        private val KEY_PORT = intPreferencesKey("server_port")
        private val KEY_CONFIGURED = stringPreferencesKey("server_configured")

        const val DEFAULT_HOST = "192.168.1.100"
        const val DEFAULT_PORT = 8080
    }

    val serverHost: Flow<String> = context.dataStore.data.map { prefs ->
        prefs[KEY_HOST] ?: DEFAULT_HOST
    }

    val serverPort: Flow<Int> = context.dataStore.data.map { prefs ->
        prefs[KEY_PORT] ?: DEFAULT_PORT
    }

    val isConfigured: Flow<Boolean> = context.dataStore.data.map { prefs ->
        prefs[KEY_CONFIGURED] == "true"
    }

    suspend fun saveServerConfig(host: String, port: Int) {
        context.dataStore.edit { prefs ->
            prefs[KEY_HOST] = host
            prefs[KEY_PORT] = port
            prefs[KEY_CONFIGURED] = "true"
        }
    }
}
