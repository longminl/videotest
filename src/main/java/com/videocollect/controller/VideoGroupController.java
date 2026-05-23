package com.videocollect.controller;

import com.videocollect.dao.VideoRecordDao;
import com.videocollect.dto.ApiResult;
import com.videocollect.model.VideoGroup;
import com.videocollect.model.VideoRecord;
import com.videocollect.service.HlsCacheService;
import com.videocollect.service.VideoGroupService;
import com.videocollect.service.VideoGroupService.DeleteResult;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.web.bind.annotation.*;

import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;

/**
 * 合集管理 REST API
 */
@RestController
@RequestMapping("/api/group")
public class VideoGroupController {

    @Autowired
    private VideoGroupService videoGroupService;

    @Autowired
    private VideoRecordDao videoRecordDao;

    @Autowired
    private HlsCacheService hlsCacheService;

    /**
     * 获取所有合集
     */
    @GetMapping("/list")
    public ApiResult<List<VideoGroup>> list() {
        return ApiResult.success(videoGroupService.findAll());
    }

    /**
     * 获取单个合集
     */
    @GetMapping("/{id}")
    public ApiResult<VideoGroup> get(@PathVariable Long id) {
        VideoGroup group = videoGroupService.findById(id);
        if (group == null) return ApiResult.error("合集不存在");
        return ApiResult.success(group);
    }

    /**
     * 获取合集下属视频
     */
    @GetMapping("/{id}/videos")
    public ApiResult<List<VideoRecord>> videos(@PathVariable Long id,
                                                @RequestParam(required = false) String sortBy,
                                                @RequestParam(required = false) String sortOrder) {
        List<VideoRecord> list = videoRecordDao.findByGroupId(id, sortBy, sortOrder);
        for (VideoRecord r : list) {
            r.setCacheSize(hlsCacheService.getCacheSizeText(r.getTitle()));
        }
        return ApiResult.success(list);
    }

    /**
     * 新建合集
     */
    @PostMapping
    public ApiResult<VideoGroup> create(@RequestBody VideoGroup group) {
        if (group.getName() == null || group.getName().trim().isEmpty()) {
            return ApiResult.error("合集名称不能为空");
        }
        VideoGroup saved = videoGroupService.save(group);
        return ApiResult.success("合集已创建", saved);
    }

    /**
     * 编辑合集
     */
    @PutMapping("/{id}")
    public ApiResult<VideoGroup> update(@PathVariable Long id, @RequestBody VideoGroup group) {
        group.setId(id);
        VideoGroup saved = videoGroupService.save(group);
        return ApiResult.success("合集已更新", saved);
    }

    /**
     * 删除合集
     * query: deleteVideos=true|false 是否同时删除下属视频
     */
    @DeleteMapping("/{id}")
    public ApiResult<?> delete(@PathVariable Long id,
                               @RequestParam(defaultValue = "false") boolean deleteVideos) {
        DeleteResult result = videoGroupService.delete(id, deleteVideos);
        if (result.isSuccess()) {
            String msg = result.getMessage();
            if (result.getAffectedVideos() > 0 && !deleteVideos) {
                msg += "（" + result.getAffectedVideos() + "个视频已解除关联）";
            }
            return ApiResult.success(msg);
        }
        return ApiResult.error(result.getMessage());
    }

    /**
     * 移动视频到合集
     */
    @PutMapping("/move-video")
    public ApiResult<?> moveVideo(@RequestBody Map<String, Long> body) {
        Long videoId = body.get("videoId");
        Long groupId = body.get("groupId");
        if (videoId == null || groupId == null) {
            return ApiResult.error("参数不完整");
        }
        VideoRecord record = videoRecordDao.findById(videoId);
        if (record == null) return ApiResult.error("视频记录不存在");

        videoRecordDao.updateGroup(videoId, groupId);

        String groupName = "合集";
        VideoGroup group = videoGroupService.findById(groupId);
        if (group != null) groupName = group.getName();

        return ApiResult.success("已移动到: " + groupName);
    }

    /**
     * 批量移动视频到合集
     */
    @PutMapping("/batch-move-video")
    public ApiResult<?> batchMoveVideo(@RequestBody Map<String, Object> body) {
        @SuppressWarnings("unchecked")
        List<Integer> idInts = (List<Integer>) body.get("videoIds");
        Long groupId = Long.valueOf(body.get("groupId").toString());
        if (idInts == null || idInts.isEmpty() || groupId == null) {
            return ApiResult.error("参数不完整");
        }
        List<Long> ids = idInts.stream().map(Integer::longValue).collect(Collectors.toList());
        videoRecordDao.batchUpdateGroup(ids, groupId);

        String groupName = "合集";
        VideoGroup group = videoGroupService.findById(groupId);
        if (group != null) groupName = group.getName();

        return ApiResult.success("已将 " + ids.size() + " 个视频移动到: " + groupName);
    }

    /**
     * 从合集移除视频（解除关联）
     */
    @PutMapping("/unlink-video/{videoId}")
    public ApiResult<?> unlinkVideo(@PathVariable Long videoId) {
        VideoRecord record = videoRecordDao.findById(videoId);
        if (record == null) return ApiResult.error("视频记录不存在");
        videoRecordDao.updateGroup(videoId, null);
        return ApiResult.success("已从合集中移除");
    }
}
