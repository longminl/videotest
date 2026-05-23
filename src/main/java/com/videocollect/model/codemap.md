# src/main/java/com/videocollect/model/

## Responsibility
数据库实体模型。对应 `video_collection` 表的 Java 映射。

## VideoRecord.java
手动 getter/setter（无 Lombok 注解），与 `application.yml` 的 `map-underscore-to-camel-case: true` 配合完成字段映射。

### 字段
| 字段 | 类型 | 数据库列 | 说明 |
|--------|------|-------------|--------|
| `id` | Long | `id` | 自增主键 |
| `title` | String | `title` | 视频标题 |
| `sourceUrl` | String | `source_url` | 来源网页URL |
| `videoUrl` | String | `video_url` | 视频流地址 |
| `status` | Integer | `status` | 0=未检测, 1=可播放, 2=不可播放, 3=解析失败 |
| `latencyMs` | Integer | `latency_ms` | 延迟（毫秒） |
| `pageTitle` | String | `page_title` | 网页标题 |
| `remark` | String | `remark` | 备注（来源信息 + 检测结果） |
| `isCached` | Boolean | `is_cached` | HLS ts 是否全部缓存 |
| `cacheSize` | String | — | 非 DB 字段，运行时计算 |
| `createdAt` | LocalDateTime | `created_at` | 创建时间 |
| `updatedAt` | LocalDateTime | `updated_at` | 更新时间 |

### 辅助方法
- `getIsCached()` — 安全空值判断（null → false）
- `getStatusText()` — status 值 → 中文字段（"未检测"/"可播放"/"不可播放"/"解析失败"）
- `getLatencyText()` — 延迟转字符串（null → "-"）
