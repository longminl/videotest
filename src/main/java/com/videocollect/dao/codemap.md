# src/main/java/com/videocollect/dao/

## Responsibility
数据访问层，MyBatis Mapper 接口。定义 `video_collection` 表的所有数据库操作。

## VideoRecordDao.java
- `@Mapper` — MyBatis 自动扫描注册
- SQL 映射在 `src/main/resources/mapper/VideoRecordMapper.xml` 中定义

### 查询方法
| 方法 | 用途 |
|--------|---------|
| `findById(Long)` | 按 ID 查单条 |
| `findByIds(List<Long>)` | 批量查询（用于批量删除前清缓存） |
| `findBySourceUrl(String)` | 按来源 URL 查重 |
| `findPage(offset, limit, status, keyword, sortBy, sortOrder)` | 分页 + 筛选 + 排序 |
| `count(status, keyword)` | 分页总数 |
| `findNeedCheck()` | 查询所有需检测的记录（status=0 或 2） |

### 写入方法
| 方法 | 用途 |
|--------|---------|
| `insert(VideoRecord)` | 插入新记录 |
| `updateStatus(id, status, latencyMs, remark)` | 更新检测状态 |
| `updateRemark(id, remark)` | 更新备注 |
| `updateIsCached(id, isCached)` | 更新 HLS 缓存完成标记 |
| `updateVideoUrl(id, videoUrl)` | 更新视频地址 |
| `updatePageTitle(id, pageTitle)` | 更新页面标题 |
| `deleteById(id)` | 删除单条 |
| `deleteBatch(List<Long>)` | 批量删除 |

## Integration
- 被 `CollectService`、`VideoController`、`HlsProxyController` 调用
- 映射文件：`src/main/resources/mapper/VideoRecordMapper.xml`
