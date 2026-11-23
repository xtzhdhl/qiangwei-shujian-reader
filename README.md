# 蔷薇书笺

一款以法式蕾丝、奶油纸感与玫瑰色调为灵感的 Android 小说阅读器。

**当前版本：** 0.1.0（早期功能原型）

## 项目目标

- 适配 OnePlus 12 竖屏与 Android 平板横屏
- 奶油白、雾粉、香槟金的原创视觉语言
- 本地书架、TXT 导入、阅读进度与书签
- Legado 风格 JSON 书源导入与基础规则解析
- 字号、行距、段落、主题和自定义字体
- 内置版本检查、SHA-256 校验、下载与 Android 系统覆盖安装更新

## Android 应用身份

- Application ID：`com.romanticreader.app`
- 应用名称：蔷薇书笺
- 首发签名证书必须妥善备份；后续版本必须使用相同 applicationId 和签名证书，才能覆盖升级并保留数据。

## 更新机制

正式发布前，应用内更新清单地址保持未配置。更新清单采用 HTTPS JSON，字段包括：

- `versionCode`
- `versionName`
- `apkUrl`
- `sha256`
- `releaseNotes`

下载后先校验 SHA-256，再交由 Android 系统安装器处理升级。普通应用更新仍需要用户确认系统安装提示。

## 项目状态

本仓库将保存 Android 工程与云端构建配置。当前工程尚未完成真实设备安装测试；书源解析仅覆盖常见规则子集，不等于完整兼容所有 Legado 规则。
