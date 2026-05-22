package com.videocollect.controller;

import com.videocollect.dao.VideoRecordDao;
import com.videocollect.dto.ApiResult;
import com.videocollect.dto.PageResult;
import com.videocollect.model.VideoRecord;
import com.videocollect.service.HlsCacheService;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Controller;
import org.springframework.ui.Model;
import java.util.List;
import java.util.Map;

import org.springframework.web.bind.annotation.*;

/**
 * 页面控制器：返回 Thymeleaf 页面 + REST 数据接口
 */
@Controller
@RequestMapping("/")
public class VideoController {

    @Autowired
    private VideoRecordDao videoRecordDao;

    @Autowired
    private HlsCacheService hlsCacheService;

    /**
     * 首页（收藏列表页）
     */
    @GetMapping
    public String index() {
        return "index";
    }

    /**
     * 详情页
     */
    @GetMapping("/detail/{id}")
    public String detail(@PathVariable Long id, Model model) {
        VideoRecord record = videoRecordDao.findById(id);
        if (record == null) {
            return "redirect:/?error=记录不存在";
        }
        model.addAttribute("record", record);
        return "detail";
    }

    // ========== REST 数据接口 ==========

    /**
     * 分页查询列表
     */
    @GetMapping("/api/list")
    @ResponseBody
    public ApiResult<PageResult<VideoRecord>> list(
            @RequestParam(defaultValue = "1") int page,
            @RequestParam(defaultValue = "20") int pageSize,
            @RequestParam(required = false) Integer status,
            @RequestParam(required = false) String keyword,
            @RequestParam(required = false) String sortBy,
            @RequestParam(required = false) String sortOrder) {

        int offset = (page - 1) * pageSize;
        java.util.List<VideoRecord> list = videoRecordDao.findPage(offset, pageSize, status, keyword, sortBy, sortOrder);
        for (VideoRecord r : list) {
            r.setCacheSize(hlsCacheService.getCacheSizeText(r.getTitle()));
        }
        long total = videoRecordDao.count(status, keyword);
        PageResult<VideoRecord> pageResult = new PageResult<>(list, total, page, pageSize);
        return ApiResult.success(pageResult);
    }

    /**
     * 获取单条详情（JSON）
     */
    @GetMapping("/api/detail/{id}")
    @ResponseBody
    public ApiResult<VideoRecord> detailApi(@PathVariable Long id) {
        VideoRecord record = videoRecordDao.findById(id);
        if (record == null) {
            return ApiResult.error("记录不存在");
        }
        record.setCacheSize(hlsCacheService.getCacheSizeText(record.getTitle()));
        return ApiResult.success(record);
    }

    /**
     * 删除记录
     */
    @DeleteMapping("/api/delete/{id}")
    @ResponseBody
    public ApiResult<?> delete(@PathVariable Long id) {
        VideoRecord record = videoRecordDao.findById(id);
        if (record == null) {
            return ApiResult.error("记录不存在");
        }
        // 先清除本地缓存（ts + m3u8 + tsUrlListCache）
        hlsCacheService.clearVideoCache(record.getTitle());
        videoRecordDao.deleteById(id);
        return ApiResult.success("删除成功");
    }

    /**
     * 批量删除记录
     */
    @DeleteMapping("/api/delete-batch")
    @ResponseBody
    public ApiResult<?> deleteBatch(@RequestBody Map<String, List<Long>> body) {
        List<Long> ids = body.get("ids");
        if (ids == null || ids.isEmpty()) {
            return ApiResult.error("请选择要删除的记录");
        }
        // 先查所有记录，逐条清缓存
        List<VideoRecord> records = videoRecordDao.findByIds(ids);
        for (VideoRecord r : records) {
            hlsCacheService.clearVideoCache(r.getTitle());
        }
        int count = videoRecordDao.deleteBatch(ids);
        return ApiResult.success("批量删除成功，共 " + count + " 条");
    }

    /**
     * 更新备注
     */
    @PutMapping("/api/remark/{id}")
    @ResponseBody
    public ApiResult<?> updateRemark(@PathVariable Long id, @RequestParam String remark) {
        VideoRecord record = videoRecordDao.findById(id);
        if (record == null) {
            return ApiResult.error("记录不存在");
        }
        videoRecordDao.updateRemark(id, remark);
        return ApiResult.success("备注已更新");
    }
}
