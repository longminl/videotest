package com.videocollect.app.ui.settings

import android.app.Application
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import com.videocollect.app.api.RetrofitClient
import com.videocollect.app.data.SettingsRepository
import kotlinx.coroutines.flow.*
import kotlinx.coroutines.launch

data class SettingsUiState(
    val host: String = "",
    val port: String = "8080",
    val isLoading: Boolean = false,
    val testResult: String? = null,
    val isSuccess: Boolean = false,
    val isSaving: Boolean = false
)

class SettingsViewModel(application: Application) : AndroidViewModel(application) {

    private val settingsRepo = SettingsRepository(application)
    private val _uiState = MutableStateFlow(SettingsUiState())
    val uiState: StateFlow<SettingsUiState> = _uiState.asStateFlow()

    init {
        viewModelScope.launch {
            val host = settingsRepo.serverHost.first()
            val port = settingsRepo.serverPort.first()
            _uiState.update { it.copy(host = host, port = port.toString()) }
        }
    }

    fun updateHost(host: String) {
        _uiState.update { it.copy(host = host, testResult = null) }
    }

    fun updatePort(port: String) {
        if (port.all { it.isDigit() } || port.isEmpty()) {
            _uiState.update { it.copy(port = port, testResult = null) }
        }
    }

    fun testConnection() {
        val state = _uiState.value
        if (state.host.isBlank() || state.port.isBlank()) {
            _uiState.update { it.copy(testResult = "请填写服务器地址和端口") }
            return
        }
        _uiState.update { it.copy(isLoading = true, testResult = null) }
        RetrofitClient.testConnection(state.host, state.port.toIntOrNull() ?: 8080) { success, msg ->
            _uiState.update {
                it.copy(
                    isLoading = false,
                    testResult = if (success) "✓ 连接成功" else "✗ $msg",
                    isSuccess = success
                )
            }
        }
    }

    fun saveConfig(onSaved: () -> Unit) {
        val state = _uiState.value
        if (state.host.isBlank() || state.port.isBlank()) return
        val port = state.port.toIntOrNull() ?: return
        _uiState.update { it.copy(isSaving = true) }
        viewModelScope.launch {
            settingsRepo.saveServerConfig(state.host, port)
            RetrofitClient.updateBaseUrl(state.host, port)
            _uiState.update { it.copy(isSaving = false) }
            onSaved()
        }
    }
}
