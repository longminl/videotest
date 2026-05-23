-- SQLite 建表脚本（Spring Profile: sqlite 时自动执行）
-- 库文件由 JDBC URL 中的路径决定，无需 CREATE DATABASE

CREATE TABLE IF NOT EXISTS video_collection (
    id             INTEGER PRIMARY KEY AUTOINCREMENT,
    title          TEXT    NOT NULL,
    source_url     TEXT    NOT NULL,
    video_url      TEXT    NOT NULL,
    status         INTEGER NOT NULL DEFAULT 0,
    latency_ms     INTEGER DEFAULT NULL,
    page_title     TEXT    DEFAULT NULL,
    remark         TEXT    DEFAULT NULL,
    group_id       INTEGER DEFAULT NULL,
    episode_number INTEGER DEFAULT NULL,
    is_cached      INTEGER NOT NULL DEFAULT 0,
    created_at     TEXT    NOT NULL DEFAULT (datetime('now','localtime')),
    updated_at     TEXT    NOT NULL DEFAULT (datetime('now','localtime'))
);

CREATE INDEX IF NOT EXISTS idx_status      ON video_collection(status);
CREATE INDEX IF NOT EXISTS idx_created     ON video_collection(created_at);
CREATE INDEX IF NOT EXISTS idx_group_id    ON video_collection(group_id);
CREATE INDEX IF NOT EXISTS idx_group_ep    ON video_collection(group_id, episode_number);

-- 视频源配置表
CREATE TABLE IF NOT EXISTS video_source (
    id                INTEGER PRIMARY KEY AUTOINCREMENT,
    name              TEXT    NOT NULL,
    home_url          TEXT    NOT NULL,
    search_url        TEXT    NOT NULL,
    search_data_path  TEXT    DEFAULT '$',
    search_title_field TEXT   DEFAULT 'title',
    search_url_field  TEXT   DEFAULT 'url',
    search_cover_field TEXT  DEFAULT NULL,
    episode_pattern   TEXT   DEFAULT NULL,
    episode_group     INTEGER DEFAULT 1,
    episode_selector  TEXT   DEFAULT NULL,
    encoding          TEXT   DEFAULT 'UTF-8',
    sort_order        INTEGER NOT NULL DEFAULT 0,
    created_at        TEXT   NOT NULL DEFAULT (datetime('now','localtime')),
    updated_at        TEXT   NOT NULL DEFAULT (datetime('now','localtime'))
);

-- 视频合集表
CREATE TABLE IF NOT EXISTS video_group (
    id               INTEGER PRIMARY KEY AUTOINCREMENT,
    name             TEXT    NOT NULL,
    source_id        INTEGER DEFAULT NULL,
    source_series_url TEXT   DEFAULT NULL,
    total_episodes   INTEGER DEFAULT NULL,
    description      TEXT   DEFAULT NULL,
    sort_order       INTEGER NOT NULL DEFAULT 0,
    created_at       TEXT   NOT NULL DEFAULT (datetime('now','localtime')),
    updated_at       TEXT   NOT NULL DEFAULT (datetime('now','localtime'))
);

CREATE INDEX IF NOT EXISTS idx_vg_source ON video_group(source_id);
