package com.videocollect.controller;

import com.videocollect.dao.VideoRecordDao;
import com.videocollect.dto.ApiResult;
import com.videocollect.dto.PageResult;
import com.videocollect.model.VideoRecord;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Controller;
import org.springframework.ui.Model;
import org.springframework.web.bind.annotation.*;

/**
 * 页面控制器：返回 Thymeleaf 页面 + REST 数据接口
 */
@Controller
@RequestMapping("/")
public class VideoController {

    @Autowired
    private VideoRecordDao videoRecordDao;

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
            @RequestParam(required = false) String keyword) {

        int offset = (page - 1) * pageSize;
        java.util.List<VideoRecord> list = videoRecordDao.findPage(offset, pageSize, status, keyword);
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
        videoRecordDao.deleteById(id);
        return ApiResult.success("删除成功");
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
