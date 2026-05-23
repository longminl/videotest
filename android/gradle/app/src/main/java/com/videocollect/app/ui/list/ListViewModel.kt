package com.videocollect.app.ui.list

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.videocollect.app.api.RetrofitClient
import com.videocollect.app.api.DeleteBatchRequest
import com.videocollect.app.api.models.VideoGroup
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
    val groupIdFilter: Long? = null,
    val keyword: String = "",
    val selectedIds: Set<Long> = emptySet(),
    val isSelectMode: Boolean = false,
    val error: String? = null,
    val sortBy: String = "title",
    val sortOrder: String = "asc",
    // group
    val groups: List<VideoGroup> = emptyList(),
    val groupsLoading: Boolean = false,
    val groupError: String? = null,
    val showGroupManagementDialog: Boolean = false,
    val groupManagementError: String? = null,
    // batch move
    val showMoveGroupDialog: Boolean = false,
    val moveGroupMessage: String? = null
)

@OptIn(kotlinx.coroutines.FlowPreview::class)
class ListViewModel : ViewModel() {

    private val _uiState = MutableStateFlow(ListUiState())
    val uiState: StateFlow<ListUiState> = _uiState.asStateFlow()

    private val pageSize = 20

    // 防抖自动搜索触发器
    private val _searchTrigger = MutableStateFlow("")

    init {
        loadGroups()
        loadData()

        // 用户停止输入 500ms 后自动搜索
        viewModelScope.launch {
            _searchTrigger
                .debounce(500)
                .collect { loadData() }
        }
    }

    fun loadGroups() {
        viewModelScope.launch {
            _uiState.update { it.copy(groupsLoading = true, groupError = null) }
            try {
                val result = RetrofitClient.getApiService().getGroupList()
                if (result.isSuccess) {
                    _uiState.update { it.copy(groups = result.data ?: emptyList(), groupsLoading = false) }
                } else {
                    _uiState.update { it.copy(groupsLoading = false, groupError = result.message) }
                }
            } catch (e: Exception) {
                _uiState.update { it.copy(groupsLoading = false, groupError = e.message ?: "加载合集失败") }
            }
        }
    }

    fun openGroupManagement() {
        loadGroups()
        _uiState.update { it.copy(showGroupManagementDialog = true, groupManagementError = null) }
    }

    fun dismissGroupManagement() {
        _uiState.update { it.copy(showGroupManagementDialog = false, groupManagementError = null) }
    }

    fun createGroup(name: String) {
        if (name.isBlank()) return
        viewModelScope.launch {
            _uiState.update { it.copy(groupManagementError = null) }
            try {
                val group = VideoGroup(
                    id = null, name = name, sourceId = null,
                    sourceSeriesUrl = null, totalEpisodes = null,
                    description = null, sortOrder = null,
                    videoCount = null, sourceName = null,
                    createdAt = null, updatedAt = null
                )
                val result = RetrofitClient.getApiService().createGroup(group)
                if (result.isSuccess) {
                    loadGroups()
                } else {
                    _uiState.update { it.copy(groupManagementError = result.message) }
                }
            } catch (e: Exception) {
                _uiState.update { it.copy(groupManagementError = e.message ?: "创建失败") }
            }
        }
    }

    fun deleteGroup(id: Long) {
        viewModelScope.launch {
            _uiState.update { it.copy(groupManagementError = null) }
            try {
                val result = RetrofitClient.getApiService().deleteGroup(id, false)
                if (result.isSuccess) {
                    // 如果当前筛选的合集被删除，重置筛选
                    val currentFilter = _uiState.value.groupIdFilter
                    loadGroups()
                    if (currentFilter == id) {
                        _uiState.update { it.copy(groupIdFilter = null) }
                        loadData()
                    }
                } else {
                    _uiState.update { it.copy(groupManagementError = result.message) }
                }
            } catch (e: Exception) {
                _uiState.update { it.copy(groupManagementError = e.message ?: "删除失败") }
            }
        }
    }

    fun renameGroup(id: Long, newName: String) {
        if (newName.isBlank()) return
        val existing = _uiState.value.groups.find { it.id == id } ?: return
        viewModelScope.launch {
            _uiState.update { it.copy(groupManagementError = null) }
            try {
                val group = existing.copy(name = newName)
                val result = RetrofitClient.getApiService().updateGroup(id, group)
                if (result.isSuccess) {
                    loadGroups()
                } else {
                    _uiState.update { it.copy(groupManagementError = result.message) }
                }
            } catch (e: Exception) {
                _uiState.update { it.copy(groupManagementError = e.message ?: "重命名失败") }
            }
        }
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
                    sortOrder = sortOrder,
                    groupId = _uiState.value.groupIdFilter
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
                    sortOrder = sortOrder,
                    groupId = state.groupIdFilter
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

    fun setGroupFilter(groupId: Long?) {
        _uiState.update { it.copy(groupIdFilter = groupId) }
        loadData()
    }

    fun setKeyword(keyword: String) {
        _uiState.update { it.copy(keyword = keyword) }
        _searchTrigger.value = keyword
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

    fun showMoveGroupDialog() {
        _uiState.update { it.copy(showMoveGroupDialog = true, moveGroupMessage = null) }
    }

    fun dismissMoveGroupDialog() {
        _uiState.update { it.copy(showMoveGroupDialog = false, moveGroupMessage = null) }
    }

    fun batchMoveToGroup(groupId: Long) {
        val ids = _uiState.value.selectedIds.toList()
        if (ids.isEmpty()) return
        viewModelScope.launch {
            try {
                val body = mapOf<String, Any>("videoIds" to ids, "groupId" to groupId)
                val result = RetrofitClient.getApiService().batchMoveToGroup(body)
                _uiState.update {
                    it.copy(
                        showMoveGroupDialog = false,
                        isSelectMode = false,
                        selectedIds = emptySet(),
                        moveGroupMessage = result.message
                    )
                }
                loadData()
            } catch (e: Exception) {
                _uiState.update { it.copy(moveGroupMessage = e.message ?: "移动失败") }
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
