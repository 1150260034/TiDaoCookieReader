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

- AGP 版本: 8.5.0，Gradle: 8.7
- 最低 Android: API 26，目标: API 34
- 包名: `com.tidao.wuxia.app`
- 阿里云镜像: `https://maven.aliyun.com/repository/google` 等

## 架构

### 核心组件

- **MainActivity** — UI 交互、业务编排、多角色选择对话框
- **AutomationReceiver** — ADB 广播接收器，支持 `READ_COOKIE`、`COPY_ALL`、`CHECK_WELFARE`、`GET_STATUS` 四个 Intent Action（后缀名，完整值为 `com.tidao.wuxia.app.action.<ACTION>`），结果输出到 Logcat 和 `cacheDir/result.txt`
- **WebViewCookieReader** — 通过 `su` 复制天刀助手 WebView Cookie 数据库 (`/data/data/com.tencent.gamehelper.wuxia/app_webview/Default/Cookies`) 到临时文件后用 Java SQLite 读取。关键路径: `/data/local/tmp/tidao_cookies.db`
- **GameDatabaseReader** — 遍历天刀助手 `databases/*.db`，用 sqlite3 命令查询 `Role WHERE f_uin=?` 定位正确数据库，读取多角色信息。临时路径: `/data/local/tmp/tidao_game.db`
- **BindingChecker** — 调用 AMS 登录验证接口 (FlowID 974294) 检查账号是否已绑定游戏角色。iRet=101 表示 Cookie 失效
- **CookieExtractor** — 旧版 HTTP 流量解析器（本地代理模式），代码保留但 MainActivity 已不使用
- **RootChecker** — 三种方式检测 Root: su 命令存在性、常见 root 路径、build.tags 含 test-keys

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

## CI/CD

- **android-build.yml** — push 到 main/master 或 PR 时构建调试 APK；push 时自动创建 GitHub prerelease，tag 为 `v{VERSION_NAME}-{SHA12}`，title 为 `Build v{VERSION_NAME} ({SHA})`，幂等（已存在则 edit+upload --clobber）
- **android-release.yml** — 打 tag (`v*`) 时构建 release APK，提取 versionName/versionCode 输出到 GITHUB_OUTPUT，用 `softprops/action-gh-release` 创建 GitHub Release

## 分支规范

新功能开发须在独立分支上进行，禁止直接在 main 分支编写代码后推送。

## 响应偏好

请始终使用简体中文回答本仓库相关的问题。技术名词优先使用中文，必要时在括号中补充英文原词。代码注释、提交说明默认使用中文。
