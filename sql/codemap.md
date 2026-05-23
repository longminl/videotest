# sql/

## Responsibility
数据库初始化脚本。创建 video_collect 库和 video_collection 表。

## init.sql
```sql
CREATE DATABASE video_collect DEFAULT CHARACTER SET utf8mb4;

CREATE TABLE video_collection (
  id          BIGINT       PRIMARY KEY AUTO_INCREMENT,
  title       VARCHAR(255) NOT NULL,
  source_url  VARCHAR(1024) NOT NULL,
  video_url   VARCHAR(2048) NOT NULL,
  status      TINYINT      NOT NULL DEFAULT 0,    -- 0=未检测, 1=可播放, 2=不可播放, 3=解析失败
  latency_ms  INT          DEFAULT NULL,
  page_title  VARCHAR(255) DEFAULT NULL,
  remark      VARCHAR(500) DEFAULT NULL,
  is_cached   TINYINT(1)   NOT NULL DEFAULT 0,
  created_at  DATETIME     NOT NULL DEFAULT CURRENT_TIMESTAMP,
  updated_at  DATETIME     NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
  INDEX idx_status (status),
  INDEX idx_created (created_at)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4;
```
