<!-- Parent: ../AGENTS.md -->
<!-- Generated: 2026-04-30 -->

# TiDaoCookieReader

## 用途

独立 Android 子仓库，从已登录天刀助手（`com.tencent.gamehelper.wuxia`）的私有数据中读取 Cookie 和角色信息，无需抓包。App 依赖 Root 权限复制天刀助手 WebView Cookie 数据库和游戏角色数据库，再上传到根仓库 FC 的 `update-cookie` HTTP 函数。

## 关键文件

| 文件 | 说明 |
|------|------|
| `app/src/main/java/com/tidao/wuxia/app/ui/MainActivity.java` | 主界面：读取/上传编排、Server酱绑定、邮箱弹窗、角色选择、更新下载 |
| `app/src/main/java/com/tidao/wuxia/app/AutomationReceiver.java` | ADB 广播接收器：自动化触发读取/复制/检测/上传 |
| `app/src/main/java/com/tidao/wuxia/app/cookie/WebViewCookieReader.java` | su 复制并读取 WebView Cookie 数据库（access_token、openid、uin 等） |
| `app/src/main/java/com/tidao/wuxia/app/cookie/GameDatabaseReader.java` | su 遍历数字数据库并读取角色信息（按 uin 定位，支持多角色选择） |
| `app/src/main/java/com/tidao/wuxia/app/cookie/BindingChecker.java` | AMS 974294 登录验证接口检测每日福利绑定状态 |
| `app/src/main/java/com/tidao/wuxia/app/cookie/CookieExtractor.java` | 旧流量解析器；`CookieData` 数据类仍被全项目复用 |
| `app/src/main/java/com/tidao/wuxia/app/net/FcUploader.java` | 构建上传 JSON 并 POST 到 `BuildConfig.UPLOAD_COOKIE_URL`，处理 400/403/其他 HTTP 错误 |
| `app/src/main/java/com/tidao/wuxia/app/net/ServerChanBinder.java` | WebView 弹窗绑定 Server酱 sendkey，回调中验证 sckey 有效性 |
| `app/src/main/java/com/tidao/wuxia/app/data/PrefsManager.java` | SharedPreferences 存储 sckey、owner、email |
| `app/src/main/java/com/tidao/wuxia/app/utils/UpdateChecker.java` | 双源更新检查：云函数优先（国内 CDN），GitHub API 回退 |
| `app/src/main/java/com/tidao/wuxia/app/utils/VersionUtils.java` | 纯 Java 版本号解析与比较，无 Android 运行时依赖，可供单元测试直接使用 |
| `app/src/main/java/com/tidao/wuxia/app/utils/RootChecker.java` | Root 权限检测（su 命令、常见路径、test-keys） |
| `app/build.gradle` | Android 配置、版本号（baseVersion 1.3）、BuildConfig 注入 |
| `settings.gradle` | Maven 仓库顺序：官方源优先，阿里云镜像兜底 |
| `app/debug.keystore` | 统一 Debug 签名证书，允许入库，保证本地与 CI APK 签名一致 |

## 子目录

| 目录 | 用途 |
|------|------|
| `app/src/main/java/com/tidao/wuxia/app/cookie/` | Cookie 与角色读取模块：WebViewCookieReader、GameDatabaseReader、BindingChecker、CookieExtractor |
| `app/src/main/java/com/tidao/wuxia/app/data/` | 本地存储：PrefsManager（sckey、owner、email） |
| `app/src/main/java/com/tidao/wuxia/app/net/` | 网络上传：FcUploader（JSON POST）、ServerChanBinder（WebView sendkey 绑定） |
| `app/src/main/java/com/tidao/wuxia/app/ui/` | 界面：MainActivity |
| `app/src/main/java/com/tidao/wuxia/app/utils/` | 工具：UpdateChecker、VersionUtils、RootChecker |
| `app/src/test/` | JUnit 单元测试：UpdateCheckerTest、FcUploaderTest、MainActivityEmailTest |
| `.github/workflows/` | CI/CD：android-build（验证+构建+冒烟）、android-publish（latest Release）、android-release（tag Release） |
| `.github/actions/` | 复用 Action：android-build（构建 APK + BuildConfig 注入）、android-smoke-test（模拟器冒烟） |
| `scripts/` | 脚本：validate-file-paths.sh、smoke-test.sh、upload-oss-artifact.sh |

## AI 代理指引

### 在此目录工作

- **子仓库有独立 .git**，提交必须 `cd TiDaoCookieReader` 后单独操作，不能在根仓库直接提交子仓库变更。
- **构建需要 Java 17**：AGP 8.5.0 + JDK 17；本地只有 Java 8 时 `assembleDebug` 会失败。GitHub Actions 使用 Temurin 17。
- **Maven 仓库顺序不可颠倒**：`settings.gradle` 必须 `google()` > `mavenCentral()` > `gradlePluginPortal()` > 阿里云镜像。阿里云放前面在 GitHub Actions 上会遇 502 导致依赖解析失败。
- **BuildConfig 注入**：`FC_URL`、`CLOUD_RELEASES_PAGE_URL`、`UPLOAD_COOKIE_URL` 由 CI 通过环境变量注入。本地构建为空串时，更新检查自动回退 GitHub 通道；上传功能需要 CI 构建才能使用。
- **Root 是硬性依赖**：App 通过 `su` 访问天刀助手私有数据（Cookie DB + 角色 DB），无 Root 则核心功能不可用。

### 测试要求

```powershell
# 单元测试（本地 JVM，不需要模拟器）
.\gradlew.bat testDebugUnitTest

# Debug 构建（需要 Java 17）
.\gradlew.bat assembleDebug

# Release 构建（需要签名参数）
.\gradlew.bat assembleRelease
```

### 常见模式

- **异步回调模式**：WebViewCookieReader、GameDatabaseReader、BindingChecker、FcUploader、ServerChanBinder、UpdateChecker 均使用 `new Thread()` + `Handler.post()` 模式做异步操作并在主线程回调。同步版本用 `synchronized(lock) { lock.wait(timeout) }` 阻塞等待。
- **su 命令模式**：Cookie 和角色读取通过 `su -c "cat ... > /data/local/tmp/..."` 复制私有数据库到临时位置，Java 侧再打开只读副本。WAL 模式数据库会同时复制 `-wal` 和 `-shm` 文件。操作完成后清理临时文件。
- **上传 JSON 格式**：FcUploader 构建的 JSON 必须包含 `name`、`cookies`、`role_params`、`sckey`、`owner`、`email`。修改此格式时必须同步根仓库 `fc_handler.update_cookie_handler` 和测试。
- **email 语义**：缺少 `email` 字段表示保留旧邮箱；`email: ""` 表示清空邮箱；非空邮箱会先测试发送才保存。
- **AMS 响应解析**：BindingChecker 使用字符串匹配（`contains("\"iRet\":\"...\"")`）而非 JSONObject 解析 AMS 响应，因为 AMS 嵌套 JSON 结构不稳定。
- **账号名脱敏**：FcUploader 日志中使用 `maskName()`（首字符 + `***`），截断响应体到 200 字符。
- **版本比较**：UpdateChecker 使用 VersionUtils 做语义版本比较 + 构建号比较，支持 `x.y.z` 格式，忽略 prerelease 后缀。

### 修改注意事项

- 不要提交 release keystore、真实 URL Secret、账号 Cookie、sendkey、SMTP 授权码。
- `app/debug.keystore` 是统一 Debug 签名证书，允许入库，用于本地和 CI 的 Debug APK 签名一致。
- 修改上传 JSON 时必须同步根仓库 `fc_handler.update_cookie_handler` 和测试。
- 修改更新检查逻辑时同步 `UpdateCheckerTest`。
- 修改 CI 源或 Gradle 配置后，确认 Android CI 和 Android Publish 都成功。
- 默认可直接维护 `main`；大改动建议临时分支，小修可直接推。

## 依赖

### 内部

- 根仓库 `fc_handler.py` 的 `update_cookie_handler`：接收上传 JSON，处理账号审批和状态同步。
- 根仓库 `config/accounts.json`：上传后写入账号配置。
- 根仓库 OSS：CI 发布 APK 时上传到阿里云 OSS。

### 外部

- Android Gradle Plugin 8.5.0
- Gradle Wrapper 8.7
- JDK 17（Temurin）
- minSdk 26 / targetSdk 34 / compileSdk 34
- applicationId: `com.tidao.wuxia.app`
- androidx.appcompat:appcompat 1.6.1
- com.google.android.material:material 1.11.0
- androidx.constraintlayout:constraintlayout 2.1.4
- JUnit 4.13.2
- AMS API（`comm.ams.game.qq.com`）：绑定检测用 FlowID 974294
- Server酱 API（`sct.ftqq.com`、`sctapi.ftqq.com`）：sendkey 绑定与验证

## CI/CD

### Android CI — `.github/workflows/android-build.yml`

push `main/master` 或 PR 时运行（排除 `*.md` 和 `docs/**`）：

1. `validate`：运行 `scripts/validate-file-paths.sh` 校验 Android 资源路径。
2. `build`：调用 `.github/actions/android-build` 构建 Debug APK + 上传 artifact。
3. `smoke-test`：调用 `.github/actions/android-smoke-test` 在 API 30 x86_64 模拟器上冒烟测试。

### Android Publish — `.github/workflows/android-publish.yml`

Android CI 成功后通过 `workflow_run` 触发（仅 `main/master` push）：

1. `git diff` 路径检查：仅 `app/`、`build.gradle`、`scripts/` 等代码路径变更时继续。
2. 构建 Debug APK（注入 Secrets：`FC_URL`、`CLOUD_RELEASES_PAGE_URL`、`UPLOAD_COOKIE_URL`）。
3. 删除并重建 `latest` Release，上传 APK。
4. 上传 APK 与 `version.json` 到阿里云 OSS。

### Android Release — `.github/workflows/android-release.yml`

tag `v*` 或手动触发：

1. 使用 Secrets 注入 release keystore 构建 Release APK。
2. 创建 GitHub Release（`softprops/action-gh-release`）。
3. 上传 Release APK 到阿里云 OSS。

### 复用 Action

- `.github/actions/android-build/action.yml`：设置 JDK 17 + Gradle，构建 APK，支持 Debug/Release 变体、BuildConfig 注入、release 签名。
- `.github/actions/android-smoke-test/action.yml`：构建 Debug APK + SrgGactionEmulatorenvsetupAndroid 模拟器冒烟测试（API 30，5 分钟超时）。

## 核心数据流

```text
用户在天刀助手扫码登录并打开活动页
        ↓
WebViewCookieReader su 复制并读取 WebView Cookie DB
        ↓
GameDatabaseReader su 遍历数字数据库读取角色信息（按 uin 定位）
        ↓
多角色时弹出选择对话框，单角色直接使用
        ↓
BindingChecker 调 AMS 974294 检测每日福利绑定状态
        ↓
MainActivity 绑定/验证 Server酱 sendkey + 可选邮箱
        ↓
FcUploader POST JSON 到 BuildConfig.UPLOAD_COOKIE_URL
```

上传 JSON 必须包含：`name`、`cookies`、`role_params`、`sckey`、`owner`、`email`（可空字符串）。

## ADB 自动化

广播 Action（`AutomationReceiver` 接收，结果输出到 Logcat 和 `cacheDir/result.txt`）：

```bash
adb shell am broadcast -a com.tidao.wuxia.app.action.READ_COOKIE
adb shell am broadcast -a com.tidao.wuxia.app.action.COPY_ALL
adb shell am broadcast -a com.tidao.wuxia.app.action.CHECK_WELFARE
adb shell am broadcast -a com.tidao.wuxia.app.action.GET_STATUS
adb shell am broadcast -a com.tidao.wuxia.app.action.UPLOAD_COOKIE
```