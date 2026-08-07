# AGENTS.md — WMS 扫码枪浏览器约定

## 项目定位
公司扫码枪（安卓 6.0）专用浏览器，进入内网 Infor WMS（wms.pantum.com）。
用户是工厂仓储/计划岗，扫码枪系统老（安卓 6）。

## 硬性约束
- **minSdk 必须 23**（安卓 6.0）——扫码枪系统老，别擅自提高。
- 内网访问，域名 wms.pantum.com 外部不可达（测试实际加载需扫码枪局域网）。
- WMS 是 Infor 系统，网页版入口即 wms.pantum.com。
- **usesCleartextTraffic=true**（内网常是 http）；WebView 信任自签证书。
- 扫码枪扫描模式：默认"模拟键盘输入"（WebView 原生接收）；若某枪是广播模式需加
  Intent 接收器（现状未加，等用户确认）。

## 架构
- 纯 WebView（无 Compose/三方浏览器库），Kotlin + AppCompat。
- MainActivity：WebView 加载固定 URL + 全屏 + 常亮 + 自动聚焦输入框 + 返回后退。
- targetSdk 36，但 minSdk 23 —— WebView 用系统自带（安卓 6 的 WebView 是旧 Chromium，
  现代 WMS 网页可能有兼容问题，酌情用 WebView 兼容设置/降级策略）。

## 已知注意
- 安卓 6 的 WebView 旧（Chromium ~50 代），若 WMS 现代 JS 打不开，可能需要内置
  更新的 WebView 或申报系统 WebView。待真机验证。
- 图标用 vector drawable（minSdk 21+ 可用），不用 adaptive-icon（26+才生效）。
- 本机无 Android SDK，构建靠 GitHub Actions（assembleDebug → artifact）。
- git push 用 SSH remote（HTTPS 无凭据）。