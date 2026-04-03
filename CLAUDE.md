# CLAUDE.md

本文件用于为 Claude Code（claude.ai/code）在处理本仓库代码时提供指导说明。

## 项目概述

天刀助手 Cookie 读取器，从已登录天刀助手 (com.tencent.gamehelper.wuxia) 的 WebView 中提取登录 Cookie 和角色信息，无需抓包。

**注意：实际 App 通过 Root 权限直接读取天刀助手私有目录中的数据库，而非代理/VPN 抓包。README.md 旧版描述了已废弃的代理抓包方式，如发现不一致以本文件为准。**

## 构建命令

```bash
# 本地开发
./gradlew assembleDebug      # 调试 APK
./gradlew assembleRelease    # 发布 APK

# CI 环境（android-build.yml 使用）
gradle --no-daemon assembleDebug
```

## 技术参数

- AGP 版本: 8.5.0，Gradle: 8.7
- 最低 Android: API 26 (Android 8.0)，目标: API 34 (Android 14)
- 包名: `com.tidao.wuxia.app`
- 天刀助手包名: `com.tencent.gamehelper.wuxia`
- 阿里云镜像: `https://maven.aliyun.com/repository/google` 等

## 项目结构

```text
TiDaoCookieReader/
├── app/src/main/java/com/tidao/wuxia/app/
│   ├── ui/MainActivity.java          # 主界面（含应用内更新下载安装）
│   ├── AutomationReceiver.java       # ADB 广播接收器（自动化测试用）
│   ├── cookie/
│   │   ├── WebViewCookieReader.java  # 通过 su 读取天刀助手 WebView Cookie 数据库
│   │   ├── GameDatabaseReader.java   # 通过 su 读取游戏数据库 Role 表
│   │   ├── BindingChecker.java       # 调用 AMS API 检测每日福利绑定状态
│   │   └── CookieExtractor.java      # HTTP 流量解析逻辑已不再使用，但 CookieData 仍作为公共数据结构被复用
│   └── utils/
│       ├── RootChecker.java          # Root 权限检测
│       └── UpdateChecker.java        # 双源更新检查（云函数优先，回退 GitHub Releases）
├── app/src/main/res/
│   ├── layout/activity_main.xml
│   └── xml/file_paths.xml            # FileProvider 路径配置（APK 安装授权）
├── .github/
│   ├── actions/
│   │   ├── android-build/            # 复用构建 action（JDK + Gradle + 签名 + BuildConfig 注入）
│   │   └── android-smoke-test/       # 复用冒烟测试 action（模拟器 + APK 安装 + 启动检查）
│   └── workflows/
│       ├── android-build.yml         # CI：校验 + 构建 + 冒烟测试
│       ├── android-publish.yml       # CD：CI 成功后发布 latest release + OSS 上传
│       └── android-release.yml       # Release：打 tag 后构建签名 APK + OSS 上传
├── scripts/
│   ├── upload-oss-artifact.sh    # OSS 上传脚本（ossutil 安装 + APK/version.json 上传）
│   ├── validate-file-paths.sh    # file_paths.xml 静态校验
│   └── smoke-test.sh             # ADB 冒烟测试脚本
├── build.gradle                       # AGP 8.5.0
└── settings.gradle                   # 使用阿里云镜像加速 Gradle 依赖
```

## 架构

### 核心组件

- **MainActivity** — UI 交互、业务编排、多角色选择对话框
- **AutomationReceiver** — ADB 广播接收器，支持 `READ_COOKIE`、`COPY_ALL`、`CHECK_WELFARE`、`GET_STATUS` 四个 Intent Action（后缀名，完整值为 `com.tidao.wuxia.app.action.<ACTION>`），结果输出到 Logcat 和 `cacheDir/result.txt`
- **WebViewCookieReader** — 通过 `su` 复制天刀助手 WebView Cookie 数据库 (`/data/data/com.tencent.gamehelper.wuxia/app_webview/Default/Cookies`) 到临时文件后用 Java SQLite 读取。关键路径: `/data/local/tmp/tidao_cookies.db`
- **GameDatabaseReader** — 遍历天刀助手 `databases/*.db`，用 sqlite3 命令查询 `Role WHERE f_uin=?` 定位正确数据库，读取多角色信息。临时路径: `/data/local/tmp/tidao_game.db`
- **BindingChecker** — 调用 AMS 登录验证接口 (FlowID 974294) 检查账号是否已绑定游戏角色。iRet=101 表示 Cookie 失效
- **CookieExtractor** — 旧版 HTTP 流量解析器（本地代理模式），代码保留但 MainActivity 已不使用
- **RootChecker** — 三种方式检测 Root: su 命令存在性、常见 root 路径、build.tags 含 test-keys
- **UpdateChecker** — 双源更新检查：云函数优先（国内 CDN 加速），失败时自动回退 GitHub API。支持版本号和构建号（versionCode）两维度比较，云函数和 GitHub 通道均使用统一的 `hasNewerBuildCode()` 方法。云函数通道使用 `CLOUD_RELEASES_PAGE_URL` 作为详情页，GitHub 通道使用 release 自带的 `html_url`

### 数据流向

```text
用户操作流程:
天刀助手扫码登录 → 点击「周周载愿」
        ↓
WebViewCookieReader (su 读 Cookie DB) ──→ CookieData
        ↓
GameDatabaseReader (su 读 Role 表) ──→ RoleInfo (支持多角色选择)
        ↓
BindingChecker (AMS API 974294) ──→ 每日福利绑定状态
        ↓
MainActivity 整合 → copyAll() → 剪贴板
```

### 目标服务器

- Cookie 数据来源: `ams.game.qq.com`, `comm.ams.game.qq.com`, `comm.aci.game.qq.com`, `apps.game.qq.com`
- AMS API: `https://comm.ams.game.qq.com/ams/ame/amesvr`
- Activity ID: `579009`，FlowID: `974294`

### 关键 URL

- 天刀助手下载地址: `https://sj.qq.com/appdetail/com.tencent.gamehelper.wuxia`
- 每日福利活动页: `https://wuxia.qq.com/lbact/a20230821lbapqam/flttl.html`

## 工作原理

1. 通过 `su` 命令将天刀助手私有目录中的 WebView Cookie 数据库复制到 `/data/local/tmp/tidao_cookies.db`
2. 直接用 Java SQLite 读取数据库，提取目标域名的 Cookie
3. 同样方式将游戏数据库复制到 `/data/local/tmp/tidao_game.db`，查询 `Role` 表获取角色信息
4. 调用 `https://comm.ams.game.qq.com/ams/ame/amesvr` (FlowID 974294) 检测绑定状态；`iRet=101` 表示 Cookie 失效

## ADB 自动化

App 暴露了四个广播 Action 用于自动化，结果输出到 Logcat 和 `cacheDir/result.txt`：

```bash
adb shell am broadcast -a com.tidao.wuxia.app.action.READ_COOKIE
adb shell am broadcast -a com.tidao.wuxia.app.action.COPY_ALL
adb shell am broadcast -a com.tidao.wuxia.app.action.CHECK_WELFARE
adb shell am broadcast -a com.tidao.wuxia.app.action.GET_STATUS
```

## CI/CD

- **android-build.yml** — push 到 main/master 或 PR 时先校验 `file_paths.xml`，再构建调试 APK，并调用复用的 Android 模拟器冒烟 action 执行 `connectedDebugAndroidTest` 与主界面启动检查
- **android-publish.yml** — 仅当 `app/`、`build.gradle`、`settings.gradle`、`gradle.properties`、`gradle/**`、`gradlew`、`gradlew.bat`、`scripts/` 变更时触发。该流程**复用**上游 CI（android-build.yml）的校验与冒烟测试结果，不会重新执行 file_paths.xml 校验或 emulator 冒烟测试，仅在主分支 CI 通过后发布 GitHub release（tag 固定为 `latest`，非 prerelease，幂等），最后调用 `scripts/upload-oss-artifact.sh` 上传 APK 及 `version.json` 到阿里云 OSS。
- **android-release.yml** — 打 tag (`v*`) 时构建 release APK，提取 versionName/versionCode 输出到 GITHUB_OUTPUT，用 `softprops/action-gh-release` 创建 GitHub Release，并调用同一 OSS 上传脚本发布
- **scripts/upload-oss-artifact.sh** — 统一 OSS 上传脚本，自动规范化 `OSS_REGION` 格式（兼容 `cn-hangzhou` 和 `oss-cn-hangzhou`），安装 ossutil、直传 APK 到 OSS（通过自定义域名 CNAME 绕过 `ApkDownloadForbidden` 拦截）、生成并上传 `version.json`（downloadUrl 使用 HTTPS 自定义域名 `oss-tiandao.zyzl.link`）

### BuildConfig 注入

CI 通过 `.github/actions/android-build` 复用 action 注入以下 BuildConfig 字段：
- `API_TOKEN` — 云函数请求来源标识（有意编译进 APK，本身仅为标识用途）
- `FC_URL` — 云函数 HTTP URL
- `CLOUD_RELEASES_PAGE_URL` — 云端更新详情页 URL（未配置时回退到 GitHub Releases）

## 分支规范

新功能开发须在独立分支上进行，禁止直接在 main 分支编写代码后推送。

## 响应偏好

请始终使用简体中文回答本仓库相关的问题。技术名词优先使用中文，必要时在括号中补充英文原词。代码注释、提交说明默认使用中文。
