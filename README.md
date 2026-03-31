# 天刀助手 Cookie 读取器 (TiDaoCookieReader)

从已登录天刀助手 (com.tencent.gamehelper.wuxia) 的 WebView 中提取登录 Cookie 和角色信息，无需抓包。

## 功能

- 读取 WebView Cookie 数据库（通过 Root 权限）
- 提取 `access_token`、`openid`、`uin`、`appid` 等登录凭证
- 从游戏数据库读取角色信息（支持多角色账号选择）
- 检测每日福利绑定状态（AMS 登录验证接口）
- 一键复制全部信息到剪贴板
- 支持 ADB 广播触发自动化操作

## 快速开始

### 1. 安装依赖

- [Android Studio](https://developer.android.com/studio)
- MuMu 模拟器（需开启 Root 权限）

### 2. 构建

```bash
./gradlew assembleDebug      # 调试 APK
./gradlew assembleRelease    # 发布 APK
```

### 3. 安装运行

1. 安装 `app-debug.apk` 到已 Root 的模拟器
2. 安装天刀助手并扫码登录
3. 在天刀助手中点击「周周载愿」
4. 打开本 App → 点「读取Cookie」→ 点「复制全部」

## 项目结构

```
TiDaoCookieReader/
├── app/src/main/java/com/tidao/wuxia/app/
│   ├── ui/MainActivity.java          # 主界面
│   ├── AutomationReceiver.java       # ADB 广播接收器（自动化测试用）
│   ├── cookie/
│   │   ├── WebViewCookieReader.java  # 通过 su 读取天刀助手 WebView Cookie 数据库
│   │   ├── GameDatabaseReader.java   # 通过 su 读取游戏数据库 Role 表
│   │   ├── BindingChecker.java       # 调用 AMS API 检测每日福利绑定状态
│   │   └── CookieExtractor.java      # HTTP 流量解析（本地代理模式，已不使用）
│   └── utils/RootChecker.java        # Root 权限检测
├── app/src/main/res/layout/activity_main.xml
├── build.gradle                       # AGP 8.5.0
└── settings.gradle                   # 使用阿里云镜像加速 Gradle 依赖
```

## 技术参数

- **最低 Android 版本**: API 26 (Android 8.0)
- **目标 Android 版本**: API 34 (Android 14)
- **包名**: `com.tidao.wuxia.app`
- **天刀助手包名**: `com.tencent.gamehelper.wuxia`

## 工作原理

1. 通过 `su` 命令将天刀助手私有目录中的 WebView Cookie 数据库复制到临时位置
2. 直接用 Java SQLite 读取数据库，提取目标域名的 Cookie
3. 同样方式读取游戏数据库 `databases/*.db` 中的 `Role` 表获取角色信息
4. 调用 `https://comm.ams.game.qq.com/ams/ame/amesvr` (FlowID 974294) 检测绑定状态

## ADB 自动化

App 暴露了四个广播 Action 用于自动化测试：

```bash
adb shell am broadcast -a com.tidao.wuxia.app.action.READ_COOKIE
adb shell am broadcast -a com.tidao.wuxia.app.action.COPY_ALL
adb shell am broadcast -a com.tidao.wuxia.app.action.CHECK_WELFARE
adb shell am broadcast -a com.tidao.wuxia.app.action.GET_STATUS
```

## CI/CD

- **android-build.yml** — push 到 main/master 时构建调试 APK，并自动更新 GitHub "latest" prerelease
- **android-release.yml** — 打 tag (`v*`) 时构建发布 APK 并创建 GitHub Release

## License

MIT License
