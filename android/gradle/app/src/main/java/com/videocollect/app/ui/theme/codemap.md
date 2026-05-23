# android/gradle/app/src/main/java/com/videocollect/app/ui/theme/

## Responsibility
Material3 主题定义。蓝白配色方案，支持暗黑模式。

## Files

### Color.kt
- **Primary Blue**：Blue600=#2563EB, Blue500=#3B82F6, Blue400=#60A5FA, Blue100=#DBEAFE
- **Cyan Accent**：Cyan500=#06B6D4, Cyan400=#22D3EE
- **Background**：CoolBg=#F0F5FF（亮）/ #0F172A（暗）
- **Surface**：SurfaceWhite=#FFFFFF / SurfaceDark=#1E293B
- **Text**：TextPrimary=#1E293B / TextSecondary=#64748B / TextTertiary=#94A3B8
- **Status**：Green=#22C55E, Red=#EF4444, Orange=#F59E0B, Gray=#94A3B8, Blue=#3B82F6
- **Gradient**：GradientStart=#2563EB → GradientEnd=#06B6D4

### Theme.kt
- `LightColorScheme` — 亮色主题：蓝白配色
- `DarkColorScheme` — 暗色主题：深蓝灰背景
- `VideoCollectTheme()` — 根据系统暗黑模式自动切换
- `SideEffect`：设置状态栏透明 + 适配亮/暗图标
