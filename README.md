# WMS 扫码枪浏览器

公司扫码枪（安卓 6.0）专用浏览器，进入内网 Infor WMS 系统（wms.pantum.com）。

## 功能
- 全屏 WebView 加载内网 WMS（https://wms.pantum.com）
- 内网自签名 https 证书信任
- 屏幕常亮（连续扫码作业）
- 页面加载完成自动聚焦输入框（扫码枪扫完直接进框）
- 软键盘默认永不自动弹出（仅手点输入框才显示）
- **广播模式**：监听扫码枪广播（Zebra/Honeywell/国产通用 action），条码自动填入当前输入框并触发 input/change 事件
- 支持 alert/prompt（WMS 弹窗）
- 硬件返回键 = WebView 后退

## 兼容性
- **minSdk 23**（安卓 6.0，扫码枪系统）
- targetSdk 36 / compileSdk 36 / AGP 8.11.1 / Kotlin 2.0.21 / Gradle 9.4.1

## 构建
```bash
./gradlew assembleDebug   # 本地
# 或推 main，GitHub Actions 自动构建 APK artifact
```

## 注意
- wms.pantum.com 是内网地址，需在扫码枪所在局域网访问
- 扫码枪的扫描模式若是"模拟键盘"（默认），WebView 原生接收；若是广播模式需另加接收器
