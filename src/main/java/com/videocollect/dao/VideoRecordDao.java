package com.videocollect.dao;

import com.videocollect.model.VideoRecord;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;

import java.util.List;

/**
 * 视频收藏记录 DAO
 */
@Mapper
public interface VideoRecordDao {

    /** 插入记录 */
    int insert(VideoRecord record);

    /** 根据ID查询 */
    VideoRecord findById(@Param("id") Long id);

    /** 根据ID批量查询 */
    List<VideoRecord> findByIds(@Param("ids") List<Long> ids);

    /** 根据来源URL查询（用于去重检测） */
    VideoRecord findBySourceUrl(@Param("sourceUrl") String sourceUrl);

    /** 分页查询 */
    List<VideoRecord> findPage(@Param("offset") int offset,
                               @Param("limit") int limit,
                               @Param("status") Integer status,
                               @Param("keyword") String keyword);

    /** 查询总数 */
    long count(@Param("status") Integer status,
               @Param("keyword") String keyword);

    /** 更新状态和延迟 */
    int updateStatus(@Param("id") Long id,
                     @Param("status") Integer status,
                     @Param("latencyMs") Integer latencyMs,
                     @Param("remark") String remark);

    /** 更新备注 */
    int updateRemark(@Param("id") Long id,
                     @Param("remark") String remark);

    /** 更新缓存状态 */
    int updateIsCached(@Param("id") Long id,
                       @Param("isCached") Boolean isCached);

    /** 更新视频地址 */
    int updateVideoUrl(@Param("id") Long id,
                       @Param("videoUrl") String videoUrl);

    /** 更新页面标题 */
    int updatePageTitle(@Param("id") Long id,
                        @Param("pageTitle") String pageTitle);

    /** 删除单条记录 */
    int deleteById(@Param("id") Long id);

    /** 批量删除记录 */
    int deleteBatch(@Param("ids") List<Long> ids);

    /** 查询所有需要检测的记录（status=0或2） */
    List<VideoRecord> findNeedCheck();
}
