package com.videocollect.dao;

import com.videocollect.model.VideoGroup;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;

import java.util.List;

/**
 * 视频合集 DAO
 */
@Mapper
public interface VideoGroupDao {

    /** 插入合集 */
    int insert(VideoGroup group);

    /** 根据ID查询 */
    VideoGroup findById(@Param("id") Long id);

    /** 查询所有合集（按排序字段正序） */
    List<VideoGroup> findAll();

    /** 更新合集 */
    int update(VideoGroup group);

    /** 删除合集 */
    int deleteById(@Param("id") Long id);

    /** 查询合集下属视频数量 */
    int countVideos(@Param("groupId") Long groupId);
}
