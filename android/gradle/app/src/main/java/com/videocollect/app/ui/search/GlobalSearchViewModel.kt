package com.videocollect.app.ui.search

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.videocollect.app.api.RetrofitClient
import com.videocollect.app.api.models.*
import kotlinx.coroutines.TimeoutCancellationException
import kotlinx.coroutines.flow.*
import kotlinx.coroutines.launch
import kotlinx.coroutines.withTimeout

data class GlobalSearchUiState(
    val keyword: String = "",
    // Sources
    val sources: List<VideoSource> = emptyList(),
    val sourcesLoading: Boolean = false,
    val selectedSourceIds: Set<Long> = emptySet(),
    // Search results
    val isSearching: Boolean = false,
    val searchResults: Map<Long, List<SearchResultItem>> = emptyMap(),
    val searchError: String? = null,
    val searchProgressText: String = "",
    val searchSourceErrors: Map<Long, String> = emptyMap(),
    // Selected result for import
    val selectedSourceId: Long? = null,
    val selectedUrl: String? = null,
    val selectedTitle: String? = null,
    // Episode parsing
    val isParsing: Boolean = false,
    val episodes: List<EpisodeItem> = emptyList(),
    val selectedEpisodes: Set<Int> = emptySet(),
    // Groups
    val groups: List<VideoGroup> = emptyList(),
    val groupsLoading: Boolean = false,
    val selectedGroupId: Long? = null,
    val newGroupName: String = "",
    // Import
    val isImporting: Boolean = false,
    val importResult: BatchImportResult? = null,
    val importError: String? = null,
    // UI step: search, results, episodes, import
    val step: Int = 1  // 1=search, 2=results, 3=episodes, 4=import
)

class GlobalSearchViewModel : ViewModel() {

    private val _uiState = MutableStateFlow(GlobalSearchUiState())
    val uiState: StateFlow<GlobalSearchUiState> = _uiState.asStateFlow()

    init {
        loadSources()
    }

    fun loadSources() {
        viewModelScope.launch {
            _uiState.update { it.copy(sourcesLoading = true) }
            try {
                val result = RetrofitClient.getApiService().getSourceList()
                if (result.isSuccess && result.data != null) {
                    val sources = result.data
                    _uiState.update {
                        it.copy(
                            sources = sources,
                            sourcesLoading = false,
                            selectedSourceIds = sources.mapNotNull { s -> s.id }.toSet()
                        )
                    }
                } else {
                    _uiState.update { it.copy(sourcesLoading = false) }
                }
            } catch (_: Exception) {
                _uiState.update { it.copy(sourcesLoading = false) }
            }
        }
    }

    fun setKeyword(keyword: String) {
        _uiState.update { it.copy(keyword = keyword) }
    }

    fun toggleSource(sourceId: Long) {
        _uiState.update { state ->
            val newSelected = if (sourceId in state.selectedSourceIds) {
                state.selectedSourceIds - sourceId
            } else {
                state.selectedSourceIds + sourceId
            }
            state.copy(selectedSourceIds = newSelected)
        }
    }

    fun toggleAllSources() {
        _uiState.update { state ->
            val allIds = state.sources.mapNotNull { it.id }.toSet()
            if (state.selectedSourceIds.size == allIds.size) {
                state.copy(selectedSourceIds = emptySet())
            } else {
                state.copy(selectedSourceIds = allIds)
            }
        }
    }

    fun search() {
        val keyword = _uiState.value.keyword.trim()
        if (keyword.isEmpty()) return
        val sourceIds = _uiState.value.selectedSourceIds.toList()
        if (sourceIds.isEmpty()) return

        viewModelScope.launch {
            _uiState.update {
                it.copy(
                    isSearching = true, searchError = null,
                    searchResults = emptyMap(), searchSourceErrors = emptyMap(),
                    searchProgressText = "", step = 2
                )
            }

            for ((index, sourceId) in sourceIds.withIndex()) {
                _uiState.update {
                    it.copy(searchProgressText = "正在搜索 (已完成 ${index}/${sourceIds.size})")
                }

                try {
                    val result = withTimeout(20000L) {
                        RetrofitClient.getApiService().searchSourceEpisodes(
                            mapOf("sourceId" to sourceId.toString(), "keyword" to keyword)
                        )
                    }
                    if (result.isSuccess && result.data?.isNotEmpty() == true) {
                        _uiState.update { state ->
                            state.copy(searchResults = state.searchResults + (sourceId to result.data))
                        }
                    }
                } catch (e: TimeoutCancellationException) {
                    _uiState.update { state ->
                        state.copy(searchSourceErrors = state.searchSourceErrors + (sourceId to "超时"))
                    }
                } catch (e: Exception) {
                    _uiState.update { state ->
                        state.copy(searchSourceErrors = state.searchSourceErrors + (sourceId to (e.message ?: "错误")))
                    }
                }
            }

            _uiState.update {
                it.copy(isSearching = false, searchProgressText = "已完成 (${sourceIds.size}/${sourceIds.size})")
            }
        }
    }

    fun selectResult(sourceId: Long, url: String, title: String) {
        _uiState.update {
            it.copy(
                selectedSourceId = sourceId,
                selectedUrl = url,
                selectedTitle = title,
                step = 3
            )
        }
        parseEpisodes(sourceId, url)
    }

    private fun parseEpisodes(sourceId: Long, url: String) {
        viewModelScope.launch {
            _uiState.update { it.copy(isParsing = true, episodes = emptyList(), selectedEpisodes = emptySet()) }
            try {
                val body = mapOf("sourceId" to sourceId.toString(), "seriesUrl" to url)
                val result = RetrofitClient.getApiService().parseEpisodes(body)
                if (result.isSuccess && result.data != null) {
                    _uiState.update {
                        it.copy(
                            episodes = result.data,
                            isParsing = false,
                            selectedEpisodes = result.data.mapNotNull { it.episodeNumber }.toSet()
                        )
                    }
                } else {
                    _uiState.update { it.copy(isParsing = false) }
                }
            } catch (_: Exception) {
                _uiState.update { it.copy(isParsing = false) }
            }
        }
    }

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

    fun goToImport() {
        loadGroups()
        _uiState.update { it.copy(step = 4) }
    }

    fun backToStep2() {
        _uiState.update { it.copy(step = 2, episodes = emptyList()) }
    }

    fun backToStep3() {
        _uiState.update { it.copy(step = 3, importResult = null) }
    }

    fun setSelectedGroupId(groupId: Long?) {
        _uiState.update { it.copy(selectedGroupId = groupId) }
    }

    fun setNewGroupName(name: String) {
        _uiState.update { it.copy(newGroupName = name) }
    }

    fun toggleEpisode(episodeNumber: Int) {
        _uiState.update { state ->
            val newSelected = if (episodeNumber in state.selectedEpisodes) {
                state.selectedEpisodes - episodeNumber
            } else {
                state.selectedEpisodes + episodeNumber
            }
            state.copy(selectedEpisodes = newSelected)
        }
    }

    fun selectAllEpisodes() {
        _uiState.update { state ->
            state.copy(selectedEpisodes = state.episodes.mapNotNull { it.episodeNumber }.toSet())
        }
    }

    fun clearEpisodeSelection() {
        _uiState.update { it.copy(selectedEpisodes = emptySet()) }
    }

    fun doImport() {
        val groupId = _uiState.value.selectedGroupId
        val newName = _uiState.value.newGroupName.trim()
        if (groupId == null && newName.isEmpty()) {
            _uiState.update { it.copy(importError = "请选择或新建合集") }
            return
        }

        val selectedEps = _uiState.value.episodes.filter { it.episodeNumber in _uiState.value.selectedEpisodes }
        if (selectedEps.isEmpty()) {
            _uiState.update { it.copy(importError = "请选择要导入的集数") }
            return
        }

        viewModelScope.launch {
            _uiState.update { it.copy(isImporting = true, importError = null) }

            // If new group, create it first
            var targetGroupId = groupId
            if (targetGroupId == null && newName.isNotEmpty()) {
                try {
                    val createGroup = VideoGroup(
                        id = null,
                        name = newName,
                        sourceId = _uiState.value.selectedSourceId,
                        sourceSeriesUrl = _uiState.value.selectedUrl,
                        totalEpisodes = null,
                        description = null,
                        sortOrder = null,
                        videoCount = null,
                        sourceName = null,
                        createdAt = null,
                        updatedAt = null
                    )
                    val createResult = RetrofitClient.getApiService().createGroup(createGroup)
                    if (createResult.isSuccess && createResult.data?.id != null) {
                        targetGroupId = createResult.data.id
                    } else {
                        _uiState.update { it.copy(isImporting = false, importError = "创建合集失败") }
                        return@launch
                    }
                } catch (e: Exception) {
                    _uiState.update { it.copy(isImporting = false, importError = e.message ?: "创建合集失败") }
                    return@launch
                }
            }

            // Batch import
            try {
                val episodes = selectedEps.map {
                    EpisodeImportItem(episodeNumber = it.episodeNumber ?: 0, url = it.url ?: "")
                }
                val body = mapOf<String, Any>("groupId" to (targetGroupId ?: 0), "episodes" to episodes.map {
                    mapOf("episodeNumber" to it.episodeNumber, "url" to it.url)
                })
                val result = RetrofitClient.getApiService().batchImportEpisodes(body)
                if (result.isSuccess && result.data != null) {
                    _uiState.update { it.copy(isImporting = false, importResult = result.data) }
                } else {
                    _uiState.update { it.copy(isImporting = false, importError = result.message) }
                }
            } catch (e: Exception) {
                _uiState.update { it.copy(isImporting = false, importError = e.message ?: "导入失败") }
            }
        }
    }

    fun reset() {
        _uiState.update { GlobalSearchUiState() }
        loadSources()
    }
}
