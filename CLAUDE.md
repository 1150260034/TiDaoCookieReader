# CLAUDE.md

本文件为 Claude Code / AI agent 在 `TiDaoCookieReader` 子仓库工作时提供上下文。该目录是独立 Git 仓库，提交、推送、CI 都与根仓库分开。

## 项目概述

天刀助手 Cookie 读取器，从已登录天刀助手（`com.tencent.gamehelper.wuxia`）的私有数据中读取 Cookie 和角色信息，无需抓包。App 依赖 Root 权限复制天刀助手 WebView Cookie 数据库和游戏角色数据库，再上传到根仓库 FC 的 `update-cookie` HTTP 函数。

当前能力：

- 读取天刀助手 Cookie。
- 读取角色信息，支持多角色选择。
- 检测每日福利绑定状态。
- 绑定 Server酱 sendkey。
- 可选绑定邮箱，Cookie 失效时后端额外邮件提醒。
- 上传 Cookie、角色、sckey、owner、email 到 FC。
- 应用内更新检查：云函数/云端页面优先，GitHub Releases 回退。

## 构建命令

```powershell
# 查看状态
git status --short --branch

# 单元测试
.\gradlew.bat testDebugUnitTest

# Debug 构建
.\gradlew.bat assembleDebug

# Release 构建（需要签名参数）
.\gradlew.bat assembleRelease
```

技术版本：

- Android Gradle Plugin: 8.5.0
- Gradle Wrapper: 8.7
- JDK: 17（GitHub Actions 使用 Temurin 17；Java 8 会失败）
- minSdk: 26
- targetSdk / compileSdk: 34
- applicationId: `com.tidao.wuxia.app`

## 项目结构

```text
TiDaoCookieReader/
├── app/src/main/java/com/tidao/wuxia/app/
│   ├── ui/MainActivity.java          # 主界面、读取/上传编排、邮箱入口
│   ├── AutomationReceiver.java       # ADB 自动化广播
│   ├── cookie/
│   │   ├── WebViewCookieReader.java  # su 读取 WebView Cookie 数据库
│   │   ├── GameDatabaseReader.java   # su 读取角色数据库
│   │   ├── BindingChecker.java       # AMS 974294 绑定/失效检查
│   │   └── CookieExtractor.java      # 旧流量解析器；CookieData 仍复用
│   ├── data/PrefsManager.java        # sckey、owner、email 本地偏好
│   ├── net/FcUploader.java           # 上传 JSON 到 FC
│   ├── net/ServerChanBinder.java     # Server酱绑定
│   └── utils/UpdateChecker.java      # 双源更新检查
├── app/src/test/                     # JUnit 单元测试
├── .github/actions/                  # 复用构建/冒烟测试 action
├── .github/workflows/                # CI、latest 发布、release 发布
├── scripts/                          # file_paths 校验、冒烟测试、OSS 上传
├── app/build.gradle                  # Android 配置、版本、BuildConfig
└── settings.gradle                   # Maven 仓库顺序
```

## 核心数据流

```text
用户在天刀助手扫码登录并打开活动页
        ↓
WebViewCookieReader 复制并读取 Cookie DB
        ↓
GameDatabaseReader 读取 Role 表并选择角色
        ↓
BindingChecker 调 AMS 974294 检测绑定状态
        ↓
MainActivity 绑定/读取 Server酱与可选邮箱
        ↓
FcUploader POST 到 BuildConfig.UPLOAD_COOKIE_URL
```

上传 JSON 必须包含：

- `name`
- `cookies`
- `role_params`
- `sckey`
- `owner`
- `email`（可空字符串）

后端语义：缺少 `email` 表示保留旧邮箱；空字符串表示清空邮箱；非空邮箱会先测试发送成功才保存。

## 关键组件

- **MainActivity**：UI、按钮状态、读取流程、上传流程、邮箱弹窗和本地轻量校验。
- **PrefsManager**：保存/读取/清除 `sckey`、`owner`、`email`。
- **FcUploader**：构建上传 JSON，处理 400/403/其他 HTTP 错误；sendkey 失效和邮箱测试失败要给友好提示。
- **ServerChanBinder**：绑定 sendkey，Server酱仍是必填主通道。
- **WebViewCookieReader**：通过 `su` 访问 `/data/data/com.tencent.gamehelper.wuxia/app_webview/Default/Cookies`。
- **GameDatabaseReader**：遍历天刀助手数据库并按 uin 读取角色信息。
- **UpdateChecker**：云端更新通道优先，GitHub Releases 回退。

## CI/CD

### Android CI: `.github/workflows/android-build.yml`

push `main/master` 或 PR 时运行：

- `scripts/validate-file-paths.sh`
- Debug APK 构建
- 模拟器冒烟测试
- Debug APK artifact 上传

### Android Publish: `.github/workflows/android-publish.yml`

Android CI 成功后触发：

- 仅代码/构建/脚本路径变更时发布。
- 构建 Debug APK。
- 删除并重建 `latest` Release。
- 上传 APK 与 `version.json` 到 OSS。

### Android Release: `.github/workflows/android-release.yml`

tag `v*` 或手动触发：

- 使用 Secrets 注入 release keystore。
- 构建 Release APK。
- 创建 GitHub Release。
- 上传 OSS。

### Maven 源

`settings.gradle` 必须保持官方源优先：

1. `google()`
2. `mavenCentral()`
3. `gradlePluginPortal()`
4. 阿里云镜像兜底

不要把阿里云镜像放在官方源前面；GitHub Actions 上遇到镜像 502 会导致依赖解析失败。

## BuildConfig 注入

CI 通过 `.github/actions/android-build` 注入：

- `FC_URL`: 云函数更新检查 URL。
- `CLOUD_RELEASES_PAGE_URL`: 云端更新详情页 URL。
- `UPLOAD_COOKIE_URL`: Cookie 上传 URL。

本地为空时，更新检查自动回退 GitHub 通道；上传功能需要构建时注入有效 URL 才能使用。

## ADB 自动化

广播 Action：

```bash
adb shell am broadcast -a com.tidao.wuxia.app.action.READ_COOKIE
adb shell am broadcast -a com.tidao.wuxia.app.action.COPY_ALL
adb shell am broadcast -a com.tidao.wuxia.app.action.CHECK_WELFARE
adb shell am broadcast -a com.tidao.wuxia.app.action.GET_STATUS
```

结果输出到 Logcat 和 `cacheDir/result.txt`。

## 维护规则

- 默认可直接维护 `main`；大改动建议临时分支，小修可直接推。
- 不要提交 release keystore、真实 URL Secret、账号 Cookie、sendkey、SMTP 授权码。
- `app/debug.keystore` 是统一 Debug 签名证书，允许入库，用于本地和 CI Debug APK 覆盖安装一致。
- 修改上传 JSON 时必须同步根仓库 `fc_handler.update_cookie_handler` 和测试。
- 修改更新检查时同步 `UpdateCheckerTest`。
- 修改 CI 源或 Gradle 配置后，确认 Android CI 和 Android Publish 都成功。

## 响应偏好

请始终使用简体中文回答本仓库相关问题。技术名词优先中文，必要时补英文。代码注释、提交说明、排查建议默认中文。

