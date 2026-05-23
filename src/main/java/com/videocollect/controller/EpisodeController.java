package com.videocollect.controller;

import com.videocollect.dto.ApiResult;
import com.videocollect.service.EpisodeSearchService;
import com.videocollect.service.EpisodeSearchService.*;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.web.bind.annotation.*;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;

/**
 * 剧集搜索/导入 REST API
 */
@RestController
@RequestMapping("/api/episode")
public class EpisodeController {

    @Autowired
    private EpisodeSearchService episodeSearchService;

    /**
     * 全源搜索（可指定 sourceIds 过滤）
     */
    @PostMapping("/search-all")
    public ApiResult<Map<Long, SourceSearchResult>> searchAll(@RequestBody Map<String, Object> body) {
        String keyword = (String) body.get("keyword");
        if (keyword == null || keyword.trim().isEmpty()) {
            return ApiResult.error("关键词不能为空");
        }

        @SuppressWarnings("unchecked")
        List<Object> rawIds = (List<Object>) body.get("sourceIds");
        List<Long> sourceIds = null;
        if (rawIds != null && !rawIds.isEmpty()) {
            sourceIds = new ArrayList<>();
            for (Object o : rawIds) {
                if (o instanceof Number) {
                    sourceIds.add(((Number) o).longValue());
                }
            }
        }

        Map<Long, SourceSearchResult> results = episodeSearchService.searchAll(keyword.trim(), sourceIds);
        return ApiResult.success(results);
    }

    /**
     * 单源搜索
     */
    @PostMapping("/search-source")
    public ApiResult<List<Map<String, String>>> searchSource(@RequestBody Map<String, String> body) {
        Long sourceId = Long.parseLong(body.get("sourceId"));
        String keyword = body.get("keyword");
        List<Map<String, String>> results = episodeSearchService.searchSource(sourceId, keyword);
        return ApiResult.success(results);
    }

    /**
     * 解析剧集主页集数
     */
    @PostMapping("/parse")
    public ApiResult<List<Map<String, Object>>> parse(@RequestBody Map<String, String> body) {
        Long sourceId = Long.parseLong(body.get("sourceId"));
        String seriesUrl = body.get("seriesUrl");
        List<Map<String, Object>> episodes = episodeSearchService.parseEpisodes(sourceId, seriesUrl);
        return ApiResult.success(episodes);
    }

    /**
     * 批量导入集数
     */
    @PostMapping("/import")
    public ApiResult<BatchImportResult> batchImport(@RequestBody Map<String, Object> body) {
        Long groupId = Long.valueOf(body.get("groupId").toString());

        @SuppressWarnings("unchecked")
        List<Map<String, Object>> episodeMaps = (List<Map<String, Object>>) body.get("episodes");

        List<EpisodeItem> episodes = episodeMaps.stream().map(m -> {
            int epNum = Integer.parseInt(m.get("episodeNumber").toString());
            String url = (String) m.get("url");
            return new EpisodeItem(epNum, url);
        }).collect(Collectors.toList());

        BatchImportResult result = episodeSearchService.batchImport(groupId, episodes);
        if (result.isSuccess()) {
            String msg = "导入完成: 成功 " + result.getSuccessCount()
                    + "，跳过 " + result.getSkippedCount()
                    + "，失败 " + result.getFailCount();
            return ApiResult.success(msg, result);
        }
        return ApiResult.error(result.getMessage());
    }

    /**
     * 检测下一集状态
     */
    @GetMapping("/next/{videoId}")
    public ApiResult<NextEpisodeResult> checkNext(@PathVariable Long videoId) {
        NextEpisodeResult result = episodeSearchService.checkNextEpisode(videoId);
        return ApiResult.success(result);
    }
}
