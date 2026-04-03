# 天刀助手 Cookie 读取器 (TiDaoCookieReader)

从已登录天刀助手的 WebView 中提取登录 Cookie 和角色信息，无需抓包。

📖 **[使用教程文档](https://docs.qq.com/aio/DRXhkU3NNY3VLSHdE?p=FxuxTS2JkoguXFfXY8ppSQ)**（腾讯文档，详细图文说明）

---

## 功能

- 自动读取天刀助手登录 Cookie（`access_token`、`openid`、`uin`、`appid` 等）
- 自动读取游戏角色信息（支持多角色账号选择）
- 检测每日福利绑定状态
- 一键复制全部信息到剪贴板

## 使用前提

- 已 Root 的 Android 模拟器（推荐 MuMu 模拟器并开启 Root 权限）
- 已在模拟器上安装并扫码登录[天刀助手](https://sj.qq.com/appdetail/com.tencent.gamehelper.wuxia)

## 快速开始

### 1. 下载安装

前往 [GitHub Releases](https://github.com/1150260034/TiDaoCookieReader/releases/latest) 下载最新版 APK，安装到模拟器中。

> **版本号说明**：`versionName` 是对外展示的版本号，例如 `v1.2.2`。
> `build`（对应 GitHub Actions 的 `run_number`）属于内部构建编号，在 Android CI 工作流内单调递增，**不会随 versionName 更新而重置**，主要用于 APK 的 `versionCode` 等内部构建标识，不会在发布页展示。
> 同一个 versionName 可能对应多个不同的 build 号，判断版本时以 `versionName` 为准。

> **安装覆盖说明（重要）**：
>
> - 本仓库已统一 Debug 签名证书（`app/debug.keystore`），本地 Debug 与 CI Debug APK 可以直接覆盖安装。
> - 若仍提示「先卸载再安装」，通常是你设备中装的是历史包（签名不一致）或 `versionCode` 更高的包（降级安装被拒绝）。
> - 签名与 `versionCode` 是两个独立校验：签名不一致一定不能覆盖；签名一致但版本降级同样不能覆盖。

### 2. 使用步骤

1. 打开天刀助手，扫码登录
2. 在天刀助手中点击「周周载愿」（使 WebView 加载完成）
3. 打开本 App → 点「读取 Cookie」→ 选择角色 → 点「复制全部」

详细图文教程请查看：[使用教程文档](https://docs.qq.com/aio/DRXhkU3NNY3VLSHdE?p=FxuxTS2JkoguXFfXY8ppSQ)

---

> 开发者文档请参阅 [CLAUDE.md](./CLAUDE.md)

## License

MIT License
