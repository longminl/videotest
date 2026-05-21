package com.videocollect.controller;

import com.videocollect.dto.ApiResult;
import com.videocollect.dto.CheckProgress;
import com.videocollect.dto.CollectRequest;
import com.videocollect.service.CollectService;
import com.videocollect.service.CollectService.CollectResult;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.web.bind.annotation.*;

/**
 * 视频收集 REST API
 */
@RestController
@RequestMapping("/api")
public class CollectController {

    private static final Logger log = LoggerFactory.getLogger(CollectController.class);

    @Autowired
    private CollectService collectService;

    /**
     * 提交URL进行收集
     */
    @PostMapping("/collect")
    public ApiResult<?> collect(@RequestBody CollectRequest request) {
        log.info("收到收集请求: {}", request.getUrl());
        CollectResult result = collectService.collect(request.getUrl());
        if (result.isSuccess()) {
            return ApiResult.success(result.getMessage(), result.getData());
        } else {
            return ApiResult.error(result.getMessage());
        }
    }

    /**
     * 手动重新检测单条
     */
    @PutMapping("/check/{id}")
    public ApiResult<?> recheck(@PathVariable Long id) {
        CollectResult result = collectService.recheck(id);
        if (result.isSuccess()) {
            return ApiResult.success(result.getMessage(), result.getData());
        } else {
            return ApiResult.error(result.getMessage());
        }
    }

    /**
     * 一键重新检测所有（同步执行，等待完成）
     */
    @PutMapping("/check-all")
    public ApiResult<CheckProgress> recheckAll() {
        CheckProgress progress = collectService.recheckAll();
        String msg = "检测完成，共 " + progress.getTotal() + " 条，"
                + "成功 " + progress.getSuccessCount() + " 条，"
                + "失败 " + progress.getFailCount() + " 条";
        return ApiResult.success(msg, progress);
    }
}
