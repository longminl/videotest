package com.videocollect.controller;

import com.videocollect.dto.ApiResult;
import com.videocollect.model.VideoSource;
import com.videocollect.service.VideoSourceService;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.web.bind.annotation.*;

import java.util.List;
import java.util.Map;

/**
 * 视频源管理 REST API
 */
@RestController
@RequestMapping("/api/source")
public class VideoSourceController {

    @Autowired
    private VideoSourceService videoSourceService;

    /**
     * 获取所有视频源
     */
    @GetMapping("/list")
    public ApiResult<List<VideoSource>> list() {
        return ApiResult.success(videoSourceService.findAll());
    }

    /**
     * 获取单个视频源
     */
    @GetMapping("/{id}")
    public ApiResult<VideoSource> get(@PathVariable Long id) {
        VideoSource source = videoSourceService.findById(id);
        if (source == null) return ApiResult.error("视频源不存在");
        return ApiResult.success(source);
    }

    /**
     * 新增视频源
     */
    @PostMapping
    public ApiResult<VideoSource> create(@RequestBody VideoSource source) {
        if (source.getName() == null || source.getName().trim().isEmpty()) {
            return ApiResult.error("视频源名称不能为空");
        }
        VideoSource saved = videoSourceService.save(source);
        return ApiResult.success("视频源已创建", saved);
    }

    /**
     * 编辑视频源
     */
    @PutMapping("/{id}")
    public ApiResult<VideoSource> update(@PathVariable Long id, @RequestBody VideoSource source) {
        source.setId(id);
        VideoSource saved = videoSourceService.save(source);
        return ApiResult.success("视频源已更新", saved);
    }

    /**
     * 删除视频源
     */
    @DeleteMapping("/{id}")
    public ApiResult<?> delete(@PathVariable Long id) {
        videoSourceService.delete(id);
        return ApiResult.success("视频源已删除");
    }

    /**
     * 导出所有视频源（JSON 数组）
     */
    @GetMapping("/export")
    public ApiResult<List<VideoSource>> exportAll() {
        return ApiResult.success(videoSourceService.findAll());
    }

    /**
     * 导入视频源
     * 接收 JSON 数组，逐条插入（自动分配新 ID）
     */
    @PostMapping("/import")
    public ApiResult<?> importSources(@RequestBody List<VideoSource> sources) {
        if (sources == null || sources.isEmpty()) {
            return ApiResult.error("导入数据不能为空");
        }
        int count = videoSourceService.importSources(sources);
        return ApiResult.success("成功导入 " + count + " 条视频源");
    }

    /**
     * 测试搜索
     */
    @PostMapping("/test-search")
    public ApiResult<List<Map<String, String>>> testSearch(@RequestBody Map<String, String> body) {
        Long sourceId = Long.parseLong(body.get("sourceId"));
        String keyword = body.get("keyword");
        VideoSource source = videoSourceService.findById(sourceId);
        if (source == null) return ApiResult.error("视频源不存在");

        List<Map<String, String>> results = videoSourceService.testSearch(source, keyword);
        return ApiResult.success("搜索完成，共 " + results.size() + " 条结果", results);
    }

    /**
     * 测试集数解析（首页测试时用）
     */
    @PostMapping("/test-search-no-id")
    public ApiResult<List<Map<String, String>>> testSearchInline(@RequestBody Map<String, Object> body) {
        String keyword = (String) body.get("keyword");
        VideoSource source = new VideoSource();
        source.setHomeUrl((String) body.get("homeUrl"));
        source.setSearchUrl((String) body.get("searchUrl"));
        source.setSearchDataPath((String) body.getOrDefault("searchDataPath", "$"));
        source.setSearchTitleField((String) body.getOrDefault("titleField", "title"));
        source.setSearchUrlField((String) body.getOrDefault("urlField", "url"));
        source.setSearchCoverField((String) body.get("coverField"));

        List<Map<String, String>> results = videoSourceService.testSearch(source, keyword);
        return ApiResult.success("搜索完成，共 " + results.size() + " 条结果", results);
    }

    /**
     * 测试集数解析
     */
    @PostMapping("/test-parse")
    public ApiResult<List<Map<String, Object>>> testParse(@RequestBody Map<String, String> body) {
        Long sourceId = Long.parseLong(body.get("sourceId"));
        String seriesUrl = body.get("seriesUrl");
        VideoSource source = videoSourceService.findById(sourceId);
        if (source == null) return ApiResult.error("视频源不存在");

        List<Map<String, Object>> episodes = videoSourceService.parseEpisodes(source, seriesUrl);
        return ApiResult.success("解析完成，共 " + episodes.size() + " 集", episodes);
    }

    /**
     * 智能推测集数正则
     */
    @PostMapping("/suggest-regex")
    public ApiResult<Map<String, Object>> suggestRegex(@RequestBody Map<String, String> body) {
        String seriesUrl = body.get("seriesUrl");
        if (seriesUrl == null || seriesUrl.trim().isEmpty()) {
            return ApiResult.error("seriesUrl 不能为空");
        }
        Map<String, Object> suggestion = videoSourceService.suggestRegex(seriesUrl);
        return ApiResult.success(suggestion);
    }

    /**
     * 测试集数解析（首页测试时用）
     */
    @PostMapping("/test-parse-no-id")
    public ApiResult<List<Map<String, Object>>> testParseInline(@RequestBody Map<String, Object> body) {
        String seriesUrl = (String) body.get("seriesUrl");
        VideoSource source = new VideoSource();
        source.setHomeUrl((String) body.get("homeUrl"));
        source.setEpisodePattern((String) body.get("episodePattern"));
        Object eg = body.get("episodeGroup");
        source.setEpisodeGroup(eg != null ? Integer.valueOf(eg.toString()) : 1);

        List<Map<String, Object>> episodes = videoSourceService.parseEpisodes(source, seriesUrl);
        return ApiResult.success("解析完成，共 " + episodes.size() + " 集", episodes);
    }
}
