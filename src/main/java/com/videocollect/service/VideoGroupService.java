package com.videocollect.service;

import com.videocollect.dao.VideoGroupDao;
import com.videocollect.dao.VideoRecordDao;
import com.videocollect.model.VideoGroup;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;

import java.util.List;

/**
 * 合集管理服务：CRUD + 删除确认
 */
@Service
public class VideoGroupService {

    private static final Logger log = LoggerFactory.getLogger(VideoGroupService.class);

    @Autowired
    private VideoGroupDao videoGroupDao;

    @Autowired
    private VideoRecordDao videoRecordDao;

    // ====== CRUD ======

    public List<VideoGroup> findAll() {
        return videoGroupDao.findAll();
    }

    public VideoGroup findById(Long id) {
        return videoGroupDao.findById(id);
    }

    public VideoGroup save(VideoGroup group) {
        if (group.getId() != null) {
            videoGroupDao.update(group);
            return videoGroupDao.findById(group.getId());
        } else {
            videoGroupDao.insert(group);
            return group;
        }
    }

    /**
     * 删除合集
     * @param id 合集ID
     * @param deleteVideos 是否同时删除下属视频
     * @return 删除结果描述
     */
    public DeleteResult delete(Long id, boolean deleteVideos) {
        VideoGroup group = videoGroupDao.findById(id);
        if (group == null) {
            return new DeleteResult(false, "合集不存在", 0);
        }

        int videoCount = videoRecordDao.countByGroupId(id);

        if (deleteVideos && videoCount > 0) {
            // 删除合集下的所有视频记录
            List<com.videocollect.model.VideoRecord> records = videoRecordDao.findByGroupId(id, null, null);
            for (com.videocollect.model.VideoRecord r : records) {
                videoRecordDao.deleteById(r.getId());
            }
            log.info("删除合集 {} 时同时删除了 {} 个视频", group.getName(), videoCount);
        } else if (videoCount > 0) {
            // 保留视频，解除关联
            List<com.videocollect.model.VideoRecord> records = videoRecordDao.findByGroupId(id, null, null);
            for (com.videocollect.model.VideoRecord r : records) {
                videoRecordDao.updateGroup(r.getId(), null);
            }
            log.info("删除合集 {}，已解除 {} 个视频的关联", group.getName(), videoCount);
        }

        videoGroupDao.deleteById(id);
        return new DeleteResult(true, "合集已删除", videoCount);
    }

    /**
     * 删除结果
     */
    public static class DeleteResult {
        private boolean success;
        private String message;
        private int affectedVideos;

        public DeleteResult(boolean success, String message, int affectedVideos) {
            this.success = success;
            this.message = message;
            this.affectedVideos = affectedVideos;
        }

        public boolean isSuccess() { return success; }
        public String getMessage() { return message; }
        public int getAffectedVideos() { return affectedVideos; }
    }
}
