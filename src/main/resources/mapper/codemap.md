# src/main/resources/mapper/

## Responsibility
MyBatis SQL 映射文件。定义 `VideoRecordDao` 接口的 SQL 语句。

## VideoRecordMapper.xml
- **namespace**: `com.videocollect.dao.VideoRecordDao`
- **resultMap `baseMap`**: 字段映射（underscore → camelCase，与 `map-underscore-to-camel-case: true` 一致）
- **SQL 片段 `baseColumns`**: 所有列的公共引用

### SELECT 语句
| id | 用途 |
|----|---------|
| `findById` | 按主键查询单条 |
| `findByIds` | 批量查询（`<foreach>` IN 子句）|
| `findBySourceUrl` | 按 source_url 查重（LIMIT 1）|
| `findPage` | 分页查询，支持 status 筛选 + keyword 模糊匹配（title/page_title/source_url/video_url）+ 排序（title 或 created_at，ASC 或 DESC）|
| `count` | 分页总数（复用 WHERE 条件）|
| `findNeedCheck` | 查询 status IN (0, 2) 的需要检测记录，按 created_at DESC 排序 |

### INSERT 语句
| id | 用途 |
|----|---------|
| `insert` | 插入新记录，`useGeneratedKeys=true` 返回自增 ID |

### UPDATE 语句
| id | 用途 |
|----|---------|
| `updateStatus` | 更新 status, latency_ms, remark（含 updatedAt）|
| `updateRemark` | 更新 remark（含 updatedAt）|
| `updateVideoUrl` | 更新 video_url（含 updatedAt）|
| `updatePageTitle` | 更新 page_title（含 updatedAt）|
| `updateIsCached` | 更新 is_cached 标记（含 updatedAt）|

所有 UPDATE 语句的 `updated_at = #{updatedAt}` 由 `UpdateTimestampInterceptor` 自动注入。

### DELETE 语句
| id | 用途 |
|----|---------|
| `deleteById` | 按主键删除 |
| `deleteBatch` | 批量删除（`<foreach>` IN 子句）|
