# 蔷薇书笺 🌹

一款以法式蕾丝、奶油纸感、珍珠与玫瑰色调为灵感的 Android 小说阅读器。原生 Kotlin + Jetpack Compose，不是网页套壳。

**当前版本：** 0.3.0（远程书源订阅与应用内更新接入阶段）

## Android 应用身份

- Application ID：`com.romanticreader.app`
- 应用名称：蔷薇书笺
- minSdk：26
- targetSdk：35
- 首发签名证书必须妥善备份；后续正式升级必须使用相同 applicationId 与签名证书。

## 当前实现

- 原生 Compose 首页、书架、发现、我的四个导航页
- 奶油白、雾粉、玫瑰金色系和圆角卡片视觉
- 本地 TXT 导入、书架保存与删除
- 常见文本编码尝试：UTF-8、GB18030、Big5、UTF-16
- 阅读正文滚动、章节标题识别、字号与行距调节、日夜模式
- 阅读位置按段落索引保存
- Legado 常见 JSON 书源导入（数组或单源对象）、去重、启用/停用和删除
- 书源原始 JSON 数据保留在本地，便于后续规则引擎接入
- GitHub Actions 自动构建 Debug APK 并上传 Artifact

## 当前限制

- 在线书源搜索、网页抓取、书源规则执行、JavaScript 规则兼容尚未实现\n- 尚未创建正式 GitHub Release；应用内更新需要发布带 APK 的 Release\n- 签名发布需要先配置 keystore 与 GitHub Secrets，首次稳定版可能需要卸载当前 Debug 版再安装
- AI 生成的装饰素材目前处于视觉概念阶段，尚未全部打包进应用资源
- 阅读设置尚未持久化；字体选择与自定义字体导入正在规划
- 尚未完成真机安装、性能与横屏/平板验收

## 计划路线

1. **视觉与阅读体验**：AI 生成并整合封面、蕾丝装饰、空状态插画；主题切换与字体管理。
2. **阅读核心**：章节识别、目录、书签、阅读历史、分页模式、阅读设置持久化。
3. **书源能力**：Legado JSON 规则模型、网络请求、搜索、书籍详情、目录、正文解析；逐步支持常见规则子集。
4. **个人数据**：备份与恢复、隐私与本地存储安全。
5. **发布验收**：真机与平板适配、稳定构建、版本升级机制。

## 构建

GitHub Actions workflows：\n- `.github/workflows/android.yml`：Debug APK 自动构建\n- `.github/workflows/release.yml`：签名 Release（tag `v*` 或手动触发）

手动触发或 push 到 main 后构建 Debug APK。构建产物名：`qiangwei-shujian-debug-apk`。

## In-app update / stable signing setup

Create and keep a private Android signing keystore. Configure these repository Actions secrets before running the signed release workflow:

- `SIGNING_KEYSTORE_BASE64`: Base64-encoded keystore file
- `RELEASE_STORE_PASSWORD`: keystore password
- `RELEASE_KEY_ALIAS`: key alias
- `RELEASE_KEY_PASSWORD`: key password

Never commit the keystore or passwords. Once the first signed release is installed, subsequent releases must use this exact keystore.
