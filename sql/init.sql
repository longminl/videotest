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

-- 视频源配置表（用户可维护多个影视站点规则）
CREATE TABLE IF NOT EXISTS video_source (
  id                  BIGINT       PRIMARY KEY AUTO_INCREMENT COMMENT '主键ID',
  name                VARCHAR(100) NOT NULL COMMENT '视频源名称',
  home_url            VARCHAR(512) NOT NULL COMMENT '网站首页地址',
  search_url          VARCHAR(512) NOT NULL COMMENT '搜索API地址，用 {keyword} 占位',
  search_data_path    VARCHAR(100) DEFAULT '$' COMMENT 'JSON数据路径',
  search_title_field  VARCHAR(50)  DEFAULT 'title' COMMENT '结果标题字段',
  search_url_field    VARCHAR(50)  DEFAULT 'url' COMMENT '结果链接字段',
  search_cover_field  VARCHAR(50)  DEFAULT NULL COMMENT '结果封面字段',
  episode_pattern     VARCHAR(255) DEFAULT NULL COMMENT '从URL提取集数的正则',
  episode_group       INT          DEFAULT 1 COMMENT '正则捕获组索引',
  episode_selector    VARCHAR(255) DEFAULT NULL COMMENT '集数链接CSS选择器（可选）',
  encoding            VARCHAR(20)  DEFAULT 'UTF-8' COMMENT '页面编码',
  sort_order          INT          NOT NULL DEFAULT 0 COMMENT '排序',
  created_at          DATETIME     NOT NULL DEFAULT CURRENT_TIMESTAMP COMMENT '创建时间',
  updated_at          DATETIME     NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP COMMENT '更新时间'
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COMMENT='视频源配置';

-- 视频合集表（一级分类，如"狂飙"）
CREATE TABLE IF NOT EXISTS video_group (
  id              BIGINT       PRIMARY KEY AUTO_INCREMENT COMMENT '主键ID',
  name            VARCHAR(255) NOT NULL COMMENT '合集名称',
  source_id       BIGINT       DEFAULT NULL COMMENT '关联视频源ID',
  source_series_url VARCHAR(1024) DEFAULT NULL COMMENT '剧集主页URL',
  total_episodes  INT          DEFAULT NULL COMMENT '已知总集数',
  description     VARCHAR(500) DEFAULT NULL COMMENT '描述',
  sort_order      INT          NOT NULL DEFAULT 0 COMMENT '排序',
  created_at      DATETIME     NOT NULL DEFAULT CURRENT_TIMESTAMP COMMENT '创建时间',
  updated_at      DATETIME     NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP COMMENT '更新时间',
  INDEX idx_source (source_id)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COMMENT='视频合集（一级分类）';

-- 给 video_collection 表添加合集ID和集数字段（向后兼容，已有数据为 NULL）
ALTER TABLE video_collection
  ADD COLUMN group_id      BIGINT  DEFAULT NULL COMMENT '所属合集ID',
  ADD COLUMN episode_number INT    DEFAULT NULL COMMENT '集数（如第265集）',
  ADD INDEX idx_group (group_id),
  ADD INDEX idx_episode (group_id, episode_number);
