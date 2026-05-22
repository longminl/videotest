-- SQLite 建表脚本（Spring Profile: sqlite 时自动执行）
-- 库文件由 JDBC URL 中的路径决定，无需 CREATE DATABASE

CREATE TABLE IF NOT EXISTS video_collection (
    id         INTEGER PRIMARY KEY AUTOINCREMENT,
    title      TEXT    NOT NULL,
    source_url TEXT    NOT NULL,
    video_url  TEXT    NOT NULL,
    status     INTEGER NOT NULL DEFAULT 0,
    latency_ms INTEGER DEFAULT NULL,
    page_title TEXT    DEFAULT NULL,
    remark     TEXT    DEFAULT NULL,
    is_cached  INTEGER NOT NULL DEFAULT 0,
    created_at TEXT    NOT NULL DEFAULT (datetime('now','localtime')),
    updated_at TEXT    NOT NULL DEFAULT (datetime('now','localtime'))
);

CREATE INDEX IF NOT EXISTS idx_status ON video_collection(status);
CREATE INDEX IF NOT EXISTS idx_created ON video_collection(created_at);
