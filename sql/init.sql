-- 创建数据库
CREATE DATABASE IF NOT EXISTS video_collect DEFAULT CHARACTER SET utf8mb4 COLLATE utf8mb4_general_ci;

USE video_collect;

-- 视频收藏表
CREATE TABLE IF NOT EXISTS video_collection (
  id          BIGINT       PRIMARY KEY AUTO_INCREMENT COMMENT '主键ID',
  title       VARCHAR(255) NOT NULL COMMENT '视频标题（取自页面/手动填写）',
  source_url  VARCHAR(1024) NOT NULL COMMENT '来源网页URL',
  video_url   VARCHAR(2048) NOT NULL COMMENT '视频流地址',
  status      TINYINT      NOT NULL DEFAULT 0 COMMENT '状态: 0=未检测, 1=可播放, 2=不可播放, 3=解析失败',
  latency_ms  INT          DEFAULT NULL COMMENT '延迟（毫秒）',
  page_title  VARCHAR(255) DEFAULT NULL COMMENT '网页标题',
  remark      VARCHAR(500) DEFAULT NULL COMMENT '备注',
  is_cached   TINYINT(1)   NOT NULL DEFAULT 0 COMMENT 'HLS ts 是否已全部缓存',
  created_at  DATETIME     NOT NULL DEFAULT CURRENT_TIMESTAMP COMMENT '创建时间',
  updated_at  DATETIME     NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP COMMENT '更新时间',
  INDEX idx_status (status),
  INDEX idx_created (created_at)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COMMENT='视频收藏表';
