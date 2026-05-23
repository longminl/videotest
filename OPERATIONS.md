# 部署操作手册

> 本文档记录每次功能更新后需要手动执行的操作步骤。

---

## 步骤1：数据库结构更新

### 执行时间
2026-05-23

### 变更内容
- 新增 `video_source` 表：视频源配置
- 新增 `video_group` 表：视频合集（一级分类）
- 修改 `video_collection` 表：新增 `group_id` 和 `episode_number` 字段

### 操作步骤

```sql
-- 如果还未初始化数据库，执行完整初始化
SOURCE sql/init.sql;

-- 如果数据库已存在（已有数据），只需执行新增部分：
USE video_collect;

-- 创建视频源配置表
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

-- 创建视频合集表
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

-- 给 video_collection 添加字段（已有数据不受影响）
ALTER TABLE video_collection
  ADD COLUMN group_id      BIGINT  DEFAULT NULL COMMENT '所属合集ID',
  ADD COLUMN episode_number INT    DEFAULT NULL COMMENT '集数（如第265集）',
  ADD INDEX idx_group (group_id),
  ADD INDEX idx_episode (group_id, episode_number);
```

### 验证方法

```sql
-- 检查新表是否存在
SHOW TABLES LIKE 'video_source';
SHOW TABLES LIKE 'video_group';

-- 检查 video_collection 是否新增字段
DESC video_collection;

-- 确认旧数据不受影响（group_id 和 episode_number 应为 NULL）
SELECT id, title, group_id, episode_number FROM video_collection LIMIT 5;
```

### SQLite 同步

```sql
-- 如果使用 SQLite 数据库（spring.profiles.active=sqlite），
-- 也需要执行对应的同步脚本：
-- src/main/resources/sql/init-sqlite.sql 已同步更新

-- 如需手动更新已有 SQLite 库，执行：
ALTER TABLE video_collection ADD COLUMN group_id INTEGER DEFAULT NULL;
ALTER TABLE video_collection ADD COLUMN episode_number INTEGER DEFAULT NULL;
CREATE INDEX IF NOT EXISTS idx_group_id ON video_collection(group_id);
CREATE INDEX IF NOT EXISTS idx_group_ep ON video_collection(group_id, episode_number);
```

---

## 步骤2-3：新增实体类与 DAO

### 执行时间
2026-05-23

### 变更内容
- 新增 `VideoSource.java` 实体类
- 新增 `VideoGroup.java` 实体类
- 修改 `VideoRecord.java`：追加 `groupId`、`episodeNumber` 字段
- 新增 `VideoSourceDao.java` + `VideoSourceMapper.xml`
- 新增 `VideoGroupDao.java` + `VideoGroupMapper.xml`
- 修改 `VideoRecordDao.java` + `VideoRecordMapper.xml`：追加合集/集数相关查询

### 操作步骤
无需手动操作，代码已包含在构建中。重新打包即可：

```bash
mvn clean package
```

---

## 步骤4-5：Service 层 + Controller 层

### 执行时间
2026-05-23

### 变更内容
- 新增 `SourceTemplateLoader.java`：启动时自动从 application.yml 加载预设视频源模板
- 新增 `VideoSourceService.java`：视频源 CRUD + 搜索测试 + 集数解析测试
- 新增 `VideoGroupService.java`：合集 CRUD + 删除确认（可选择是否同时删除视频）
- 新增 `EpisodeSearchService.java`：全源搜索 + 剧集解析 + 批量导入 + 下一集检测
- 新增 `VideoSourceController.java`：`/api/source/*` 接口
- 新增 `VideoGroupController.java`：`/api/group/*` 接口
- 新增 `EpisodeController.java`：`/api/episode/*` 接口

### 操作步骤
无需手动操作，重新打包即可。启动时会自动从 application.yml 读取预设模板并写入数据库。

```bash
mvn clean package
java -jar target/video-collect-1.0.0.jar
```

---

## 步骤6：前端页面

### 执行时间
2026-05-23

### 新增页面
- **视频源管理页** (`/sources`)：新增/编辑/删除视频源，附带一键测试搜索和测试集数解析功能
- **合集管理弹窗**：在列表页操作，支持新建/编辑/删除合集，删除时可选同时删除视频
- **总搜索弹窗**：在所有视频源中并行搜索，结果分组展示，可选择导入
- **批量导入向导**：三步式向导 — 选源 → 输入剧集主页 → 选择集数 → 导入

### 页面改动
- **列表页** (`index.html`)
  - 顶栏新增"视频源"和"总搜索"按钮
  - 新增合集筛选栏：下拉选择合集 + 管理合集 + 批量导入按钮
  - 表格新增列：合集、集数、下一集
  - 响应式适配：400px 隐藏合集列，576px 隐藏延迟和收录时间，768px 操作列 sticky
- **详情页** (`detail.html`)
  - 显示所属合集和集数
  - 新增"合集"按钮（移动到合集）
  - 新增"下集"按钮（检测下一集并一键导入）

### 操作步骤
无需手动操作，直接启动即可。

```bash
mvn clean package
java -jar target/video-collect-1.0.0.jar
```

启动后访问：
- `http://localhost:8080/` — 列表页（全部功能已更新）
- `http://localhost:8080/sources` — 视频源管理页

---

## 2026-05-23 更新：Bug 修复 + 批量移动到合集

### 变更内容

#### Bug 修复
1. **合集创建失败** — `VideoGroupService.save()` 新建合集时未设 `sortOrder` 默认值，SQL NOT NULL 约束报错。修复：新建时自动设 `sortOrder=0`、`totalEpisodes=0`。
2. **视频入库未关联合集** — `VideoRecordMapper.xml` 的 INSERT 语句缺少 `group_id` 和 `episode_number` 字段，虽然 `setGroupId()` 已调用但值被静默丢弃。修复：INSERT 追加这两列。
3. **详情页所有点击事件无效** — `<script>` 缺少 `th:inline="javascript"`，`[[${record.videoUrl}]]` 在纯文本模式下输出，若视频URL含特殊字符会破坏整个脚本块语法。修复：改用 `<script th:inline="javascript">`，Thymeleaf 自动 JS 转义。
4. **批量导入检测失败** — `tryReparseVideoUrl()` 用简单正则搜视频扩展名，无法识别 JS 变量嵌入的视频地址（如 `var player_xxx={"url":"..."}`）。而 `/api/check` 使用 `VideoParser.parse()` 可正确解析。修复：`tryReparseVideoUrl` 改用 `PageFetcher.fetch()` + `VideoParser.parse()`（与 `/api/check` 一致），找到视频直链后重新检测并更新 status。

#### 新功能
5. **批量移动到合集** — 列表页勾选视频后出现"移动到合集"按钮，点击弹窗选择目标合集，批量更新。
   - 前端：`index.html` — 新增按钮 + 合集选择弹窗
   - 后端：`PUT /api/group/batch-move-video`（`VideoGroupController.java`）
   - DAO：`VideoRecordDao.batchUpdateGroup()` + Mapper SQL

#### 其他
6. **HTML 搜索过滤优化** — `VideoSourceService.parseHtmlSearchResults()` 增加 `/lty/\d+` 白名单模式 + `isNoiseLink()` 排除导航/地图/搜索翻页等噪音链接。
7. **测试搜索结果可直接导入** — `sources.html` 每行结果加"导入"按钮 → 确认弹窗 → 自动跳转首页启动导入向导。
8. **导入向导骨架屏修复** — `doBatchImport()` 无响应问题修复：`renderImportStep3()` 增加合集选择器，`goImportStep2()` 提前捕获合集选择。
9. **模态框遮挡修复** — `openGroupEdit()` 打开编辑弹窗时先关闭合集列表弹窗，避免被遮挡。

### 操作步骤

无数据库变更，直接重启即可：

```bash
mvn clean package
# 停止旧进程
netstat -ano | findstr :8080
taskkill /PID <PID> /F
# 启动新版本
java -jar target/video-collect-1.0.0.jar
```
