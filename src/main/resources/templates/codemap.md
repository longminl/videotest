# src/main/resources/templates/

## Responsibility
Thymeleaf 模板页面。提供浏览器端用户界面，蓝白配色主题。

## Files

### index.html
- **功能**：视频收藏列表主页
- **布局**：
  - 顶部 TopBar（标题 + 批量删除 + 一键检测 + 收藏按钮）
  - 表格列：# / 标题 / 状态 / 延迟 / 缓存 / 操作 / 来源 / 收录时间
  - 状态列彩色圆点（绿/红/黄/灰）
  - 操作列：播放（可播放时显示）、重新检测、编辑备注、删除
  - 缓存列：HLS 视频显示缓按钮和状态
  - 分页组件 + 搜索/筛选
- **交互**：
  - 批量删除（复选框 + 全选）
  - 缓存按钮 fire-and-forget + 20s 超时自动重试
  - 移动端优化：touch-action:manipulation, 禁用 backdrop-filter/shimer
- **依赖**：Bootstrap 5.3.3, Bootstrap Icons, Plyr 播放器

### detail.html
- **功能**：视频详情页
- **组件**：
  - Plyr 视频播放器 + HLS.js 支持 m3u8 播放
  - 视频信息卡：标题、状态、延迟、来源
  - 缓存进度条（轮询 /api/cache/status）
  - 备注编辑框
  - 下载按钮
- **依赖**：Plyr 3.7.8, HLS.js 1.5.17, Bootstrap
