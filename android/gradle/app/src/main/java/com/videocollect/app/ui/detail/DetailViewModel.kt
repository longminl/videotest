package com.videocollect.app.ui.detail

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.videocollect.app.api.RetrofitClient
import com.videocollect.app.api.CacheStatusResponse
import com.videocollect.app.api.models.VideoRecord
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.*
import kotlinx.coroutines.launch

data class DetailUiState(
    val record: VideoRecord? = null,
    val isLoading: Boolean = true,
    val error: String? = null,
    val remarkText: String = "",
    val isSavingRemark: Boolean = false,
    val remarkSaved: Boolean = false,
    val isRechecking: Boolean = false,
    val cacheStatus: CacheStatusResponse? = null,
    val isPollingCache: Boolean = false,
    val isDeleting: Boolean = false
)

class DetailViewModel : ViewModel() {

    private val _uiState = MutableStateFlow(DetailUiState())
    val uiState: StateFlow<DetailUiState> = _uiState.asStateFlow()

    fun load(id: Long) {
        viewModelScope.launch {
            _uiState.update { it.copy(isLoading = true) }
            try {
                val result = RetrofitClient.getApiService().getVideoDetail(id)
                if (result.isSuccess && result.data != null) {
                    _uiState.update {
                        it.copy(
                            record = result.data,
                            isLoading = false,
                            remarkText = result.data.remark ?: ""
                        )
                    }
                } else {
                    _uiState.update { it.copy(isLoading = false, error = result.message) }
                }
            } catch (e: Exception) {
                _uiState.update { it.copy(isLoading = false, error = e.message ?: "加载失败") }
            }
        }
    }

    fun updateRemark(text: String) {
        _uiState.update { it.copy(remarkText = text, remarkSaved = false) }
    }

    fun saveRemark() {
        val id = _uiState.value.record?.id ?: return
        val text = _uiState.value.remarkText
        viewModelScope.launch {
            _uiState.update { it.copy(isSavingRemark = true) }
            try {
                RetrofitClient.getApiService().updateRemark(id, text)
                _uiState.update { it.copy(isSavingRemark = false, remarkSaved = true) }
            } catch (_: Exception) {
                _uiState.update { it.copy(isSavingRemark = false) }
            }
        }
    }

    fun recheck() {
        val id = _uiState.value.record?.id ?: return
        viewModelScope.launch {
            _uiState.update { it.copy(isRechecking = true) }
            try {
                val result = RetrofitClient.getApiService().recheckVideo(id)
                if (result.isSuccess && result.data != null) {
                    _uiState.update { it.copy(record = result.data, isRechecking = false) }
                } else {
                    _uiState.update { it.copy(isRechecking = false) }
                }
            } catch (_: Exception) {
                _uiState.update { it.copy(isRechecking = false) }
            }
        }
    }

    fun startCache() {
        val r = _uiState.value.record ?: return
        viewModelScope.launch {
            try {
                RetrofitClient.getApiService().cacheStart(r.videoUrl ?: "", r.title, r.id)
                pollCacheStatus()
            } catch (_: Exception) {}
        }
    }

    private suspend fun pollCacheStatus() {
        val r = _uiState.value.record ?: return
        _uiState.update { it.copy(isPollingCache = true) }
        for (i in 0 until 60) {
            delay(3000)
            try {
                val result = RetrofitClient.getApiService().cacheStatus(r.videoUrl ?: "", r.title, r.id)
                if (result.isSuccess && result.data != null) {
                    _uiState.update { it.copy(cacheStatus = result.data) }
                    if (result.data.finished == true) {
                        _uiState.update { it.copy(isPollingCache = false) }
                        load(r.id ?: 0)
                        return
                    }
                }
            } catch (_: Exception) {}
        }
        _uiState.update { it.copy(isPollingCache = false) }
    }

    fun delete(onDeleted: () -> Unit) {
        val id = _uiState.value.record?.id ?: return
        viewModelScope.launch {
            _uiState.update { it.copy(isDeleting = true) }
            try {
                RetrofitClient.getApiService().deleteVideo(id)
                onDeleted()
            } catch (_: Exception) {
                _uiState.update { it.copy(isDeleting = false) }
            }
        }
    }
}
