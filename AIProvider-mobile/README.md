# AIProvider Mobile

## 目标设备

- 唯一适配机型：iPhone 15 Pro Max。
- 竖屏 CSS 视口：430 × 932。
- WebView 必须保留 `viewport-fit=cover`，页面负责避让顶部灵动岛安全区和底部 Home Indicator 安全区。
- 手机 APP 只打开生产地址 `http://35.78.120.126/mobile/`，继续共用现有 `/api/` 与 `/ws/`。

当前移动端不以其他 Android 或 iPhone 机型作为验收目标。
