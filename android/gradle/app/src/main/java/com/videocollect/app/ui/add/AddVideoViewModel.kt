package com.videocollect.app.ui.add

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.videocollect.app.api.RetrofitClient
import com.videocollect.app.api.models.CollectRequest
import com.videocollect.app.api.models.VideoRecord
import kotlinx.coroutines.flow.*
import kotlinx.coroutines.launch

data class AddVideoUiState(
    val url: String = "",
    val isLoading: Boolean = false,
    val result: VideoRecord? = null,
    val error: String? = null,
    val isSuccess: Boolean = false
)

class AddVideoViewModel : ViewModel() {

    private val _uiState = MutableStateFlow(AddVideoUiState())
    val uiState: StateFlow<AddVideoUiState> = _uiState.asStateFlow()

    fun updateUrl(url: String) {
        _uiState.update { it.copy(url = url, error = null, result = null, isSuccess = false) }
    }

    fun submit(onSuccess: () -> Unit) {
        val url = _uiState.value.url.trim()
        if (url.isBlank()) {
            _uiState.update { it.copy(error = "请输入 URL") }
            return
        }

        viewModelScope.launch {
            _uiState.update { it.copy(isLoading = true, error = null) }
            try {
                val result = RetrofitClient.getApiService().collectVideo(CollectRequest(url))
                if (result.isSuccess && result.data != null) {
                    _uiState.update {
                        it.copy(isLoading = false, result = result.data, isSuccess = true)
                    }
                    onSuccess()
                } else {
                    _uiState.update { it.copy(isLoading = false, error = result.message) }
                }
            } catch (e: Exception) {
                _uiState.update { it.copy(isLoading = false, error = e.message ?: "收藏失败") }
            }
        }
    }
}
