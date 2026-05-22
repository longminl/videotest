package com.videocollect.app.ui.list

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.videocollect.app.api.RetrofitClient
import com.videocollect.app.api.DeleteBatchRequest
import com.videocollect.app.api.models.VideoRecord
import kotlinx.coroutines.flow.*
import kotlinx.coroutines.launch

data class ListUiState(
    val items: List<VideoRecord> = emptyList(),
    val isLoading: Boolean = false,
    val isRefreshing: Boolean = false,
    val isLoadingMore: Boolean = false,
    val currentPage: Int = 1,
    val hasMore: Boolean = true,
    val statusFilter: Int? = null,
    val keyword: String = "",
    val selectedIds: Set<Long> = emptySet(),
    val isSelectMode: Boolean = false,
    val error: String? = null,
    val sortBy: String = "title",
    val sortOrder: String = "asc"
)

class ListViewModel : ViewModel() {

    private val _uiState = MutableStateFlow(ListUiState())
    val uiState: StateFlow<ListUiState> = _uiState.asStateFlow()

    private val pageSize = 20

    init {
        loadData()
    }

    fun loadData() {
        viewModelScope.launch {
            _uiState.update { it.copy(isLoading = true, error = null, currentPage = 1, hasMore = true) }
            try {
                val sortBy = _uiState.value.sortBy.takeIf { it.isNotBlank() }
                val sortOrder = _uiState.value.sortOrder.takeIf { it.isNotBlank() }
                val result = RetrofitClient.getApiService().getVideoList(
                    page = 1,
                    pageSize = pageSize,
                    status = _uiState.value.statusFilter,
                    keyword = _uiState.value.keyword.ifBlank { null },
                    sortBy = sortBy,
                    sortOrder = sortOrder
                )
                if (result.isSuccess && result.data != null) {
                    val items = result.data.list
                    _uiState.update {
                        it.copy(
                            items = items,
                            isLoading = false,
                            isRefreshing = false,
                            currentPage = 1,
                            hasMore = items.size >= pageSize
                        )
                    }
                } else {
                    _uiState.update { it.copy(isLoading = false, isRefreshing = false, error = result.message) }
                }
            } catch (e: Exception) {
                _uiState.update { it.copy(isLoading = false, isRefreshing = false, error = e.message ?: "加载失败") }
            }
        }
    }

    fun refresh() {
        _uiState.update { it.copy(isRefreshing = true) }
        loadData()
    }

    fun loadMore() {
        val state = _uiState.value
        if (state.isLoadingMore || !state.hasMore) return
        viewModelScope.launch {
            _uiState.update { it.copy(isLoadingMore = true) }
            val nextPage = state.currentPage + 1
            try {
                val sortBy = state.sortBy.takeIf { it.isNotBlank() }
                val sortOrder = state.sortOrder.takeIf { it.isNotBlank() }
                val result = RetrofitClient.getApiService().getVideoList(
                    page = nextPage,
                    pageSize = pageSize,
                    status = state.statusFilter,
                    keyword = state.keyword.ifBlank { null },
                    sortBy = sortBy,
                    sortOrder = sortOrder
                )
                if (result.isSuccess && result.data != null) {
                    val newItems = state.items + result.data.list
                    _uiState.update {
                        it.copy(
                            items = newItems,
                            isLoadingMore = false,
                            currentPage = nextPage,
                            hasMore = result.data.list.size >= pageSize
                        )
                    }
                } else {
                    _uiState.update { it.copy(isLoadingMore = false) }
                }
            } catch (e: Exception) {
                _uiState.update { it.copy(isLoadingMore = false) }
            }
        }
    }

    fun setStatusFilter(status: Int?) {
        _uiState.update { it.copy(statusFilter = status) }
        loadData()
    }

    fun setKeyword(keyword: String) {
        _uiState.update { it.copy(keyword = keyword) }
    }

    fun search() {
        loadData()
    }

    fun toggleSelectMode() {
        _uiState.update { it.copy(isSelectMode = !it.isSelectMode, selectedIds = emptySet()) }
    }

    fun toggleSelection(id: Long) {
        _uiState.update { state ->
            val newSelected = if (id in state.selectedIds) {
                state.selectedIds - id
            } else {
                state.selectedIds + id
            }
            state.copy(selectedIds = newSelected)
        }
    }

    fun selectAll() {
        _uiState.update { state ->
            state.copy(selectedIds = state.items.mapNotNull { it.id }.toSet())
        }
    }

    fun deselectAll() {
        _uiState.update { it.copy(selectedIds = emptySet()) }
    }

    fun deleteSelected(onDone: () -> Unit) {
        val ids = _uiState.value.selectedIds.toList()
        if (ids.isEmpty()) return
        viewModelScope.launch {
            try {
                RetrofitClient.getApiService().deleteBatch(DeleteBatchRequest(ids))
                _uiState.update { it.copy(isSelectMode = false, selectedIds = emptySet()) }
                loadData()
                onDone()
            } catch (e: Exception) {
                _uiState.update { it.copy(error = e.message ?: "删除失败") }
            }
        }
    }

    fun startCache(id: Long, videoUrl: String, title: String?) {
        viewModelScope.launch {
            try {
                RetrofitClient.getApiService().cacheStart(videoUrl, title, id)
            } catch (_: Exception) {}
        }
    }

    fun checkCacheStatus(id: Long, videoUrl: String, title: String?, onResult: (Boolean, Int, Int) -> Unit) {
        viewModelScope.launch {
            try {
                val result = RetrofitClient.getApiService().cacheStatus(videoUrl, title, id)
                if (result.isSuccess && result.data != null) {
                    onResult(result.data.finished == true, result.data.total ?: 0, result.data.cached ?: 0)
                }
            } catch (_: Exception) {}
        }
    }

    fun setSortBy(sortBy: String) {
        _uiState.update { it.copy(sortBy = sortBy) }
        loadData()
    }

    fun setSortOrder(sortOrder: String) {
        _uiState.update { it.copy(sortOrder = sortOrder) }
        loadData()
    }

    fun cycleSort() {
        val s = _uiState.value
        val next = when (s.sortBy to s.sortOrder) {
            "created_at" to "desc" -> "created_at" to "asc"
            "created_at" to "asc" -> "title" to "asc"
            "title" to "asc" -> "title" to "desc"
            else -> "created_at" to "desc"
        }
        _uiState.update { it.copy(sortBy = next.first, sortOrder = next.second) }
        loadData()
    }

    fun sortLabel(): String = when (_uiState.value.sortBy to _uiState.value.sortOrder) {
        "created_at" to "desc" -> "最新"
        "created_at" to "asc" -> "最早"
        "title" to "asc" -> "标题A-Z"
        "title" to "desc" -> "标题Z-A"
        else -> "排序"
    }
}
