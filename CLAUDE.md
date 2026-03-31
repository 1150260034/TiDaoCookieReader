# CLAUDE.md

This file provides guidance to Claude Code (claude.ai/code) when working with code in this repository.

## 项目概述

天刀助手 Cookie 读取器，一个 Android App，用于从天刀助手 (com.tencent.gamehelper.wuxia) 提取登录所需的 Cookie 和角色信息。

## 构建命令

```bash
./gradlew assembleDebug      # 构建调试 APK
./gradlew assembleRelease    # 构建发布 APK
./gradlew --no-daemon assembleDebug  # CI 环境使用
```

最低 Android 版本: API 26 (Android 8.0)，目标版本: API 34 (Android 14)

## 架构

### 核心组件

- **MainActivity** — 主界面，UI 交互和业务编排
- **AutomationReceiver** — ADB 广播接收器，支持 `READ_COOKIE`、`COPY_ALL`、`CHECK_WELFARE`、`GET_STATUS` 四个 Intent Action，用于自动化测试
- **WebViewCookieReader** — 通过 `su` 命令读取天刀助手 WebView Cookie 数据库 (`/data/data/com.tencent.gamehelper.wuxia/app_webview/Default/Cookies`)
- **GameDatabaseReader** — 通过 `su` 读取天刀助手游戏数据库，从 `Role` 表提取多角色信息。数据库按 uin 分离，需要先定位正确数据库
- **BindingChecker** — 调用 AMS 登录验证接口 (FlowID 974294) 检查账号是否已绑定游戏角色
- **CookieExtractor** — 解析 HTTP 流量提取 Cookie（本地代理抓包模式使用）

### 数据流向

```
WebViewCookieReader (su 读取) ──┐
                                ├──> MainActivity ──> BindingChecker ──> 每日福利 API
GameDatabaseReader (su 读取) ────┘
```

### 目标服务器

Cookie 相关请求发往: `ams.game.qq.com`, `comm.ams.game.qq.com`, `comm.aci.game.qq.com`, `apps.game.qq.com`

AMS API: `https://comm.ams.game.qq.com/ams/ame/amesvr`，Activity ID: `579009`

## CI/CD

- **android-build.yml** — push 到 main/master 时构建调试 APK，并自动更新 GitHub "latest" prerelease
- **android-release.yml** — 打 tag (`v*`) 时构建发布 APK 并创建 GitHub Release

## 响应偏好

请始终使用简体中文回答本仓库相关的问题。技术名词优先使用中文，必要时在括号中补充英文原词。
