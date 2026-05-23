package com.videocollect.app.ui.source

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.videocollect.app.api.RetrofitClient
import com.videocollect.app.api.models.EpisodeItem
import com.videocollect.app.api.models.SearchResultItem
import com.videocollect.app.api.models.VideoSource
import kotlinx.coroutines.flow.*
import kotlinx.coroutines.launch

data class SourceUiState(
    val sources: List<VideoSource> = emptyList(),
    val isLoading: Boolean = false,
    val error: String? = null,
    // Edit mode
    val editingSource: VideoSource? = null,
    val isEditing: Boolean = false,
    val showEditDialog: Boolean = false,
    val editError: String? = null,
    // Test search
    val testSourceId: Long? = null,
    val testKeyword: String = "",
    val isTestingSearch: Boolean = false,
    val testResults: List<SearchResultItem> = emptyList(),
    val testSearchError: String? = null,
    // Test parse
    val testParseUrl: String = "",
    val isTestingParse: Boolean = false,
    val testParseResults: List<EpisodeItem> = emptyList(),
    val testParseError: String? = null,
    // Suggest regex
    val suggestUrl: String = "",
    val suggestResult: Map<String, Any>? = null,
    // Export/Import
    val isExporting: Boolean = false,
    val exportData: List<VideoSource>? = null,
    val importMessage: String? = null
)

class SourceViewModel : ViewModel() {

    private val _uiState = MutableStateFlow(SourceUiState())
    val uiState: StateFlow<SourceUiState> = _uiState.asStateFlow()

    init {
        loadSources()
    }

    fun loadSources() {
        viewModelScope.launch {
            _uiState.update { it.copy(isLoading = true, error = null) }
            try {
                val result = RetrofitClient.getApiService().getSourceList()
                if (result.isSuccess && result.data != null) {
                    _uiState.update { it.copy(sources = result.data, isLoading = false) }
                } else {
                    _uiState.update { it.copy(isLoading = false, error = result.message) }
                }
            } catch (e: Exception) {
                _uiState.update { it.copy(isLoading = false, error = e.message ?: "加载失败") }
            }
        }
    }

    // ===== CRUD =====

    fun openCreate() {
        _uiState.update {
            it.copy(
                editingSource = null,
                isEditing = false,
                showEditDialog = true,
                editError = null
            )
        }
    }

    fun openEdit(source: VideoSource) {
        _uiState.update {
            it.copy(
                editingSource = source,
                isEditing = true,
                showEditDialog = true,
                editError = null
            )
        }
    }

    fun closeEditDialog() {
        _uiState.update { it.copy(showEditDialog = false) }
    }

    fun saveSource(source: VideoSource) {
        if (source.name.isNullOrBlank()) {
            _uiState.update { it.copy(editError = "名称不能为空") }
            return
        }
        if (source.homeUrl.isNullOrBlank()) {
            _uiState.update { it.copy(editError = "首页地址不能为空") }
            return
        }

        viewModelScope.launch {
            try {
                val result = if (_uiState.value.isEditing && source.id != null) {
                    RetrofitClient.getApiService().updateSource(source.id, source)
                } else {
                    RetrofitClient.getApiService().createSource(source)
                }
                if (result.isSuccess) {
                    _uiState.update { it.copy(showEditDialog = false) }
                    loadSources()
                } else {
                    _uiState.update { it.copy(editError = result.message) }
                }
            } catch (e: Exception) {
                _uiState.update { it.copy(editError = e.message ?: "保存失败") }
            }
        }
    }

    fun deleteSource(sourceId: Long) {
        viewModelScope.launch {
            try {
                RetrofitClient.getApiService().deleteSource(sourceId)
                loadSources()
            } catch (e: Exception) {
                _uiState.update { it.copy(error = e.message ?: "删除失败") }
            }
        }
    }

    // ===== Test Search =====

    fun setTestKeyword(keyword: String) {
        _uiState.update { it.copy(testKeyword = keyword) }
    }

    fun testSearch(sourceId: Long) {
        val keyword = _uiState.value.testKeyword.trim()
        if (keyword.isEmpty()) return

        viewModelScope.launch {
            _uiState.update {
                it.copy(testSourceId = sourceId, isTestingSearch = true, testResults = emptyList(), testSearchError = null)
            }
            try {
                val body = mapOf("sourceId" to sourceId.toString(), "keyword" to keyword)
                val result = RetrofitClient.getApiService().testSearch(body)
                if (result.isSuccess && result.data != null) {
                    _uiState.update { it.copy(testResults = result.data, isTestingSearch = false) }
                } else {
                    _uiState.update { it.copy(isTestingSearch = false, testSearchError = result.message) }
                }
            } catch (e: Exception) {
                _uiState.update { it.copy(isTestingSearch = false, testSearchError = e.message ?: "搜索失败") }
            }
        }
    }

    // ===== Test Parse =====

    fun setTestParseUrl(url: String) {
        _uiState.update { it.copy(testParseUrl = url) }
    }

    fun testParse(sourceId: Long) {
        val url = _uiState.value.testParseUrl.trim()
        if (url.isEmpty()) return

        viewModelScope.launch {
            _uiState.update { it.copy(isTestingParse = true, testParseResults = emptyList(), testParseError = null) }
            try {
                val body = mapOf("sourceId" to sourceId.toString(), "seriesUrl" to url)
                val result = RetrofitClient.getApiService().testParse(body)
                if (result.isSuccess && result.data != null) {
                    _uiState.update { it.copy(testParseResults = result.data, isTestingParse = false) }
                } else {
                    _uiState.update { it.copy(isTestingParse = false, testParseError = result.message) }
                }
            } catch (e: Exception) {
                _uiState.update { it.copy(isTestingParse = false, testParseError = e.message ?: "解析失败") }
            }
        }
    }

    // ===== Suggest Regex =====

    fun setSuggestUrl(url: String) {
        _uiState.update { it.copy(suggestUrl = url) }
    }

    fun suggestRegex() {
        val url = _uiState.value.suggestUrl.trim()
        if (url.isEmpty()) return

        viewModelScope.launch {
            try {
                val body = mapOf("seriesUrl" to url)
                val result = RetrofitClient.getApiService().suggestRegex(body)
                if (result.isSuccess && result.data != null) {
                    _uiState.update { it.copy(suggestResult = result.data) }
                } else {
                    _uiState.update { it.copy(suggestResult = null) }
                }
            } catch (_: Exception) {
                _uiState.update { it.copy(suggestResult = null) }
            }
        }
    }

    // ===== Export/Import =====

    fun exportSources() {
        viewModelScope.launch {
            _uiState.update { it.copy(isExporting = true) }
            try {
                val result = RetrofitClient.getApiService().exportSources()
                if (result.isSuccess && result.data != null) {
                    _uiState.update { it.copy(isExporting = false, exportData = result.data) }
                } else {
                    _uiState.update { it.copy(isExporting = false) }
                }
            } catch (_: Exception) {
                _uiState.update { it.copy(isExporting = false) }
            }
        }
    }

    fun importSources(sources: List<VideoSource>) {
        viewModelScope.launch {
            try {
                val result = RetrofitClient.getApiService().importSources(sources)
                _uiState.update { it.copy(importMessage = result.message) }
                loadSources()
            } catch (e: Exception) {
                _uiState.update { it.copy(importMessage = "导入失败: ${e.message}") }
            }
        }
    }

    fun clearImportMessage() {
        _uiState.update { it.copy(importMessage = null) }
    }
}
