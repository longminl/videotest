package com.videocollect.app.ui.detail

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.videocollect.app.api.RetrofitClient
import com.videocollect.app.api.CacheStatusResponse
import com.videocollect.app.api.models.VideoGroup
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
    val cacheError: String? = null,
    val isDeleting: Boolean = false,
    // group
    val groups: List<VideoGroup> = emptyList(),
    val groupsLoading: Boolean = false,
    val showMoveGroupDialog: Boolean = false,
    val moveGroupMessage: String? = null,
    // next episode
    val nextEpisodeChecking: Boolean = false,
    val nextEpisodeMessage: String? = null,
    val nextEpisodeAvailable: Boolean = false,
    val nextEpisodeNumber: Int? = null,
    val nextEpisodeUrl: String? = null
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
        if (r.videoUrl.isNullOrBlank()) {
            _uiState.update { it.copy(cacheError = "视频地址为空，无法缓存") }
            return
        }
        viewModelScope.launch {
            _uiState.update { it.copy(cacheError = null, cacheStatus = null) }
            try {
                val result = RetrofitClient.getApiService().cacheStart(r.videoUrl ?: "", r.title, r.id)
                if (result.isSuccess) {
                    pollCacheStatus()
                } else {
                    _uiState.update { it.copy(cacheError = result.message) }
                }
            } catch (e: Exception) {
                _uiState.update { it.copy(cacheError = e.message ?: "启动缓存失败") }
            }
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
                    _uiState.update { it.copy(cacheStatus = result.data, cacheError = null) }
                    if (result.data.finished == true) {
                        _uiState.update { it.copy(isPollingCache = false) }
                        load(r.id ?: 0)
                        return
                    }
                }
            } catch (_: Exception) {}
        }
        _uiState.update { it.copy(isPollingCache = false, cacheError = "缓存超时，请重试") }
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

    // ===== Group =====

    fun loadGroups() {
        viewModelScope.launch {
            _uiState.update { it.copy(groupsLoading = true) }
            try {
                val result = RetrofitClient.getApiService().getGroupList()
                if (result.isSuccess) {
                    _uiState.update { it.copy(groups = result.data ?: emptyList(), groupsLoading = false) }
                } else {
                    _uiState.update { it.copy(groupsLoading = false) }
                }
            } catch (_: Exception) {
                _uiState.update { it.copy(groupsLoading = false) }
            }
        }
    }

    fun showMoveGroupDialog() {
        loadGroups()
        _uiState.update { it.copy(showMoveGroupDialog = true, moveGroupMessage = null) }
    }

    fun dismissMoveGroupDialog() {
        _uiState.update { it.copy(showMoveGroupDialog = false) }
    }

    fun moveToGroup(groupId: Long) {
        val videoId = _uiState.value.record?.id ?: return
        viewModelScope.launch {
            try {
                val body = mapOf("videoId" to videoId, "groupId" to groupId)
                val result = RetrofitClient.getApiService().moveVideoToGroup(body)
                _uiState.update { it.copy(showMoveGroupDialog = false) }
                // Reload to reflect group change
                load(videoId)
            } catch (e: Exception) {
                _uiState.update { it.copy(moveGroupMessage = e.message ?: "移动失败") }
            }
        }
    }

    fun unlinkFromGroup() {
        val videoId = _uiState.value.record?.id ?: return
        viewModelScope.launch {
            try {
                RetrofitClient.getApiService().unlinkVideoFromGroup(videoId)
                load(videoId)
            } catch (_: Exception) {}
        }
    }

    // ===== Next Episode =====

    fun checkNextEpisode() {
        val videoId = _uiState.value.record?.id ?: return
        viewModelScope.launch {
            _uiState.update { it.copy(nextEpisodeChecking = true, nextEpisodeMessage = null) }
            try {
                val result = RetrofitClient.getApiService().checkNextEpisode(videoId)
                if (result.isSuccess && result.data != null) {
                    _uiState.update {
                        it.copy(
                            nextEpisodeChecking = false,
                            nextEpisodeMessage = result.data.message,
                            nextEpisodeAvailable = result.data.available == true,
                            nextEpisodeNumber = result.data.nextEpisodeNumber,
                            nextEpisodeUrl = result.data.nextUrl
                        )
                    }
                } else {
                    _uiState.update { it.copy(nextEpisodeChecking = false, nextEpisodeMessage = result.message) }
                }
            } catch (e: Exception) {
                _uiState.update { it.copy(nextEpisodeChecking = false, nextEpisodeMessage = e.message ?: "检测失败") }
            }
        }
    }

    fun importNextEpisode(onDone: (Long?) -> Unit) {
        val nextUrl = _uiState.value.nextEpisodeUrl ?: return
        val record = _uiState.value.record ?: return
        if (nextUrl.isBlank()) return
        viewModelScope.launch {
            try {
                val title = (record.title ?: "") + " 第" + (_uiState.value.nextEpisodeNumber ?: "") + "集"
                val body = com.videocollect.app.api.models.CollectRequest(url = nextUrl)
                val result = RetrofitClient.getApiService().collectVideo(body)
                if (result.isSuccess && result.data != null) {
                    _uiState.update { it.copy(nextEpisodeMessage = "已导入下一集") }
                    onDone(result.data.id)
                    load(record.id ?: 0)
                } else {
                    _uiState.update { it.copy(nextEpisodeMessage = result.message ?: "导入失败") }
                }
            } catch (e: Exception) {
                _uiState.update { it.copy(nextEpisodeMessage = e.message ?: "导入失败") }
            }
        }
    }
}
