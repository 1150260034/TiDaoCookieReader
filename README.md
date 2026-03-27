# 天刀助手 Cookie 读取器 (TiDaoCookieReader)

一个简单的 Android App，用于自动提取天刀助手的登录 Cookie。

## 功能

- 一键启动抓包服务
- 自动检测并提取 `access_token`、`openid`、`uin`、`gtk` 等 Cookie 信息
- 一键复制到剪贴板
- 自动打开天刀助手 App

## 快速开始

### 1. 安装 Android Studio

下载并安装 [Android Studio](https://developer.android.com/studio) (推荐最新版)

### 2. 导入项目

1. 打开 Android Studio
2. 选择 `File` → `Open`
3. 选择项目文件夹 `TiDaoCookieReader`
4. 等待 Gradle 同步完成

### 3. 配置模拟器

1. 确保 Mumu 模拟器已安装并运行
2. 在 Android Studio 中选择你的模拟器作为运行目标
3. 点击 `Run` 按钮 (绿色三角形)

### 4. 使用 App（朋友的操作）

```
1. 打开"天刀助手抓包" App
2. 点「开始抓包」
3. 点「打开天刀助手」
4. 在天刀助手里登录账号
5. 回来点「复制全部」
6. 发给你
```

## 项目结构

```
TiDaoCookieReader/
├── app/
│   ├── src/main/
│   │   ├── java/com/tidao/wuxia/app/
│   │   │   ├── vpn/
│   │   │   │   ├── LocalVpnService.java    # VPN 抓包（可选）
│   │   │   │   └── LocalProxyServer.java    # HTTP 代理服务器
│   │   │   ├── cookie/
│   │   │   │   └── CookieExtractor.java     # Cookie 提取
│   │   │   └── ui/
│   │   │       └── MainActivity.java        # 主界面
│   │   ├── res/
│   │   │   ├── layout/activity_main.xml     # 界面布局
│   │   │   └── values/                       # 资源文件
│   │   └── AndroidManifest.xml
│   └── build.gradle
├── build.gradle
├── settings.gradle
├── gradle.properties
└── README.md
```

## 技术说明

- **最低 Android 版本**: Android 8.0 (API 26)
- **目标 Android 版本**: Android 14 (API 34)
- **抓包方式**: 本地 HTTP 代理服务器

## 工作原理

1. App 启动本地 HTTP 代理服务器（端口 8080）
2. 用户配置 WiFi 代理指向本机
3. 天刀助手的 HTTP/HTTPS 请求经过代理
4. App 解析请求，提取 Cookie 参数

## 已知问题

- 如果天刀助手使用 SSL Pinning，HTTPS 请求可能无法解析
- 这种情况需要安装证书或使用其他抓包工具

## License

MIT License
