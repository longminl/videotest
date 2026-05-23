# android/gradle/app/src/main/res/

## Responsibility
Android 资源文件。包含主题、字符串、启动图标等。

## Files

### AndroidManifest.xml
- 权限：INTERNET, ACCESS_NETWORK_STATE
- Application：VideoCollectApp, clearTextTraffic=true, networkSecurityConfig
- Activity：MainActivity（LAUNCHER）, PlayerActivity（Fullscreen 主题）

### values/themes.xml
- `Theme.VideoCollect` — Material Light NoActionBar + 透明状态栏/导航栏

### values/strings.xml
- 应用名等字符串资源

### mipmap / drawable
- 启动图标（ic_launcher）

### xml/network_security_config.xml
- 允许明文 HTTP 访问（用于局域网服务器连接）
