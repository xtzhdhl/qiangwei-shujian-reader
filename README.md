# 蔷薇书笺 🌹

一款以法式蕾丝、奶油纸感、珍珠与玫瑰色调为灵感的 Android 小说阅读器。原生 Kotlin + Jetpack Compose，不是网页套壳。

**当前版本：** 0.3.0（远程书源订阅与应用内更新接入阶段）

## Android 应用身份

- Application ID：`com.romanticreader.app`
- 应用名称：蔷薇书笺
- minSdk：26
- targetSdk：35

## 当前实现

- 原生 Compose 首页、书架、发现、我的四个导航页
- 奶油白、雾粉、玫瑰金色系和圆角卡片视觉
- 本地 TXT 导入、书架保存与删除
- 常见文本编码尝试：UTF-8、GB18030、Big5、UTF-16
- 阅读正文滚动、章节标题识别、字号与行距调节、日夜模式
- 阅读位置按段落索引保存
- Legado JSON 书源导入（数组、单源对象及常见包装数组），去重、启用/停用和删除
- HTTPS 远程 JSON 书源订阅、刷新与订阅管理；刷新保留本地启用状态
- GitHub Releases 应用内检查更新、APK 下载、SHA-256 校验（Release asset digest 提供时）和系统安装引导
- Debug APK 自动构建上传

## 当前限制

- 在线书源搜索、网页抓取、书源规则执行、JavaScript 规则兼容尚未实现
- 尚未创建正式 GitHub Release；应用内更新需要发布带 APK 的 Release
- 签名发布需要先配置 keystore 与 GitHub Actions Secrets；首次稳定版可能需要卸载当前 Debug 版再安装
- AI 生成的装饰素材目前尚未全部打包进应用资源
- 阅读设置尚未持久化；字体选择与自定义字体导入正在规划
- 尚未完成真机安装、性能与横屏/平板验收

## 路线

1. 视觉资源：生成并整合封面、蕾丝装饰、空状态插画与原创图标。
2. 阅读核心：章节目录、书签、阅读历史、分页模式、设置持久化、字体库。
3. 书源能力：规则引擎、搜索、详情、目录、正文解析，逐步兼容常见 Legado 规则。
4. 个人数据：备份恢复、隐私与本地数据安全。
5. 真机与发布验收。

## GitHub Actions

- `.github/workflows/android.yml`：Debug APK 自动构建
- `.github/workflows/release.yml`：签名 Release（tag `v*` 或手动触发）

## 应用内更新的签名准备

在 GitHub 仓库 Settings → Secrets and variables → Actions 中配置：

- `SIGNING_KEYSTORE_BASE64`：Keystore 文件的 Base64
- `RELEASE_STORE_PASSWORD`：Keystore 密码
- `RELEASE_KEY_ALIAS`：密钥别名
- `RELEASE_KEY_PASSWORD`：密钥密码

不要将 keystore 或密码提交到仓库。第一份稳定签名 APK 安装后，未来更新必须一直使用同一个 keystore。
