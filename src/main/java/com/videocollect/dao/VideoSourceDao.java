package com.videocollect.dao;

import com.videocollect.model.VideoSource;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;

import java.util.List;

/**
 * 视频源配置 DAO
 */
@Mapper
public interface VideoSourceDao {

    /** 插入视频源 */
    int insert(VideoSource source);

    /** 根据ID查询 */
    VideoSource findById(@Param("id") Long id);

    /** 查询所有视频源（按排序字段正序） */
    List<VideoSource> findAll();

    /** 更新视频源 */
    int update(VideoSource source);

    /** 删除视频源 */
    int deleteById(@Param("id") Long id);

    /** 批量插入（导入用） */
    int insertBatch(@Param("list") List<VideoSource> sources);
}
